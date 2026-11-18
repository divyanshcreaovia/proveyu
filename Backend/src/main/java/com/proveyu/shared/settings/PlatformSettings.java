package com.proveyu.shared.settings;

import com.proveyu.shared.error.DomainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Settings the platform admin can change without a restart. Each one has a default from application.yml (or .env); a value saved
 * by an admin overrides it. Reads come from memory (loaded at start and updated on every save), so services can call these getters
 * on every request.
 */
@Slf4j
@Service
public class PlatformSettings {

    public enum Type { INT, TEXT, BOOL }

    /** min / max apply to INT values and to the length of TEXT values. */
    public record Spec(String key, String group, String label, String help, Type type, int min, int max, String defaultValue) {}

    public record Entry(String key, String group, String label, String help, Type type, int min, int max, String value, String defaultValue,
                        boolean overridden, Instant updatedAt) {}

    public static final String INTERVIEW_PRICE = "interview.price-rupees";
    public static final String INTERVIEW_MAX_PER_PURCHASE = "interview.max-per-purchase";
    public static final String INTERVIEW_FREE_EVERY_DAYS = "interview.free-every-days";
    public static final String INTERVIEW_UPI_ID = "interview.upi.id";
    public static final String INTERVIEW_UPI_NAME = "interview.upi.name";
    public static final String PREP_MAX_WARNINGS = "prep.max-tab-switch-warnings";
    public static final String PREP_UNLOCK_PERCENT = "prep.unlock-percent";
    public static final String PREP_PROCTOR_RETENTION_DAYS = "prep.proctor-retention-days";
    public static final String BANNER_ENABLED = "site.banner.enabled";
    public static final String BANNER_TEXT = "site.banner.text";
    public static final String BANNER_LINK = "site.banner.link";

    private final PlatformSettingRepository repository;
    private final Map<String, Spec> specs = new LinkedHashMap<>();
    private final Map<String, PlatformSetting> saved = new ConcurrentHashMap<>();

    public PlatformSettings(PlatformSettingRepository repository,
                            @Value("${interview.price-rupees:20}") int price,
                            @Value("${interview.max-per-purchase:10}") int maxPerPurchase,
                            @Value("${interview.free-every-days:15}") int freeEveryDays,
                            @Value("${interview.upi.id:}") String upiId,
                            @Value("${interview.upi.name:ProveYu}") String upiName,
                            @Value("${prep.max-tab-switch-warnings:2}") int maxWarnings,
                            @Value("${prep.unlock-percent:50}") int unlockPercent,
                            @Value("${prep.proctor-retention-days:0}") int retentionDays) {
        this.repository = repository;
        spec(INTERVIEW_PRICE, "AI interview", "Price of one extra interview (₹)", "What a student pays by UPI for one AI interview.", Type.INT, 1, 10_000, price);
        spec(INTERVIEW_MAX_PER_PURCHASE, "AI interview", "Interviews per purchase (max)", "How many interviews a student can buy in one payment.", Type.INT, 1, 100, maxPerPurchase);
        spec(INTERVIEW_FREE_EVERY_DAYS, "AI interview", "One free interview every (days)", "Days between free AI interviews for each student.", Type.INT, 1, 365, freeEveryDays);
        spec(INTERVIEW_UPI_ID, "AI interview", "UPI ID for payments", "Where students pay, e.g. proveyu@okaxis. Empty switches buying off.", Type.TEXT, 0, 100, upiId == null ? "" : upiId.trim());
        spec(INTERVIEW_UPI_NAME, "AI interview", "UPI payee name", "Name shown on the QR and in the payment app.", Type.TEXT, 1, 60, upiName == null || upiName.isBlank() ? "ProveYu" : upiName.trim());
        spec(PREP_MAX_WARNINGS, "Mock tests", "Warnings before auto-submit", "Times a student may leave a running test with a warning; the next time submits it.", Type.INT, 0, 10, maxWarnings);
        spec(PREP_UNLOCK_PERCENT, "Mock tests", "Score to unlock the next test (%)", "The next test of the path opens above this best score.", Type.INT, 0, 100, unlockPercent);
        spec(PREP_PROCTOR_RETENTION_DAYS, "Mock tests", "Keep proctoring screenshots (days)", "Screenshots older than this are deleted every night. 0 keeps them.", Type.INT, 0, 3650, retentionDays);
        spec(BANNER_ENABLED, "Website", "Show the announcement bar", "The blue bar at the top of the public website.", Type.BOOL, 0, 1, "true");
        spec(BANNER_TEXT, "Website", "Announcement text", "One short sentence.", Type.TEXT, 1, 200,
                "AI mock interviews are live for students: speak your answers, get a scored report in minutes.");
        spec(BANNER_LINK, "Website", "Announcement link", "Page the \"See how\" link opens, e.g. /for-students#ai-interview. Empty hides the link.",
                Type.TEXT, 0, 200, "/for-students#ai-interview");
        try {
            repository.findAll().forEach(s -> saved.put(s.getKey(), s));
        } catch (RuntimeException e) { // table missing (before the migration ran): defaults only
            log.warn("Platform settings not loaded, using defaults: {}", e.getMessage());
        }
    }

    private void spec(String key, String group, String label, String help, Type type, int min, int max, Object def) {
        specs.put(key, new Spec(key, group, label, help, type, min, max, String.valueOf(def)));
    }

    // ------------------------------------------------------------------ reads

    public String text(String key) {
        Spec s = spec(key);
        PlatformSetting v = saved.get(key);
        return v == null ? s.defaultValue() : v.getValue();
    }

    public int number(String key) {
        try {
            return Integer.parseInt(text(key).trim());
        } catch (NumberFormatException e) {
            return Integer.parseInt(spec(key).defaultValue());
        }
    }

    public boolean flag(String key) {
        return Boolean.parseBoolean(text(key).trim());
    }

    public List<Entry> all() {
        return specs.values().stream().map(s -> {
            PlatformSetting v = saved.get(s.key());
            return new Entry(s.key(), s.group(), s.label(), s.help(), s.type(), s.min(), s.max(), v == null ? s.defaultValue() : v.getValue(),
                    s.defaultValue(), v != null, v == null ? null : v.getUpdatedAt());
        }).toList();
    }

    // ------------------------------------------------------------------ writes (admin)

    /** Saves the given values (checked first, all or nothing) and returns "label: old -> new" for each change. */
    @Transactional
    public List<String> update(Map<String, String> values, UUID adminId) {
        Map<String, String> clean = new LinkedHashMap<>();
        values.forEach((k, v) -> clean.put(k, check(spec(k), v)));
        List<String> changes = new ArrayList<>();
        Instant now = Instant.now();
        clean.forEach((k, v) -> {
            String old = text(k);
            if (old.equals(v)) return;
            PlatformSetting row = new PlatformSetting(k, v, adminId, now);
            repository.save(row);
            saved.put(k, row);
            changes.add(spec(k).label() + ": " + (old.isEmpty() ? "(empty)" : old) + " → " + (v.isEmpty() ? "(empty)" : v));
        });
        return changes;
    }

    /** Back to the application.yml default. */
    @Transactional
    public String reset(String key) {
        Spec s = spec(key);
        repository.deleteById(key);
        saved.remove(key);
        return s.label() + " reset to " + (s.defaultValue().isEmpty() ? "(empty)" : s.defaultValue());
    }

    private Spec spec(String key) {
        Spec s = specs.get(key);
        if (s == null) throw new DomainException("Unknown setting " + key, HttpStatus.BAD_REQUEST, "UNKNOWN_SETTING");
        return s;
    }

    private static String check(Spec s, String raw) {
        String v = raw == null ? "" : raw.trim();
        switch (s.type()) {
            case INT -> {
                int n;
                try { n = Integer.parseInt(v); } catch (NumberFormatException e) { throw bad(s.label() + " must be a whole number"); }
                if (n < s.min() || n > s.max()) throw bad(s.label() + " must be between " + s.min() + " and " + s.max());
                return String.valueOf(n);
            }
            case BOOL -> {
                if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) throw bad(s.label() + " must be on or off");
                return v.toLowerCase(Locale.ROOT);
            }
            default -> {
                if (v.length() < s.min() || v.length() > s.max()) throw bad(s.label() + " must be " + s.min() + " to " + s.max() + " characters");
                if (s.key().equals(INTERVIEW_UPI_ID) && !v.isEmpty() && !v.matches("[A-Za-z0-9._-]{2,64}@[A-Za-z0-9.-]{2,64}")) {
                    throw bad("Enter a UPI ID like name@bank, or leave it empty to switch buying off");
                }
                if (s.key().equals(BANNER_LINK) && !v.isEmpty() && !(v.startsWith("/") || v.startsWith("https://"))) {
                    throw bad("The link must start with / (a page of this site) or https://");
                }
                return v;
            }
        }
    }

    private static DomainException bad(String m) {
        return new DomainException(m, HttpStatus.BAD_REQUEST, "INVALID_SETTING");
    }
}

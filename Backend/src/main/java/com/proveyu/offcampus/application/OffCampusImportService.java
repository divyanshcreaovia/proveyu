package com.proveyu.offcampus.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proveyu.offcampus.domain.OffCampusModel.ImportRun;
import com.proveyu.offcampus.domain.OffCampusModel.JobField;
import com.proveyu.offcampus.domain.OffCampusModel.JobType;
import com.proveyu.offcampus.domain.OffCampusModel.Source;
import com.proveyu.offcampus.domain.OffCampusModel.WorkMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Nightly import of fresher jobs and internships from the Careerjet job search API (https://www.careerjet.co.in/partners/api), stored
 * as DRAFT off-campus jobs for the ProveYu team to review and publish. For every field (software, data, mechanical, ...) it runs one
 * "fresher" search and one internship search across India, newest first, and tags each job with its field so students can follow
 * their interests. The same ad is never imported twice. Only ads from the last {@code days} days are kept: older drafts are deleted
 * and older published ones closed. Without an API key the import only does that clean-up.
 */
@Slf4j
@Service
public class OffCampusImportService {

    /** One ad mapped to an off-campus job. */
    record Imported(Source source, String sourceId, String company, String title, JobType jobType, WorkMode workMode, JobField field,
                    String location, BigDecimal ctcMin, BigDecimal ctcMax, Integer stipend, String description, String url, Instant postedAt) {}

    /** Running totals of one import. */
    private static final class Tally {
        int fetched, added, skipped;
        final List<String> errors = new ArrayList<>();
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final int days;
    private final long pauseMs;
    private final String careerjetKey;
    private final String careerjetUrl;
    private final String careerjetLocale;
    private final String careerjetIp;
    private final String careerjetAgent;
    private final int careerjetPages;
    private final String careerjetReferer;
    private volatile ImportRun last;

    public OffCampusImportService(JdbcTemplate jdbc, ObjectMapper json,
                                  @Value("${offcampus.import.days:15}") int days,
                                  @Value("${offcampus.import.pause-ms:1500}") long pauseMs,
                                  @Value("${offcampus.import.careerjet.api-key:}") String careerjetKey,
                                  @Value("${offcampus.import.careerjet.url:https://search.api.careerjet.net/v4/query}") String careerjetUrl,
                                  @Value("${offcampus.import.careerjet.locale:en_IN}") String careerjetLocale,
                                  @Value("${offcampus.import.careerjet.user-ip:}") String careerjetIp,
                                  @Value("${offcampus.import.careerjet.user-agent:Mozilla/5.0 (compatible; ProveYuJobImport/1.0)}") String careerjetAgent,
                                  @Value("${offcampus.import.careerjet.pages:2}") int careerjetPages,
                                  @Value("${offcampus.import.careerjet.referer:}") String careerjetReferer) {
        this.jdbc = jdbc;
        this.json = json;
        this.days = Math.max(1, days);
        this.pauseMs = Math.max(0, pauseMs);
        this.careerjetKey = trim(careerjetKey);
        this.careerjetUrl = noSlash(careerjetUrl);
        this.careerjetLocale = careerjetLocale;
        this.careerjetIp = trim(careerjetIp);
        this.careerjetAgent = careerjetAgent;
        this.careerjetPages = Math.max(1, Math.min(careerjetPages, 10));
        this.careerjetReferer = trim(careerjetReferer);
        this.last = new ImportRun(configured(), null, null, 0, 0, 0, 0, null);
    }

    public boolean configured() {
        return !careerjetKey.isEmpty();
    }

    public ImportRun last() {
        return last;
    }

    /** Every night at 12 (India time by default). */
    @Scheduled(cron = "${offcampus.import.cron:0 0 0 * * *}", zone = "${offcampus.import.zone:Asia/Kolkata}")
    public void nightly() {
        run();
    }

    public synchronized ImportRun run() {
        Instant started = Instant.now();
        int expired = expireOld();
        if (!configured()) {
            last = new ImportRun(false, started, Instant.now(), 0, 0, 0, expired,
                    "Set CAREERJET_API_KEY in Backend/.env to import jobs.");
            log.info("[OFF CAMPUS IMPORT] not configured; removed or closed {} old imported jobs", expired);
            return last;
        }
        Tally t = new Tally();
        careerjet(started, t);
        String error = t.errors.isEmpty() ? null : String.join("; ", t.errors);
        last = new ImportRun(true, started, Instant.now(), t.fetched, t.added, t.skipped, expired, error);
        log.info("[OFF CAMPUS IMPORT] fetched {}, added {}, skipped {}, expired {}{}", t.fetched, t.added, t.skipped, expired,
                error == null ? "" : " (" + error + ")");
        return last;
    }

    // ================================================================== Careerjet

    private void careerjet(Instant now, Tally t) {
        String ip = careerjetIp.isEmpty() ? publicIp() : careerjetIp;
        String auth = "Basic " + Base64.getEncoder().encodeToString((careerjetKey + ":").getBytes(StandardCharsets.UTF_8));
        outer:
        for (JobField field : JobField.values()) {
            if (field.search == null) continue;
            for (boolean internship : new boolean[]{false, true}) {
                for (int page = 1; page <= careerjetPages; page++) {
                    try {
                        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(careerjetUrl(field, internship, page, ip)))
                                .header("Authorization", auth).header("Accept", "application/json").timeout(Duration.ofSeconds(30)).GET();
                        // Careerjet checks the Referer against the website declared in the publisher account
                        if (!careerjetReferer.isEmpty()) req.header("Referer", careerjetReferer);
                        HttpResponse<String> r = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
                        if (r.statusCode() == 401 || r.statusCode() == 403) {
                            t.errors.add("Careerjet refused the request (HTTP " + r.statusCode() + "): " + shortBody(r.body())
                                    + (r.body() != null && r.body().contains("referrer") ? " Set CAREERJET_REFERER to the website URL declared in your Careerjet publisher account." : ""));
                            break outer;
                        }
                        if (r.statusCode() == 429) {
                            t.errors.add("Careerjet rate limit reached; the rest is imported tomorrow night");
                            break outer;
                        }
                        if (r.statusCode() >= 300) {
                            t.errors.add("Careerjet HTTP " + r.statusCode() + " for " + field.label);
                            break;
                        }
                        JsonNode body = json.readTree(r.body());
                        JsonNode jobs = body.path("jobs");
                        if (!jobs.isArray() || jobs.isEmpty()) break;
                        for (JsonNode ad : jobs) {
                            t.fetched++;
                            Imported job = mapCareerjet(ad, field, internship, now, days);
                            if (job == null || !insert(job)) t.skipped++;
                            else t.added++;
                        }
                        if (page >= body.path("pages").asInt(1)) break;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        t.errors.add("Import interrupted");
                        break outer;
                    } catch (Exception e) {
                        log.warn("[OFF CAMPUS IMPORT] Careerjet {} page {} failed: {}", field, page, e.toString());
                        t.errors.add("Could not reach Careerjet: " + e.getClass().getSimpleName());
                        break;
                    } finally {
                        pause();
                    }
                }
            }
        }
    }

    String careerjetUrl(JobField field, boolean internship, int page, String ip) {
        StringBuilder u = new StringBuilder(careerjetUrl)
                .append("?locale_code=").append(enc(careerjetLocale))
                .append("&keywords=").append(enc(internship ? field.search : field.search + " fresher"))
                .append("&sort=date&page_size=100&page=").append(page)
                .append("&user_ip=").append(enc(ip)).append("&user_agent=").append(enc(careerjetAgent));
        if (internship) u.append("&contract_type=i");
        return u.toString();
    }

    /** Maps one Careerjet job; null when it is unusable (no company, no link, too old, not in rupees). */
    static Imported mapCareerjet(JsonNode ad, JobField searched, boolean internship, Instant now, int days) {
        String title = OffCampusImportService.clean(ad.path("title").asText(""));
        String company = clean(ad.path("company").asText(""));
        String url = ad.path("url").asText("").trim();
        if (title.isEmpty() || company.isEmpty() || !url.matches("^https?://\\S+$") || url.length() > 1000) return null;
        Instant posted = parseDate(ad.path("date").asText(""));
        if (posted == null) posted = now;
        if (posted.isBefore(now.minus(Duration.ofDays(days)))) return null;
        if (notForFreshers(title, ad.path("description").asText(""))) return null;
        String location = clean(ad.path("locations").asText(""));
        String lower = (title + " " + location + " " + ad.path("description").asText("")).toLowerCase(Locale.ROOT);
        // Careerjet's internship filter also returns regular jobs, so the title decides
        JobType type = INTERN.matcher(title).find() ? JobType.INTERNSHIP : JobType.FULL_TIME;
        JobField byTitle = JobField.classify(title);
        JobField field = byTitle != JobField.OTHER ? byTitle : searched;
        BigDecimal ctcMin = null, ctcMax = null;
        Integer stipend = null;
        String currency = ad.path("salary_currency_code").asText("INR");
        double min = ad.path("salary_min").asDouble(0), max = ad.path("salary_max").asDouble(min);
        if (min > 0 && (currency.isBlank() || "INR".equalsIgnoreCase(currency))) {
            String per = ad.path("salary_type").asText("Y").toUpperCase(Locale.ROOT);
            double yearlyMin = "M".equals(per) ? min * 12 : "Y".equals(per) ? min : -1;
            double yearlyMax = "M".equals(per) ? Math.max(min, max) * 12 : "Y".equals(per) ? Math.max(min, max) : -1;
            if (yearlyMin > 0 && yearlyMax < 50_000_000) {
                if (type == JobType.INTERNSHIP) stipend = (int) Math.round(yearlyMin / 12);
                else {
                    ctcMin = lpa(yearlyMin);
                    ctcMax = lpa(yearlyMax);
                }
            }
        }
        String desc = clean(ad.path("description").asText(""));
        String description = (desc.isEmpty() ? "" : desc + "\n\n") + "Found through Careerjet. Read the full ad and apply on the job page.";
        return new Imported(Source.CAREERJET, "cj-" + hash(company + "|" + title + "|" + location), cut(company, 150), cut(title, 150), type,
                workMode(lower), field, location.isEmpty() ? null : cut(location, 150), ctcMin, ctcMax, stipend, cut(description, 8000), url, posted);
    }

    // ================================================================== storage

    /** Inserts a draft; false when the ad was already imported. */
    private boolean insert(Imported j) {
        return jdbc.update("""
                insert ignore into off_campus_jobs (id, company_name, role_title, job_type, work_mode, field, location, ctc_min_lpa, ctc_max_lpa,
                    stipend_per_month, description, apply_url, apply_deadline, status, source, source_id, source_posted_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?)""",
                UUID.randomUUID().toString(), j.company(), j.title(), j.jobType().name(), j.workMode().name(), j.field().name(), j.location(),
                j.ctcMin(), j.ctcMax(), j.stipend(), j.description(), j.url(), Timestamp.from(j.postedAt().plus(Duration.ofDays(days))),
                j.source().name(), j.sourceId(), Timestamp.from(j.postedAt())) == 1;
    }

    /** Imported ads older than the window: unreviewed drafts are deleted, published ones closed. */
    private int expireOld() {
        Timestamp cutoff = Timestamp.from(Instant.now().minus(Duration.ofDays(days)));
        int deleted = jdbc.update("""
                delete from off_campus_jobs where source <> 'MANUAL' and status = 'DRAFT' and source_posted_at < ?
                  and not exists (select 1 from off_campus_applications a where a.job_id = off_campus_jobs.id)""", cutoff);
        int closed = jdbc.update("update off_campus_jobs set status = 'CLOSED' where source <> 'MANUAL' and status = 'PUBLISHED' and source_posted_at < ?",
                cutoff);
        return deleted + closed;
    }

    // ================================================================== helpers

    /** Careerjet wants the IP of the user behind a search; a nightly import has none, so the server's public IP is sent. */
    private String publicIp() {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create("https://api.ipify.org")).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            String ip = r.body().trim();
            if (r.statusCode() == 200 && ip.matches("[0-9a-fA-F:.]{3,45}")) return ip;
        } catch (Exception e) {
            log.info("[OFF CAMPUS IMPORT] public IP lookup failed: {}", e.toString());
        }
        return "127.0.0.1";
    }

    private static final java.util.regex.Pattern INTERN =
            java.util.regex.Pattern.compile("\\b(intern|interns|internship|trainee|apprentice|apprenticeship)\\b", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern SENIOR = java.util.regex.Pattern.compile(
            "\\b(senior|sr|lead|principal|staff|manager|head|director|architect|vp|avp|ii|iii|iv)\\b|\\bsde\\s*[-]?\\s*[23]\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE);
    /** "3+ years", "4-6 years", "5 yrs" (the lower bound counts). */
    private static final java.util.regex.Pattern YEARS = java.util.regex.Pattern.compile("\\b(\\d{1,2})\\s*\\+?\\s*(?:-|to)?\\s*\\d{0,2}\\s*\\+?\\s*(?:years?|yrs?)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /** Senior titles, or an ad asking for 2+ years of experience: not for freshers. */
    static boolean notForFreshers(String title, String description) {
        if (title != null && SENIOR.matcher(title.replace('.', ' ')).find()) return true;
        java.util.regex.Matcher m = YEARS.matcher((title == null ? "" : title) + " " + (description == null ? "" : description));
        while (m.find()) if (Integer.parseInt(m.group(1)) >= 2) return true;
        return false;
    }

    static WorkMode workMode(String lower) {
        return lower.contains("work from home") || lower.contains("remote") || lower.contains("wfh") ? WorkMode.REMOTE
                : lower.contains("hybrid") ? WorkMode.HYBRID : WorkMode.ONSITE;
    }

    /** ISO-8601, RFC 1123 ("Tue, 18 Nov 2025 10:00:00 GMT") or a plain date; null if none fits. */
    static Instant parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim();
        try { return Instant.parse(v); } catch (Exception ignored) { /* next */ }
        try { return OffsetDateTime.parse(v).toInstant(); } catch (Exception ignored) { /* next */ }
        try { return ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(); } catch (Exception ignored) { /* next */ }
        try { // RFC 1123 with a wrong weekday: drop the weekday
            return ZonedDateTime.parse(v.replaceFirst("^[A-Za-z]{3},\\s*", ""), DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss z", Locale.ENGLISH)).toInstant();
        } catch (Exception ignored) { /* next */ }
        try { return LocalDate.parse(v.length() >= 10 ? v.substring(0, 10) : v).atStartOfDay().toInstant(ZoneOffset.UTC); } catch (Exception ignored) { /* none */ }
        return null;
    }

    private void pause() {
        if (pauseMs == 0) return;
        try {
            Thread.sleep(pauseMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static BigDecimal lpa(double rupeesPerYear) {
        return BigDecimal.valueOf(rupeesPerYear / 100_000).setScale(2, RoundingMode.HALF_UP).min(BigDecimal.valueOf(500));
    }

    /** Feeds mark matches with <b> / <strong>; strip tags and squeeze spaces. */
    static String clean(String s) {
        return s == null ? "" : s.replaceAll("<[^>]*>", " ").replace("&amp;", "&").replace("&nbsp;", " ").replace("&#39;", "'").replace("&quot;", "\"")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ").trim();
    }

    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 32);
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static String shortBody(String b) {
        return b == null ? "" : b.length() > 200 ? b.substring(0, 200) : b;
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String noSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}

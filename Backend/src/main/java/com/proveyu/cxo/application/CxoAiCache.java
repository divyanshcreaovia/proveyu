package com.proveyu.cxo.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-college cache of AI answers (table {@code cxo_ai_cache}). PLAN entries hold the dashboard spec for a question, INSIGHT entries
 * the summary for a set of numbers. Only model answers are stored; widget numbers are always queried live.
 */
@Slf4j
@Component
public class CxoAiCache {

    public enum Kind { PLAN, INSIGHT }

    private final JdbcTemplate jdbc;
    private final Duration planTtl;
    private final Duration insightTtl;

    public CxoAiCache(JdbcTemplate jdbc, @Value("${cxo.cache.plan-ttl-hours:168}") long planHours,
                      @Value("${cxo.cache.insight-ttl-hours:24}") long insightHours) {
        this.jdbc = jdbc;
        this.planTtl = Duration.ofHours(Math.max(0, planHours));
        this.insightTtl = Duration.ofHours(Math.max(0, insightHours));
    }

    /** Same question in other words of case and spacing gives the same key. */
    public static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}&&[^%.]]", " ").replaceAll("\\s+", " ").strip();
    }

    public static String key(String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String p : parts) {
                md.update((p == null ? "" : p).getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<String> get(UUID collegeId, Kind kind, String key) {
        Duration ttl = kind == Kind.PLAN ? planTtl : insightTtl;
        if (ttl.isZero()) return Optional.empty();
        try {
            List<String> rows = jdbc.queryForList("select payload from cxo_ai_cache where college_id = ? and kind = ? and cache_key = ? and created_at >= ?",
                    String.class, collegeId.toString(), kind.name(), key, Timestamp.from(Instant.now().minus(ttl)));
            if (rows.isEmpty()) return Optional.empty();
            jdbc.update("update cxo_ai_cache set hits = hits + 1, last_hit_at = now() where college_id = ? and kind = ? and cache_key = ?",
                    collegeId.toString(), kind.name(), key);
            return Optional.of(rows.get(0));
        } catch (Exception e) {
            log.warn("CXO cache read failed: {}", e.toString());
            return Optional.empty();
        }
    }

    public void put(UUID collegeId, Kind kind, String key, String payload) {
        try {
            jdbc.update("""
                    insert into cxo_ai_cache (id, college_id, kind, cache_key, payload) values (?, ?, ?, ?, ?)
                    on duplicate key update payload = values(payload), created_at = now(), hits = 0, last_hit_at = null""",
                    UUID.randomUUID().toString(), collegeId.toString(), kind.name(), key, payload);
        } catch (Exception e) {
            log.warn("CXO cache write failed: {}", e.toString());
        }
    }

    /** Drops expired entries once a day. */
    @Scheduled(cron = "${cxo.cache.cleanup-cron:0 30 3 * * *}")
    public void purge() {
        Duration longest = planTtl.compareTo(insightTtl) >= 0 ? planTtl : insightTtl;
        int n = jdbc.update("delete from cxo_ai_cache where created_at < ?", Timestamp.from(Instant.now().minus(longest)));
        if (n > 0) log.info("CXO cache: removed {} expired entries", n);
    }
}

package com.proveyu.shared.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sessions that must stop although their token has not expired: users a platform admin blocked (users.is_active = 0), and sign-ins
 * made before the password was last changed. Kept in memory, so no database query per request; loaded at start.
 */
@Slf4j
@Component
public class UserBlockList {
    private final Set<String> blocked = ConcurrentHashMap.newKeySet();
    /** user id → the second the password was last changed. */
    private final Map<String, Long> passwordChanged = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;

    public UserBlockList(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        try {
            blocked.addAll(jdbc.queryForList("select id from users where is_active = 0", String.class));
            jdbc.query("select id, password_changed_at from users where password_changed_at is not null",
                    rs -> {
                        passwordChanged.put(rs.getString(1), rs.getTimestamp(2).toInstant().getEpochSecond());
                    });
        } catch (RuntimeException e) {
            log.warn("Blocked users not loaded: {}", e.getMessage());
        }
    }

    /** Call when a password changes: every sign-in made before now stops working. */
    public void passwordChanged(UUID userId) {
        long now = Instant.now().getEpochSecond();
        jdbc.update("update users set password_changed_at = ? where id = ?", Timestamp.from(Instant.ofEpochSecond(now)), userId.toString());
        passwordChanged.put(userId.toString(), now);
    }

    /** A token's issue time is in whole seconds, so one made in the same second as the change (signing in again) is accepted. */
    public boolean isOutdated(String userId, Date issuedAt) {
        Long changed = userId == null ? null : passwordChanged.get(userId);
        return changed != null && (issuedAt == null || issuedAt.toInstant().getEpochSecond() < changed);
    }

    public boolean isBlocked(String userId) {
        return userId != null && blocked.contains(userId);
    }

    public void block(UUID userId) {
        blocked.add(userId.toString());
    }

    public void unblock(UUID userId) {
        blocked.remove(userId.toString());
    }
}

package com.proveyu.shared.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Users a platform admin blocked (users.is_active = 0). Sign-in already refuses them; this list also stops sessions that were open
 * when they were blocked, without a database query per request. Loaded at start, updated when an admin blocks or unblocks.
 */
@Slf4j
@Component
public class UserBlockList {
    private final Set<String> blocked = ConcurrentHashMap.newKeySet();

    public UserBlockList(JdbcTemplate jdbc) {
        try {
            blocked.addAll(jdbc.queryForList("select id from users where is_active = 0", String.class));
        } catch (RuntimeException e) {
            log.warn("Blocked users not loaded: {}", e.getMessage());
        }
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

package com.proveyu.shared.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class TokenBlacklistService {

    // Map storing token signature/raw token -> expiration epoch timestamp in milliseconds
    private final Map<String, Long> blacklistedTokens = new ConcurrentHashMap<>();

    /**
     * Blacklists a JWT token until its natural expiration time.
     */
    public void blacklistToken(String token, long expiryTimeMs) {
        if (token != null && !token.isBlank()) {
            blacklistedTokens.put(token, expiryTimeMs);
            log.info("[TOKEN BLACKLISTED] Successfully blacklisted token. Total active blacklisted tokens: {}", blacklistedTokens.size());
        }
    }

    /**
     * Checks if a JWT token has been invalidated / logged out.
     */
    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Long expiryTimeMs = blacklistedTokens.get(token);
        if (expiryTimeMs == null) {
            return false;
        }
        // If token has naturally expired past its TTL, remove it and return false
        if (System.currentTimeMillis() > expiryTimeMs) {
            blacklistedTokens.remove(token);
            return false;
        }
        return true;
    }

    /**
     * Scheduled cleanup job running every 30 minutes to purge expired tokens from memory.
     */
    @Scheduled(fixedRate = 1800000)
    public void purgeExpiredTokens() {
        long now = System.currentTimeMillis();
        int initialSize = blacklistedTokens.size();
        blacklistedTokens.entrySet().removeIf(entry -> entry.getValue() < now);
        int purgedCount = initialSize - blacklistedTokens.size();
        if (purgedCount > 0) {
            log.info("[TOKEN BLACKLIST CLEANUP] Purged {} expired tokens from memory blacklist. Remaining: {}", purgedCount, blacklistedTokens.size());
        }
    }
}

package com.proveyu.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proveyu.shared.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    @Value("${security.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${security.rate-limit.auth-limit-per-minute:60}")
    private int authLimitPerMinute;

    @Value("${security.rate-limit.user-limit-per-minute:120}")
    private int userLimitPerMinute;

    /**
     * Addresses of reverse proxies whose X-Forwarded-For is believed (TRUSTED_PROXIES, e.g. "127.0.0.1" when nginx runs on the same
     * server). Empty = the header is ignored, because anyone can send it: a fake address per request would dodge the rate limit.
     */
    @Value("${security.trusted-proxies:}")
    private java.util.List<String> trustedProxies = java.util.List.of();

    private final JwtTokenProvider jwtTokenProvider;
    private final Map<String, RequestCounter> requestCounts = new ConcurrentHashMap<>();
    /** Knows java.time (ApiResponse carries an Instant timestamp); a bare ObjectMapper failed here and turned every 429 into a 500 / 401. */
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (rateLimitEnabled && isRateLimitedPath(path)) {
            String clientIp = getClientIp(request);
            String authHeader = request.getHeader("Authorization");
            
            String identifierKey;
            int limit;

            // If user provides a JWT token, rate limit per USER ID; otherwise rate limit per IP
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (jwtTokenProvider.validateToken(token)) {
                    String userId = jwtTokenProvider.getUserIdFromToken(token);
                    identifierKey = "USER:" + userId;
                    limit = userLimitPerMinute;
                } else {
                    identifierKey = "IP:" + clientIp;
                    limit = authLimitPerMinute;
                }
            } else {
                identifierKey = "IP:" + clientIp;
                limit = authLimitPerMinute;
            }

            String bucketKey = identifierKey + ":" + (System.currentTimeMillis() / 60000);

            RequestCounter counter = requestCounts.computeIfAbsent(bucketKey, k -> new RequestCounter());
            int currentCount = counter.increment();

            if (currentCount > limit) {
                log.warn("[RATE LIMIT EXCEEDED] Identifier=[{}] requested path=[{}] count=[{}] limit=[{}]",
                        identifierKey, path, currentCount, limit);

                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);

                ApiResponse<Void> apiResponse = ApiResponse.error(
                        "Too many requests. Please slow down and try again after 1 minute.",
                        "RATE_LIMIT_EXCEEDED"
                );

                response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
                return;
            }
        }

        // Periodic cleanup of old 1-minute buckets
        if (requestCounts.size() > 2000) {
            long currentMinute = System.currentTimeMillis() / 60000;
            requestCounts.keySet().removeIf(key -> {
                String[] parts = key.split(":");
                return parts.length >= 2 && Long.parseLong(parts[parts.length - 1]) < currentMinute - 2;
            });
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimitedPath(String path) {
        return path.startsWith("/api/v1/auth/") ||
               path.startsWith("/api/v1/college/auth/") ||
               path.startsWith("/api/v1/public/pricing-enquiries");
    }

    /**
     * The caller's address. Behind a trusted proxy (nginx appends the real address to X-Forwarded-For) it is the right-most address
     * that is not a proxy; anything to its left was written by the client and is ignored. Without a trusted proxy: the socket address.
     */
    String getClientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String xf = request.getHeader("X-Forwarded-For");
        if (xf == null || xf.isBlank() || !isTrusted(remote)) return remote;
        String[] hops = xf.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String ip = hops[i].trim();
            if (!ip.isEmpty() && !isTrusted(ip)) return ip;
        }
        return remote;
    }

    private boolean isTrusted(String ip) {
        return trustedProxies != null && trustedProxies.stream().map(String::trim).anyMatch(p -> !p.isEmpty() && p.equals(ip));
    }

    private static class RequestCounter {
        private final AtomicInteger count = new AtomicInteger(0);

        public int increment() {
            return count.incrementAndGet();
        }
    }
}

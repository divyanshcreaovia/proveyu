package com.proveyu.shared.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();

        // Only apply detailed structured request/response logging for API endpoints
        if (!uri.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String method = request.getMethod();
        String queryString = request.getQueryString() != null ? "?" + request.getQueryString() : "";
        String fullPath = uri + queryString;
        String clientIp = getClientIp(request);
        long startTime = System.currentTimeMillis();

        log.info("==> [API REQUEST INCOMING] Method=[{}] Path=[{}] ClientIP=[{}]", method, fullPath, clientIp);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();

            if (status >= 500) {
                log.error("<== [API RESPONSE EXITED] Method=[{}] Path=[{}] Status=[{}] Duration=[{}ms]",
                        method, fullPath, status, duration);
            } else if (status >= 400) {
                log.warn("<== [API RESPONSE EXITED] Method=[{}] Path=[{}] Status=[{}] Duration=[{}ms]",
                        method, fullPath, status, duration);
            } else {
                log.info("<== [API RESPONSE EXITED] Method=[{}] Path=[{}] Status=[{}] Duration=[{}ms]",
                        method, fullPath, status, duration);
            }
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}

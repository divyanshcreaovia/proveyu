package com.proveyu.chat.websocket;

import com.proveyu.shared.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_ATTR = "userId";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                  ServerHttpResponse response,
                                  WebSocketHandler wsHandler,
                                  Map<String, Object> attributes) {
        String token = extractToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            try {
                String userIdStr = jwtTokenProvider.getUserIdFromToken(token);
                UUID userId = UUID.fromString(userIdStr);
                attributes.put(USER_ID_ATTR, userId);
                log.info("[WEBSOCKET AUTH SUCCESS] Client authenticated for userId=[{}]", userId);
                return true;
            } catch (Exception ex) {
                log.warn("[WEBSOCKET AUTH ERROR] Failed to parse userId from token: {}", ex.getMessage());
            }
        }

        log.warn("[WEBSOCKET AUTH REJECTED] Handshake rejected due to missing or invalid token. URI: {}", request.getURI());
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // No-op after handshake
    }

    private String extractToken(ServerHttpRequest request) {
        // 1. Check query parameter: ws://.../ws/chat?token=<jwt>
        URI uri = request.getURI();
        String query = uri.getQuery();
        if (StringUtils.hasText(query)) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length == 2 && "token".equalsIgnoreCase(pair[0])) {
                    return pair[1];
                }
            }
        }

        // 2. Check Authorization header: Bearer <jwt>
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        return null;
    }
}

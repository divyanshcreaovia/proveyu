package com.proveyu.chat.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatWebSocketSessionManager sessionManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID userId = getUserId(session);
        if (userId != null) {
            sessionManager.addSession(userId, session);
        } else {
            log.warn("[WEBSOCKET HANDLER] Session established without authenticated userId, closing sessionId=[{}]", session.getId());
            try {
                session.close(CloseStatus.NOT_ACCEPTABLE);
            } catch (Exception ex) {
                log.error("Failed to close unauthenticated session: {}", ex.getMessage());
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Echo / Heartbeat ping-pong support
        String payload = message.getPayload();
        if ("ping".equalsIgnoreCase(payload.trim()) || payload.contains("\"ping\"")) {
            try {
                session.sendMessage(new TextMessage("{\"event\":\"pong\",\"timestamp\":" + System.currentTimeMillis() + "}"));
            } catch (Exception ex) {
                log.warn("Failed to send pong to session {}: {}", session.getId(), ex.getMessage());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = getUserId(session);
        if (userId != null) {
            sessionManager.removeSession(userId, session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        UUID userId = getUserId(session);
        log.warn("[WEBSOCKET TRANSPORT ERROR] User=[{}] SessionId=[{}]: {}",
                userId, session.getId(), exception.getMessage());
        if (userId != null) {
            sessionManager.removeSession(userId, session);
        }
    }

    public void publishEvent(UUID userId, Object eventPayload) {
        sessionManager.sendToUser(userId, eventPayload);
    }

    private UUID getUserId(WebSocketSession session) {
        Object attr = session.getAttributes().get(WebSocketAuthInterceptor.USER_ID_ATTR);
        if (attr instanceof UUID uuid) {
            return uuid;
        } else if (attr instanceof String str) {
            try {
                return UUID.fromString(str);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}

package com.proveyu.chat.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketSessionManager {

    private final ObjectMapper objectMapper;
    private final Map<UUID, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    public void addSession(UUID userId, WebSocketSession session) {
        if (userId == null || session == null) {
            return;
        }
        userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        log.info("[WEBSOCKET CONNECTED] User=[{}] SessionId=[{}] Active sessions for user=[{}]",
                userId, session.getId(), getActiveSessionCount(userId));
    }

    public void removeSession(UUID userId, WebSocketSession session) {
        if (userId == null || session == null) {
            return;
        }
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
            }
        }
        log.info("[WEBSOCKET DISCONNECTED] User=[{}] SessionId=[{}] Remaining active sessions=[{}]",
                userId, session.getId(), getActiveSessionCount(userId));
    }

    public boolean isUserOnline(UUID userId) {
        if (userId == null) {
            return false;
        }
        Set<WebSocketSession> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    public int getActiveSessionCount(UUID userId) {
        if (userId == null) {
            return 0;
        }
        Set<WebSocketSession> sessions = userSessions.get(userId);
        return sessions != null ? sessions.size() : 0;
    }

    public void sendToUser(UUID userId, Object payload) {
        if (userId == null || payload == null) {
            return;
        }

        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("[WEBSOCKET OFFLINE] User=[{}] has no active sessions. Real-time message omitted.", userId);
            return;
        }

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.error("[WEBSOCKET JSON ERROR] Failed to serialize event for user=[{}]: {}", userId, ex.getMessage());
            return;
        }

        TextMessage textMessage = new TextMessage(jsonPayload);
        for (WebSocketSession session : sessions) {
            if (session != null && session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                    log.debug("[WEBSOCKET DELIVERED] Event delivered to User=[{}] SessionId=[{}]", userId, session.getId());
                } catch (IOException ex) {
                    log.warn("[WEBSOCKET SEND FAILED] Could not send message to user=[{}] sessionId=[{}]: {}",
                            userId, session.getId(), ex.getMessage());
                }
            }
        }
    }
}

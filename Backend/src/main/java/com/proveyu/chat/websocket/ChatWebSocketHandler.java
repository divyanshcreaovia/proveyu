package com.proveyu.chat.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proveyu.chat.application.ChatService;
import com.proveyu.chat.web.dto.ChatDto.SendTextMessageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ChatService> chatServiceProvider;

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
        String payload = message.getPayload();
        if ("ping".equalsIgnoreCase(payload.trim()) || payload.contains("ping")) {
            try {
                session.sendMessage(new TextMessage("{\"event\":\"pong\",\"timestamp\":" + System.currentTimeMillis() + "}"));
            } catch (Exception ex) {
                log.warn("Failed to send pong to session {}: {}", session.getId(), ex.getMessage());
            }
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String event = root.path("event").asText();
            if ("message.send".equalsIgnoreCase(event) || "chat.message".equalsIgnoreCase(event)) {
                UUID senderId = getUserId(session);
                JsonNode dataNode = root.path("data");
                String receiverIdStr = dataNode.path("receiverId").asText();
                String msgText = dataNode.path("message").asText();

                if (senderId != null && !receiverIdStr.isBlank() && !msgText.isBlank()) {
                    UUID receiverId = UUID.fromString(receiverIdStr);
                    ChatService chatService = chatServiceProvider.getIfAvailable();
                    if (chatService != null) {
                        SendTextMessageRequest req = new SendTextMessageRequest(receiverId, msgText);
                        chatService.sendTextMessage(senderId, req);
                        log.info("[WEBSOCKET DISPATCHED] Message from [{}] to [{}] processed via WebSocket", senderId, receiverId);
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("WebSocket incoming message not a chat command: {}", ex.getMessage());
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

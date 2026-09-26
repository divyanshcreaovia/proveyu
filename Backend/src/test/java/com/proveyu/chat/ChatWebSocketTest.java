package com.proveyu.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proveyu.chat.websocket.ChatWebSocketEvent;
import com.proveyu.chat.websocket.ChatWebSocketSessionManager;
import com.proveyu.chat.websocket.WebSocketAuthInterceptor;
import com.proveyu.shared.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private ServerHttpRequest serverHttpRequest;

    @Mock
    private ServerHttpResponse serverHttpResponse;

    @Mock
    private WebSocketHandler webSocketHandler;

    @Mock
    private WebSocketSession session1;

    @Mock
    private WebSocketSession session2;

    private WebSocketAuthInterceptor authInterceptor;
    private ChatWebSocketSessionManager sessionManager;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        authInterceptor = new WebSocketAuthInterceptor(jwtTokenProvider);
        objectMapper = new ObjectMapper();
        sessionManager = new ChatWebSocketSessionManager(objectMapper);
    }

    @Test
    @DisplayName("WebSocket Auth: Valid token in query param allows handshake and extracts userId")
    void testAuth_ValidTokenInQueryParam() {
        UUID userId = UUID.randomUUID();
        when(serverHttpRequest.getURI()).thenReturn(URI.create("ws://localhost:8080/ws/chat?token=valid_jwt_token"));
        when(jwtTokenProvider.validateToken("valid_jwt_token")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("valid_jwt_token")).thenReturn(userId.toString());

        Map<String, Object> attributes = new HashMap<>();
        boolean result = authInterceptor.beforeHandshake(serverHttpRequest, serverHttpResponse, webSocketHandler, attributes);

        assertThat(result).isTrue();
        assertThat(attributes.get(WebSocketAuthInterceptor.USER_ID_ATTR)).isEqualTo(userId);
    }

    @Test
    @DisplayName("WebSocket Auth: Valid token in Authorization header allows handshake")
    void testAuth_ValidTokenInHeader() {
        UUID userId = UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer header_token");

        when(serverHttpRequest.getURI()).thenReturn(URI.create("ws://localhost:8080/ws/chat"));
        when(serverHttpRequest.getHeaders()).thenReturn(headers);
        when(jwtTokenProvider.validateToken("header_token")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("header_token")).thenReturn(userId.toString());

        Map<String, Object> attributes = new HashMap<>();
        boolean result = authInterceptor.beforeHandshake(serverHttpRequest, serverHttpResponse, webSocketHandler, attributes);

        assertThat(result).isTrue();
        assertThat(attributes.get(WebSocketAuthInterceptor.USER_ID_ATTR)).isEqualTo(userId);
    }

    @Test
    @DisplayName("WebSocket Auth: Rejects handshake with HTTP 401 when token is missing")
    void testAuth_MissingToken() {
        when(serverHttpRequest.getURI()).thenReturn(URI.create("ws://localhost:8080/ws/chat"));
        when(serverHttpRequest.getHeaders()).thenReturn(new HttpHeaders());

        Map<String, Object> attributes = new HashMap<>();
        boolean result = authInterceptor.beforeHandshake(serverHttpRequest, serverHttpResponse, webSocketHandler, attributes);

        assertThat(result).isFalse();
        verify(serverHttpResponse).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("WebSocket Auth: Rejects handshake with HTTP 401 when token is invalid")
    void testAuth_InvalidToken() {
        when(serverHttpRequest.getURI()).thenReturn(URI.create("ws://localhost:8080/ws/chat?token=bad_token"));
        when(jwtTokenProvider.validateToken("bad_token")).thenReturn(false);

        Map<String, Object> attributes = new HashMap<>();
        boolean result = authInterceptor.beforeHandshake(serverHttpRequest, serverHttpResponse, webSocketHandler, attributes);

        assertThat(result).isFalse();
        verify(serverHttpResponse).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Session Manager: Supports multiple sessions per user and delivers message to all open sessions")
    void testSessionManager_MultipleSessionsAndDelivery() throws IOException {
        UUID userId = UUID.randomUUID();
        when(session1.getId()).thenReturn("sess-1");
        when(session1.isOpen()).thenReturn(true);
        when(session2.getId()).thenReturn("sess-2");
        when(session2.isOpen()).thenReturn(true);

        // Add 2 tabs/sessions for the same user
        sessionManager.addSession(userId, session1);
        sessionManager.addSession(userId, session2);

        assertThat(sessionManager.isUserOnline(userId)).isTrue();
        assertThat(sessionManager.getActiveSessionCount(userId)).isEqualTo(2);

        // Send event to user
        ChatWebSocketEvent<String> event = new ChatWebSocketEvent<>("message.new", "test payload");
        sessionManager.sendToUser(userId, event);

        // Both sessions should receive the TextMessage
        verify(session1, times(1)).sendMessage(any(TextMessage.class));
        verify(session2, times(1)).sendMessage(any(TextMessage.class));

        // Disconnect session1
        sessionManager.removeSession(userId, session1);
        assertThat(sessionManager.isUserOnline(userId)).isTrue();
        assertThat(sessionManager.getActiveSessionCount(userId)).isEqualTo(1);

        // Disconnect session2
        sessionManager.removeSession(userId, session2);
        assertThat(sessionManager.isUserOnline(userId)).isFalse();
        assertThat(sessionManager.getActiveSessionCount(userId)).isEqualTo(0);
    }

    @Test
    @DisplayName("Session Manager: Handles offline user gracefully without exceptions")
    void testSessionManager_OfflineUser() {
        UUID offlineUserId = UUID.randomUUID();

        assertThat(sessionManager.isUserOnline(offlineUserId)).isFalse();

        // Sending to offline user should not throw any exception
        ChatWebSocketEvent<String> event = new ChatWebSocketEvent<>("message.new", "offline");
        sessionManager.sendToUser(offlineUserId, event);
    }
}

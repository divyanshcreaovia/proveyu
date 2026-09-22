package com.proveyu.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proveyu.chat.application.ChatService;
import com.proveyu.chat.application.ChatService.ChatFileAttachment;
import com.proveyu.chat.domain.MessageStatus;
import com.proveyu.chat.domain.MessageType;
import com.proveyu.chat.web.ChatController;
import com.proveyu.chat.web.dto.ChatDto.ChatMessageResponse;
import com.proveyu.chat.web.dto.ChatDto.SendTextMessageRequest;
import com.proveyu.chat.web.dto.ChatDto.UnreadNotificationSummary;
import com.proveyu.chat.web.dto.ChatDto.UnreadSenderNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    @InjectMocks
    private ChatController chatController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID currentUserId;
    private UUID receiverId;
    private UUID messageId;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(chatController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
        objectMapper = new ObjectMapper();

        currentUserId = UUID.randomUUID();
        receiverId = UUID.randomUUID();
        messageId = UUID.randomUUID();

        authentication = new UsernamePasswordAuthenticationToken(
                currentUserId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_CANDIDATE")));
    }

    @Test
    @DisplayName("REST POST /api/chat/messages: Successfully sends text message")
    void testSendTextMessage_Endpoint() throws Exception {
        SendTextMessageRequest request = new SendTextMessageRequest(receiverId, "Hello recruiter");

        ChatMessageResponse response = ChatMessageResponse.builder()
                .id(messageId)
                .senderId(currentUserId)
                .receiverId(receiverId)
                .message("Hello recruiter")
                .messageType(MessageType.TEXT)
                .status(MessageStatus.SENT)
                .createdAt(Instant.now())
                .build();

        when(chatService.sendTextMessage(eq(currentUserId), any(SendTextMessageRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/chat/messages")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(messageId.toString()))
                .andExpect(jsonPath("$.data.message").value("Hello recruiter"))
                .andExpect(jsonPath("$.data.messageType").value("TEXT"));
    }

    @Test
    @DisplayName("REST POST /api/chat/messages/file: Successfully sends multipart file message")
    void testSendFileMessage_Endpoint() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", "Dummy PDF content".getBytes());

        ChatMessageResponse response = ChatMessageResponse.builder()
                .id(messageId)
                .senderId(currentUserId)
                .receiverId(receiverId)
                .message("Here is my resume")
                .messageType(MessageType.FILE)
                .fileName("document.pdf")
                .fileSize(18L)
                .fileContentType("application/pdf")
                .status(MessageStatus.SENT)
                .build();

        when(chatService.sendFileMessage(eq(currentUserId), eq(receiverId), eq("Here is my resume"), any()))
                .thenReturn(response);

        mockMvc.perform(multipart("/api/chat/messages/file")
                        .file(file)
                        .param("receiverId", receiverId.toString())
                        .param("message", "Here is my resume")
                        .principal(authentication))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileName").value("document.pdf"))
                .andExpect(jsonPath("$.data.messageType").value("FILE"));
    }

    @Test
    @DisplayName("REST GET /api/chat/messages/{userId}: Retrieves paginated conversation history")
    void testGetConversationHistory_Endpoint() throws Exception {
        ChatMessageResponse msg = ChatMessageResponse.builder()
                .id(messageId)
                .senderId(currentUserId)
                .receiverId(receiverId)
                .message("Conversation item")
                .build();

        when(chatService.getConversationHistory(eq(currentUserId), eq(receiverId), any()))
                .thenReturn(new PageImpl<>(List.of(msg), PageRequest.of(0, 50), 1));

        mockMvc.perform(get("/api/chat/messages/" + receiverId)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].message").value("Conversation item"));
    }

    @Test
    @DisplayName("REST GET /api/chat/unread: Retrieves unread messages for authenticated user")
    void testGetUnreadMessages_Endpoint() throws Exception {
        ChatMessageResponse unread = ChatMessageResponse.builder()
                .id(messageId)
                .senderId(receiverId)
                .receiverId(currentUserId)
                .message("Unread notification")
                .status(MessageStatus.SENT)
                .build();

        when(chatService.getUnreadMessages(eq(currentUserId)))
                .thenReturn(List.of(unread));

        mockMvc.perform(get("/api/chat/unread")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].message").value("Unread notification"));
    }

    @Test
    @DisplayName("REST GET /api/chat/notifications: Retrieves login notification summary with count and sender names")
    void testGetUnreadNotifications_Endpoint() throws Exception {
        UnreadSenderNotification senderNotification = UnreadSenderNotification.builder()
                .senderId(receiverId)
                .senderName("Google Recruiter")
                .senderEmail("recruiter@google.com")
                .unreadCount(2L)
                .lastMessage("Interview invitation confirmed")
                .lastMessageAt(Instant.now())
                .build();

        UnreadNotificationSummary summary = UnreadNotificationSummary.builder()
                .totalUnreadCount(2L)
                .senders(List.of(senderNotification))
                .build();

        when(chatService.getUnreadNotificationSummary(eq(currentUserId)))
                .thenReturn(summary);

        mockMvc.perform(get("/api/chat/notifications")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalUnreadCount").value(2))
                .andExpect(jsonPath("$.data.senders[0].senderName").value("Google Recruiter"))
                .andExpect(jsonPath("$.data.senders[0].unreadCount").value(2))
                .andExpect(jsonPath("$.data.senders[0].lastMessage").value("Interview invitation confirmed"));
    }

    @Test
    @DisplayName("REST PATCH /api/chat/messages/{messageId}/read: Marks message as read")
    void testMarkAsRead_Endpoint() throws Exception {
        ChatMessageResponse readMsg = ChatMessageResponse.builder()
                .id(messageId)
                .status(MessageStatus.READ)
                .readAt(Instant.now())
                .build();

        when(chatService.markMessageAsRead(eq(currentUserId), eq(messageId)))
                .thenReturn(readMsg);

        mockMvc.perform(patch("/api/chat/messages/" + messageId + "/read")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("READ"));
    }

    @Test
    @DisplayName("REST GET /api/chat/messages/{messageId}/file: Streams file attachment securely")
    void testDownloadFile_Endpoint() throws Exception {
        ByteArrayResource resource = new ByteArrayResource("PDF Content".getBytes());
        ChatFileAttachment attachment = new ChatFileAttachment(
                resource, "resume.pdf", "application/pdf", 11L);

        when(chatService.loadChatAttachment(eq(currentUserId), eq(messageId)))
                .thenReturn(attachment);

        mockMvc.perform(get("/api/chat/messages/" + messageId + "/file")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"resume.pdf\""))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(content().bytes("PDF Content".getBytes()));
    }
}

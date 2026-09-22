package com.proveyu.chat;

import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.chat.application.ChatService;
import com.proveyu.chat.domain.ChatMessage;
import com.proveyu.chat.domain.MessageStatus;
import com.proveyu.chat.domain.MessageType;
import com.proveyu.chat.infrastructure.ChatMessageRepository;
import com.proveyu.chat.web.dto.ChatDto.ChatMessageResponse;
import com.proveyu.chat.web.dto.ChatDto.SendTextMessageRequest;
import com.proveyu.chat.web.dto.ChatDto.UnreadNotificationSummary;
import com.proveyu.chat.websocket.ChatWebSocketEvent;
import com.proveyu.chat.websocket.ChatWebSocketHandler;
import com.proveyu.shared.error.DomainException;
import com.proveyu.shared.storage.FileStorageService;
import com.proveyu.shared.storage.StoredChatFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ChatWebSocketHandler chatWebSocketHandler;

    @InjectMocks
    private ChatService chatService;

    private UUID senderId;
    private UUID receiverId;
    private UUID messageId;

    @BeforeEach
    void setUp() {
        senderId = UUID.randomUUID();
        receiverId = UUID.randomUUID();
        messageId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Send text message successfully: Persists to DB and publishes message.new WebSocket event")
    void testSendTextMessage_Success() {
        SendTextMessageRequest request = new SendTextMessageRequest(receiverId, "Hello, are you available for an interview?");

        when(userRepository.existsById(receiverId)).thenReturn(true);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            return new ChatMessage(
                    messageId,
                    msg.getSenderId(),
                    msg.getReceiverId(),
                    msg.getMessage(),
                    msg.getMessageType(),
                    null, null, null, null,
                    MessageStatus.SENT,
                    Instant.now(),
                    Instant.now(),
                    null
            );
        });

        ChatMessageResponse response = chatService.sendTextMessage(senderId, request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(messageId);
        assertThat(response.getSenderId()).isEqualTo(senderId);
        assertThat(response.getReceiverId()).isEqualTo(receiverId);
        assertThat(response.getMessage()).isEqualTo("Hello, are you available for an interview?");
        assertThat(response.getMessageType()).isEqualTo(MessageType.TEXT);
        assertThat(response.getStatus()).isEqualTo(MessageStatus.SENT);

        // Verify WebSocket event published to receiver and sender
        verify(chatWebSocketHandler, times(1)).publishEvent(eq(receiverId), any(ChatWebSocketEvent.class));
        verify(chatWebSocketHandler, times(1)).publishEvent(eq(senderId), any(ChatWebSocketEvent.class));
    }

    @Test
    @DisplayName("Send text message: Rejects sending message to oneself")
    void testSendTextMessage_SelfMessageRejected() {
        SendTextMessageRequest request = new SendTextMessageRequest(senderId, "Hello myself");

        assertThatThrownBy(() -> chatService.sendTextMessage(senderId, request))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SELF_MESSAGE_NOT_ALLOWED")
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);

        verify(chatMessageRepository, never()).save(any());
        verify(chatWebSocketHandler, never()).publishEvent(any(), any());
    }

    @Test
    @DisplayName("Send text message: Rejects if receiver does not exist in DB")
    void testSendTextMessage_ReceiverNotFound() {
        SendTextMessageRequest request = new SendTextMessageRequest(receiverId, "Hello");
        when(userRepository.existsById(receiverId)).thenReturn(false);

        assertThatThrownBy(() -> chatService.sendTextMessage(senderId, request))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", "USER_NOT_FOUND")
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("Send text message: Rejects empty or whitespace-only message")
    void testSendTextMessage_EmptyMessageRejected() {
        SendTextMessageRequest request = new SendTextMessageRequest(receiverId, "   ");
        when(userRepository.existsById(receiverId)).thenReturn(true);

        assertThatThrownBy(() -> chatService.sendTextMessage(senderId, request))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", "EMPTY_MESSAGE")
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("Send file message successfully: Stores file, saves metadata to DB, publishes WebSocket event")
    void testSendFileMessage_Success() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "offer_letter.pdf", "application/pdf", "Dummy PDF content".getBytes());

        StoredChatFile storedFile = new StoredChatFile("chat_sender_123.pdf", "offer_letter.pdf", 18L, "application/pdf");
        when(userRepository.existsById(receiverId)).thenReturn(true);
        when(fileStorageService.storeChatFile(file, senderId)).thenReturn(storedFile);

        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            return new ChatMessage(
                    messageId,
                    msg.getSenderId(),
                    msg.getReceiverId(),
                    msg.getMessage(),
                    msg.getMessageType(),
                    msg.getFilePath(),
                    msg.getFileName(),
                    msg.getFileSize(),
                    msg.getFileContentType(),
                    MessageStatus.SENT,
                    Instant.now(),
                    Instant.now(),
                    null
            );
        });

        ChatMessageResponse response = chatService.sendFileMessage(senderId, receiverId, "Here is the offer", file);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(messageId);
        assertThat(response.getMessageType()).isEqualTo(MessageType.FILE);
        assertThat(response.getFilePath()).isEqualTo("chat_sender_123.pdf");
        assertThat(response.getFileName()).isEqualTo("offer_letter.pdf");
        assertThat(response.getFileSize()).isEqualTo(18L);
        assertThat(response.getFileContentType()).isEqualTo("application/pdf");
        assertThat(response.getMessage()).isEqualTo("Here is the offer");

        verify(chatWebSocketHandler, times(1)).publishEvent(eq(receiverId), any(ChatWebSocketEvent.class));
    }

    @Test
    @DisplayName("Send file message: Rejects empty file")
    void testSendFileMessage_EmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        when(userRepository.existsById(receiverId)).thenReturn(true);

        assertThatThrownBy(() -> chatService.sendFileMessage(senderId, receiverId, "text", emptyFile))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", "EMPTY_FILE");

        verify(fileStorageService, never()).storeChatFile(any(), any());
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("Get conversation history with pagination")
    void testGetConversationHistory_Success() {
        Pageable pageable = PageRequest.of(0, 20);
        ChatMessage msg1 = ChatMessage.builder()
                .id(UUID.randomUUID())
                .senderId(senderId)
                .receiverId(receiverId)
                .message("Hi")
                .messageType(MessageType.TEXT)
                .status(MessageStatus.SENT)
                .createdAt(Instant.now().minusSeconds(60))
                .build();

        when(userRepository.existsById(receiverId)).thenReturn(true);
        when(chatMessageRepository.findConversation(senderId, receiverId, pageable))
                .thenReturn(new PageImpl<>(List.of(msg1), pageable, 1));

        Page<ChatMessageResponse> result = chatService.getConversationHistory(senderId, receiverId, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getMessage()).isEqualTo("Hi");
    }

    @Test
    @DisplayName("Get conversation history: Rejects if other user not found")
    void testGetConversationHistory_UserNotFound() {
        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.existsById(receiverId)).thenReturn(false);

        assertThatThrownBy(() -> chatService.getConversationHistory(senderId, receiverId, pageable))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", "USER_NOT_FOUND");
    }

    @Test
    @DisplayName("Get unread messages for authenticated user")
    void testGetUnreadMessages() {
        ChatMessage unreadMsg = ChatMessage.builder()
                .id(messageId)
                .senderId(senderId)
                .receiverId(receiverId)
                .message("Unread message")
                .status(MessageStatus.SENT)
                .build();

        when(chatMessageRepository.findUnreadMessages(receiverId)).thenReturn(List.of(unreadMsg));

        List<ChatMessageResponse> unread = chatService.getUnreadMessages(receiverId);

        assertThat(unread).hasSize(1);
        assertThat(unread.get(0).getMessage()).isEqualTo("Unread message");
    }

    @Test
    @DisplayName("Get unread notifications: Returns total count and sender names for login notification")
    void testUnreadNotificationSummary_Success() {
        UUID sender2Id = UUID.randomUUID();

        ChatMessage msg1 = ChatMessage.builder()
                .id(UUID.randomUUID())
                .senderId(senderId)
                .receiverId(receiverId)
                .message("Hello candidate, please share your resume")
                .messageType(MessageType.TEXT)
                .status(MessageStatus.SENT)
                .createdAt(Instant.now().minusSeconds(120))
                .build();

        ChatMessage msg2 = ChatMessage.builder()
                .id(UUID.randomUUID())
                .senderId(senderId)
                .receiverId(receiverId)
                .message("Also let us know your availability")
                .messageType(MessageType.TEXT)
                .status(MessageStatus.SENT)
                .createdAt(Instant.now().minusSeconds(60))
                .build();

        ChatMessage msg3 = ChatMessage.builder()
                .id(UUID.randomUUID())
                .senderId(sender2Id)
                .receiverId(receiverId)
                .message("Invitation to interview")
                .messageType(MessageType.TEXT)
                .status(MessageStatus.SENT)
                .createdAt(Instant.now().minusSeconds(30))
                .build();

        when(chatMessageRepository.findUnreadMessages(receiverId))
                .thenReturn(List.of(msg3, msg2, msg1)); // ordered desc

        User user1 = User.builder().id(senderId).fullName("Sarah Recruiter").email("sarah@company.com").build();
        User user2 = User.builder().id(sender2Id).fullName("John TechLead").email("john@company.com").build();

        when(userRepository.findAllById(any())).thenReturn(List.of(user1, user2));

        UnreadNotificationSummary summary = chatService.getUnreadNotificationSummary(receiverId);

        assertThat(summary).isNotNull();
        assertThat(summary.getTotalUnreadCount()).isEqualTo(3);
        assertThat(summary.getSenders()).hasSize(2);

        // Verify sender details and counts
        assertThat(summary.getSenders())
                .extracting("senderName")
                .containsExactlyInAnyOrder("Sarah Recruiter", "John TechLead");

        var sarahNotification = summary.getSenders().stream()
                .filter(s -> s.getSenderName().equals("Sarah Recruiter"))
                .findFirst().orElseThrow();
        assertThat(sarahNotification.getUnreadCount()).isEqualTo(2);
        assertThat(sarahNotification.getLastMessage()).isEqualTo("Also let us know your availability");

        var johnNotification = summary.getSenders().stream()
                .filter(s -> s.getSenderName().equals("John TechLead"))
                .findFirst().orElseThrow();
        assertThat(johnNotification.getUnreadCount()).isEqualTo(1);
        assertThat(johnNotification.getLastMessage()).isEqualTo("Invitation to interview");
    }

    @Test
    @DisplayName("Get unread notifications: Returns zero count and empty list when no unread messages")
    void testUnreadNotificationSummary_Empty() {
        when(chatMessageRepository.findUnreadMessages(receiverId)).thenReturn(List.of());

        UnreadNotificationSummary summary = chatService.getUnreadNotificationSummary(receiverId);

        assertThat(summary).isNotNull();
        assertThat(summary.getTotalUnreadCount()).isEqualTo(0);
        assertThat(summary.getSenders()).isEmpty();
    }

    @Test
    @DisplayName("Mark message as read: Only receiver can mark as read")
    void testMarkMessageAsRead_Success() {
        ChatMessage message = ChatMessage.builder()
                .id(messageId)
                .senderId(senderId)
                .receiverId(receiverId)
                .message("Please read this")
                .status(MessageStatus.SENT)
                .build();

        when(chatMessageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        ChatMessageResponse response = chatService.markMessageAsRead(receiverId, messageId);

        assertThat(response.getStatus()).isEqualTo(MessageStatus.READ);
        assertThat(response.getReadAt()).isNotNull();

        // WebSocket notification to sender of read receipt
        verify(chatWebSocketHandler, times(1)).publishEvent(eq(senderId), any(ChatWebSocketEvent.class));
    }

    @Test
    @DisplayName("Mark message as read: Rejects if caller is not the receiver (e.g. sender or stranger)")
    void testMarkMessageAsRead_Unauthorized() {
        ChatMessage message = ChatMessage.builder()
                .id(messageId)
                .senderId(senderId)
                .receiverId(receiverId)
                .message("Please read this")
                .status(MessageStatus.SENT)
                .build();

        when(chatMessageRepository.findById(messageId)).thenReturn(Optional.of(message));

        // Sender tries to mark as read
        assertThatThrownBy(() -> chatService.markMessageAsRead(senderId, messageId))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", "UNAUTHORIZED_MESSAGE_ACCESS")
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("Secure file access: Sender or Receiver can download attachment")
    void testLoadChatAttachment_Authorized() {
        ChatMessage fileMessage = ChatMessage.builder()
                .id(messageId)
                .senderId(senderId)
                .receiverId(receiverId)
                .messageType(MessageType.FILE)
                .filePath("chat_safe_file.pdf")
                .fileName("resume.pdf")
                .fileContentType("application/pdf")
                .fileSize(1024L)
                .build();

        Resource mockResource = new ByteArrayResource("test".getBytes());
        when(chatMessageRepository.findById(messageId)).thenReturn(Optional.of(fileMessage));
        when(fileStorageService.loadChatFileAsResource("chat_safe_file.pdf")).thenReturn(mockResource);

        // Receiver accesses file
        ChatService.ChatFileAttachment attachment = chatService.loadChatAttachment(receiverId, messageId);
        assertThat(attachment.fileName()).isEqualTo("resume.pdf");
        assertThat(attachment.fileSize()).isEqualTo(1024L);

        // Sender accesses file
        ChatService.ChatFileAttachment attachment2 = chatService.loadChatAttachment(senderId, messageId);
        assertThat(attachment2.fileName()).isEqualTo("resume.pdf");
    }

    @Test
    @DisplayName("Secure file access: Unauthorized third-party is rejected with 403")
    void testLoadChatAttachment_UnauthorizedThirdParty() {
        UUID strangerId = UUID.randomUUID();
        ChatMessage fileMessage = ChatMessage.builder()
                .id(messageId)
                .senderId(senderId)
                .receiverId(receiverId)
                .messageType(MessageType.FILE)
                .filePath("chat_safe_file.pdf")
                .build();

        when(chatMessageRepository.findById(messageId)).thenReturn(Optional.of(fileMessage));

        assertThatThrownBy(() -> chatService.loadChatAttachment(strangerId, messageId))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", "UNAUTHORIZED_FILE_ACCESS")
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);

        verify(fileStorageService, never()).loadChatFileAsResource(any());
    }
}

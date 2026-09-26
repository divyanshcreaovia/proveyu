package com.proveyu.chat.application;

import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.chat.domain.ChatMessage;
import com.proveyu.chat.domain.MessageStatus;
import com.proveyu.chat.domain.MessageType;
import com.proveyu.chat.infrastructure.ChatMessageRepository;
import com.proveyu.chat.web.dto.ChatDto.*;
import com.proveyu.chat.websocket.ChatWebSocketEvent;
import com.proveyu.chat.websocket.ChatWebSocketHandler;
import com.proveyu.shared.error.DomainException;
import com.proveyu.shared.storage.FileStorageService;
import com.proveyu.shared.storage.StoredChatFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final ChatWebSocketHandler chatWebSocketHandler;

    @Transactional
    public ChatMessageResponse sendTextMessage(UUID senderId, SendTextMessageRequest request) {
        if (senderId == null) {
            throw new DomainException("Sender must be authenticated", HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }

        if (senderId.equals(request.getReceiverId())) {
            throw new DomainException("Cannot send message to yourself", HttpStatus.BAD_REQUEST, "SELF_MESSAGE_NOT_ALLOWED");
        }

        if (!userRepository.existsById(request.getReceiverId())) {
            throw new DomainException("Receiver user does not exist", HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
        }

        if (!StringUtils.hasText(request.getMessage())) {
            throw new DomainException("Message text cannot be empty", HttpStatus.BAD_REQUEST, "EMPTY_MESSAGE");
        }

        ChatMessage chatMessage = ChatMessage.builder()
                .senderId(senderId)
                .receiverId(request.getReceiverId())
                .message(request.getMessage().trim())
                .messageType(MessageType.TEXT)
                .status(MessageStatus.SENT)
                .build();

        // 1. Database is source of truth — persist before WebSocket delivery
        ChatMessage saved = chatMessageRepository.save(chatMessage);
        log.info("[CHAT PERSISTED] Text message id=[{}] from=[{}] to=[{}]", saved.getId(), senderId, request.getReceiverId());

        ChatMessageResponse response = ChatMessageResponse.fromEntity(saved);

        // 2. Real-time WebSocket delivery
        publishMessageNewEvent(saved.getReceiverId(), saved.getSenderId(), response);

        return response;
    }

    @Transactional
    public ChatMessageResponse sendFileMessage(UUID senderId, UUID receiverId, String messageText, MultipartFile file) {
        if (senderId == null) {
            throw new DomainException("Sender must be authenticated", HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }

        if (senderId.equals(receiverId)) {
            throw new DomainException("Cannot send file to yourself", HttpStatus.BAD_REQUEST, "SELF_MESSAGE_NOT_ALLOWED");
        }

        if (!userRepository.existsById(receiverId)) {
            throw new DomainException("Receiver user does not exist", HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
        }

        if (file == null || file.isEmpty()) {
            throw new DomainException("Chat file is required and cannot be empty", HttpStatus.BAD_REQUEST, "EMPTY_FILE");
        }

        // Store file using existing file storage service
        StoredChatFile storedFile = fileStorageService.storeChatFile(file, senderId);

        ChatMessage chatMessage = ChatMessage.builder()
                .senderId(senderId)
                .receiverId(receiverId)
                .message(StringUtils.hasText(messageText) ? messageText.trim() : null)
                .messageType(MessageType.FILE)
                .filePath(storedFile.storageFileName())
                .fileName(storedFile.originalFileName())
                .fileSize(storedFile.fileSize())
                .fileContentType(storedFile.contentType())
                .status(MessageStatus.SENT)
                .build();

        // 1. Database is source of truth
        ChatMessage saved = chatMessageRepository.save(chatMessage);
        log.info("[CHAT FILE PERSISTED] File message id=[{}] file=[{}] from=[{}] to=[{}]",
                saved.getId(), storedFile.originalFileName(), senderId, receiverId);

        ChatMessageResponse response = ChatMessageResponse.fromEntity(saved);

        // 2. Real-time WebSocket delivery
        publishMessageNewEvent(saved.getReceiverId(), saved.getSenderId(), response);

        return response;
    }

    @Transactional(readOnly = true)
    public Page<ChatMessageResponse> getConversationHistory(UUID currentUserId, UUID otherUserId, Pageable pageable) {
        if (!userRepository.existsById(otherUserId)) {
            throw new DomainException("Target user does not exist", HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
        }

        Page<ChatMessage> page = chatMessageRepository.findConversation(currentUserId, otherUserId, pageable);
        return page.map(ChatMessageResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getUnreadMessages(UUID currentUserId) {
        List<ChatMessage> unread = chatMessageRepository.findUnreadMessages(currentUserId);
        return unread.stream().map(ChatMessageResponse::fromEntity).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UnreadNotificationSummary getUnreadNotificationSummary(UUID currentUserId) {
        List<ChatMessage> unreadMessages = chatMessageRepository.findUnreadMessages(currentUserId);
        if (unreadMessages == null || unreadMessages.isEmpty()) {
            return UnreadNotificationSummary.builder()
                    .totalUnreadCount(0L)
                    .senders(Collections.emptyList())
                    .build();
        }

        // Group unread messages by senderId preserving arrival order (findUnreadMessages orders by createdAt DESC)
        Map<UUID, List<ChatMessage>> messagesBySender = unreadMessages.stream()
                .collect(Collectors.groupingBy(ChatMessage::getSenderId, LinkedHashMap::new, Collectors.toList()));

        // Batch load senders
        Map<UUID, User> userMap = userRepository.findAllById(messagesBySender.keySet()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<UnreadSenderNotification> senderNotifications = messagesBySender.entrySet().stream()
                .map(entry -> {
                    UUID senderId = entry.getKey();
                    List<ChatMessage> msgs = entry.getValue();
                    User sender = userMap.get(senderId);
                    String senderName = sender != null ? sender.getFullName() : "User (" + senderId.toString().substring(0, 8) + ")";
                    String senderEmail = sender != null ? sender.getEmail() : null;

                    ChatMessage latestMsg = msgs.get(0); // Most recent unread
                    String preview = latestMsg.getMessageType() == MessageType.FILE
                            ? "[Attachment: " + (latestMsg.getFileName() != null ? latestMsg.getFileName() : "file") + "]"
                            : latestMsg.getMessage();

                    return UnreadSenderNotification.builder()
                            .senderId(senderId)
                            .senderName(senderName)
                            .senderEmail(senderEmail)
                            .unreadCount(msgs.size())
                            .lastMessage(preview)
                            .lastMessageAt(latestMsg.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        return UnreadNotificationSummary.builder()
                .totalUnreadCount(unreadMessages.size())
                .senders(senderNotifications)
                .build();
    }

    @Transactional
    public ChatMessageResponse markMessageAsRead(UUID currentUserId, UUID messageId) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new DomainException("Message not found", HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND"));

        // Only the receiver of that message can mark it as read
        if (!message.getReceiverId().equals(currentUserId)) {
            throw new DomainException("Only the recipient of this message can mark it as read",
                    HttpStatus.FORBIDDEN, "UNAUTHORIZED_MESSAGE_ACCESS");
        }

        if (message.getStatus() != MessageStatus.READ) {
            message.setStatus(MessageStatus.READ);
            message.setReadAt(Instant.now());
            ChatMessage saved = chatMessageRepository.save(message);
            log.info("[CHAT MARK READ] Message id=[{}] marked as READ by receiver=[{}]", messageId, currentUserId);

            ChatMessageResponse response = ChatMessageResponse.fromEntity(saved);

            // Notify original sender of read receipt via WebSocket if online
            ChatWebSocketEvent<ChatMessageResponse> readEvent = new ChatWebSocketEvent<>("message.read", response);
            chatWebSocketHandler.publishEvent(saved.getSenderId(), readEvent);

            return response;
        }

        return ChatMessageResponse.fromEntity(message);
    }

    @Transactional(readOnly = true)
    public ChatFileAttachment loadChatAttachment(UUID currentUserId, UUID messageId) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new DomainException("Message not found", HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND"));

        // Verify authenticated user is either sender OR receiver
        if (!message.getSenderId().equals(currentUserId) && !message.getReceiverId().equals(currentUserId)) {
            throw new DomainException("Unauthorized: You do not participate in this conversation",
                    HttpStatus.FORBIDDEN, "UNAUTHORIZED_FILE_ACCESS");
        }

        if (message.getMessageType() != MessageType.FILE || !StringUtils.hasText(message.getFilePath())) {
            throw new DomainException("Message does not contain an accessible file attachment",
                    HttpStatus.BAD_REQUEST, "NOT_A_FILE_MESSAGE");
        }

        Resource resource = fileStorageService.loadChatFileAsResource(message.getFilePath());

        return new ChatFileAttachment(
                resource,
                message.getFileName() != null ? message.getFileName() : "attachment",
                message.getFileContentType() != null ? message.getFileContentType() : "application/octet-stream",
                message.getFileSize() != null ? message.getFileSize() : 0L
        );
    }

    private void publishMessageNewEvent(UUID receiverId, UUID senderId, ChatMessageResponse response) {
        ChatWebSocketEvent<ChatMessageResponse> event = new ChatWebSocketEvent<>("message.new", response);
        // Deliver to receiver (if online)
        chatWebSocketHandler.publishEvent(receiverId, event);
        // Deliver to sender's other sessions/tabs (if online)
        chatWebSocketHandler.publishEvent(senderId, event);
    }

    public record ChatFileAttachment(
            Resource resource,
            String fileName,
            String contentType,
            long fileSize
    ) {}
}

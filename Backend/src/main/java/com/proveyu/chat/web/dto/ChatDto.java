package com.proveyu.chat.web.dto;

import com.proveyu.chat.domain.ChatMessage;
import com.proveyu.chat.domain.MessageStatus;
import com.proveyu.chat.domain.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

public class ChatDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SendTextMessageRequest {
        @NotNull(message = "Receiver ID is required")
        private UUID receiverId;

        @NotBlank(message = "Message text cannot be empty")
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChatMessageResponse {
        private UUID id;
        private UUID senderId;
        private UUID receiverId;
        private String message;
        private MessageType messageType;
        private String filePath;
        private String fileName;
        private Long fileSize;
        private String fileContentType;
        private MessageStatus status;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant readAt;

        public static ChatMessageResponse fromEntity(ChatMessage entity) {
            if (entity == null) {
                return null;
            }
            return ChatMessageResponse.builder()
                    .id(entity.getId())
                    .senderId(entity.getSenderId())
                    .receiverId(entity.getReceiverId())
                    .message(entity.getMessage())
                    .messageType(entity.getMessageType())
                    .filePath(entity.getFilePath())
                    .fileName(entity.getFileName())
                    .fileSize(entity.getFileSize())
                    .fileContentType(entity.getFileContentType())
                    .status(entity.getStatus())
                    .createdAt(entity.getCreatedAt())
                    .updatedAt(entity.getUpdatedAt())
                    .readAt(entity.getReadAt())
                    .build();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UnreadSenderNotification {
        private UUID senderId;
        private String senderName;
        private String senderEmail;
        private long unreadCount;
        private String lastMessage;
        private Instant lastMessageAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UnreadNotificationSummary {
        private long totalUnreadCount;
        private java.util.List<UnreadSenderNotification> senders;
    }
}

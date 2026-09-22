package com.proveyu.chat.domain;

import com.proveyu.auth.domain.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_sender_id", columnList = "sender_id"),
        @Index(name = "idx_chat_receiver_id", columnList = "receiver_id"),
        @Index(name = "idx_chat_sender_receiver_created", columnList = "sender_id, receiver_id, created_at"),
        @Index(name = "idx_chat_receiver_sender_created", columnList = "receiver_id, sender_id, created_at"),
        @Index(name = "idx_chat_receiver_status", columnList = "receiver_id, status")
})
public class ChatMessage {

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "sender_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID senderId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "receiver_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID receiverId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", insertable = false, updatable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", insertable = false, updatable = false)
    private User receiver;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private MessageType messageType = MessageType.TEXT;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_content_type", length = 100)
    private String fileContentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MessageStatus status = MessageStatus.SENT;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "read_at")
    private Instant readAt;

    public ChatMessage() {}

    public ChatMessage(UUID id, UUID senderId, UUID receiverId, String message, MessageType messageType,
                       String filePath, String fileName, Long fileSize, String fileContentType,
                       MessageStatus status, Instant createdAt, Instant updatedAt, Instant readAt) {
        this.id = id;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.message = message;
        this.messageType = messageType != null ? messageType : MessageType.TEXT;
        this.filePath = filePath;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileContentType = fileContentType;
        this.status = status != null ? status : MessageStatus.SENT;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.readAt = readAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSenderId() { return senderId; }
    public void setSenderId(UUID senderId) { this.senderId = senderId; }

    public UUID getReceiverId() { return receiverId; }
    public void setReceiverId(UUID receiverId) { this.receiverId = receiverId; }

    public User getSender() { return sender; }
    public User getReceiver() { return receiver; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public MessageType getMessageType() { return messageType; }
    public void setMessageType(MessageType messageType) { this.messageType = messageType; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getFileContentType() { return fileContentType; }
    public void setFileContentType(String fileContentType) { this.fileContentType = fileContentType; }

    public MessageStatus getStatus() { return status; }
    public void setStatus(MessageStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }

    public static ChatMessageBuilder builder() {
        return new ChatMessageBuilder();
    }

    public static class ChatMessageBuilder {
        private UUID id;
        private UUID senderId;
        private UUID receiverId;
        private String message;
        private MessageType messageType = MessageType.TEXT;
        private String filePath;
        private String fileName;
        private Long fileSize;
        private String fileContentType;
        private MessageStatus status = MessageStatus.SENT;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant readAt;

        public ChatMessageBuilder id(UUID id) { this.id = id; return this; }
        public ChatMessageBuilder senderId(UUID senderId) { this.senderId = senderId; return this; }
        public ChatMessageBuilder receiverId(UUID receiverId) { this.receiverId = receiverId; return this; }
        public ChatMessageBuilder message(String message) { this.message = message; return this; }
        public ChatMessageBuilder messageType(MessageType messageType) { this.messageType = messageType; return this; }
        public ChatMessageBuilder filePath(String filePath) { this.filePath = filePath; return this; }
        public ChatMessageBuilder fileName(String fileName) { this.fileName = fileName; return this; }
        public ChatMessageBuilder fileSize(Long fileSize) { this.fileSize = fileSize; return this; }
        public ChatMessageBuilder fileContentType(String fileContentType) { this.fileContentType = fileContentType; return this; }
        public ChatMessageBuilder status(MessageStatus status) { this.status = status; return this; }
        public ChatMessageBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public ChatMessageBuilder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public ChatMessageBuilder readAt(Instant readAt) { this.readAt = readAt; return this; }

        public ChatMessage build() {
            return new ChatMessage(id, senderId, receiverId, message, messageType, filePath, fileName, fileSize,
                    fileContentType, status, createdAt, updatedAt, readAt);
        }
    }
}

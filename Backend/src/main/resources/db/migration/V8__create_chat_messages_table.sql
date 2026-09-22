-- ============================================================
-- PROVEYU Database Schema (MySQL 8.0)
-- Migration: V8__create_chat_messages_table.sql
-- Description: Create chat_messages table for real-time text & file chat
-- ============================================================

CREATE TABLE IF NOT EXISTS chat_messages (
    id                  CHAR(36) NOT NULL PRIMARY KEY,
    sender_id           CHAR(36) NOT NULL,
    receiver_id         CHAR(36) NOT NULL,
    message             TEXT NULL,
    message_type        VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    file_path           VARCHAR(500) NULL,
    file_name           VARCHAR(255) NULL,
    file_size           BIGINT NULL,
    file_content_type   VARCHAR(100) NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'SENT',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    read_at             DATETIME NULL,
    CONSTRAINT fk_chat_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_receiver FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_chat_sender_id (sender_id),
    INDEX idx_chat_receiver_id (receiver_id),
    INDEX idx_chat_sender_receiver_created (sender_id, receiver_id, created_at),
    INDEX idx_chat_receiver_sender_created (receiver_id, sender_id, created_at),
    INDEX idx_chat_receiver_status (receiver_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

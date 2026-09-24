package com.proveyu.notification.application.dto;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class NotificationDto {
    private UUID id;
    private String type;
    private String message;
    private boolean isRead;
    private Instant createdAt;
}

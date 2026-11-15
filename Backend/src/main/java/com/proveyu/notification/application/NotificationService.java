package com.proveyu.notification.application;

import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.notification.application.dto.NotificationDto;
import com.proveyu.notification.domain.Notification;
import com.proveyu.notification.domain.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<NotificationDto> getUserNotifications(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new com.proveyu.shared.error.DomainException("User not found", org.springframework.http.HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));
        return notificationRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new com.proveyu.shared.error.DomainException("Notification not found", org.springframework.http.HttpStatus.NOT_FOUND, "NOT_FOUND"));
        
        if (!notification.getUser().getId().equals(userId)) {
            throw new com.proveyu.shared.error.DomainException("Not your notification", org.springframework.http.HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
        
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    /** Creates an in-app notification for a user (no-op if the user does not exist). */
    @Transactional
    public void notify(UUID userId, String type, String message) {
        if (userId == null) return;
        userRepository.findById(userId).ifPresent(user -> {
            Notification n = new Notification();
            n.setUser(user);
            n.setType(type.length() > 50 ? type.substring(0, 50) : type);
            n.setMessage(message);
            notificationRepository.save(n);
        });
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> notificationRepository.findByUserOrderByCreatedAtDesc(user).forEach(n -> {
            if (!n.isRead()) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        }));
    }

    private NotificationDto mapToDto(Notification entity) {
        NotificationDto dto = new NotificationDto();
        dto.setId(entity.getId());
        dto.setType(entity.getType());
        dto.setMessage(entity.getMessage());
        dto.setRead(entity.isRead());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}

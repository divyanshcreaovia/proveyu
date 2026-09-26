package com.proveyu.chat.web;

import com.proveyu.chat.application.ChatService;
import com.proveyu.chat.application.ChatService.ChatFileAttachment;
import com.proveyu.chat.web.dto.ChatDto.ChatMessageResponse;
import com.proveyu.chat.web.dto.ChatDto.SendTextMessageRequest;
import com.proveyu.chat.web.dto.ChatDto.UnreadNotificationSummary;
import com.proveyu.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping({"/api/chat", "/api/v1/chat"})
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendTextMessage(
            @Valid @RequestBody SendTextMessageRequest request,
            Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        ChatMessageResponse response = chatService.sendTextMessage(currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Message sent successfully"));
    }

    @PostMapping(value = "/messages/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendFileMessage(
            @RequestParam("receiverId") UUID receiverId,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        ChatMessageResponse response = chatService.sendFileMessage(currentUserId, receiverId, message, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "File message sent successfully"));
    }

    @GetMapping("/messages/{userId}")
    public ResponseEntity<ApiResponse<Page<ChatMessageResponse>>> getConversation(
            @PathVariable UUID userId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable,
            Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        Page<ChatMessageResponse> history = chatService.getConversationHistory(currentUserId, userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(history, "Conversation history retrieved successfully"));
    }

    @GetMapping("/unread")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getUnreadMessages(
            Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        List<ChatMessageResponse> unread = chatService.getUnreadMessages(currentUserId);
        return ResponseEntity.ok(ApiResponse.success(unread, "Unread messages retrieved successfully"));
    }

    @GetMapping({"/notifications", "/unread/notifications"})
    public ResponseEntity<ApiResponse<UnreadNotificationSummary>> getUnreadNotifications(
            Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        UnreadNotificationSummary summary = chatService.getUnreadNotificationSummary(currentUserId);
        return ResponseEntity.ok(ApiResponse.success(summary, "Unread notifications retrieved successfully"));
    }

    @PatchMapping("/messages/{messageId}/read")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> markAsRead(
            @PathVariable UUID messageId,
            Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        ChatMessageResponse response = chatService.markMessageAsRead(currentUserId, messageId);
        return ResponseEntity.ok(ApiResponse.success(response, "Message marked as read"));
    }

    @GetMapping("/messages/{messageId}/file")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable UUID messageId,
            Authentication authentication) {
        UUID currentUserId = UUID.fromString(authentication.getName());
        ChatFileAttachment attachment = chatService.loadChatAttachment(currentUserId, messageId);

        String encodedFileName = URLEncoder.encode(attachment.fileName(), StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(attachment.fileSize()))
                .body(attachment.resource());
    }
}

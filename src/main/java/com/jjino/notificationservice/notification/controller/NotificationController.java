package com.jjino.notificationservice.notification.controller;

import static org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE;

import com.jjino.notificationservice.global.auth.CurrentUserId;
import com.jjino.notificationservice.notification.controller.dto.CreateNotificationRequest;
import com.jjino.notificationservice.notification.controller.dto.NotificationResponse;
import com.jjino.notificationservice.notification.service.NotificationService;
import com.jjino.notificationservice.notification.service.SseEmitterService;
import com.jjino.notificationservice.notification.service.dto.CreateNotificationCommand;
import com.jjino.notificationservice.notification.service.dto.NotificationInfo;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final SseEmitterService sseEmitterService;

    // sse 구독
    @GetMapping(value = "/stream", produces = TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@CurrentUserId Long userId) {
        return sseEmitterService.subscribe(userId);
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> send(@Valid @RequestBody CreateNotificationRequest request) {
        NotificationInfo info = notificationService.send(
                new CreateNotificationCommand(request.userId(), request.type(), request.title(), request.content()));
        return ResponseEntity.status(HttpStatus.CREATED).body(NotificationResponse.from(info));
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getAll(@CurrentUserId Long userId) {
        return ResponseEntity.ok(
                notificationService.getAll(userId)
                        .stream()
                        .map(NotificationResponse::from)
                        .toList()
        );
    }

    @GetMapping("/unread")
    public ResponseEntity<List<NotificationResponse>> getUnread(@CurrentUserId Long userId) {
        return ResponseEntity.ok(
                notificationService.getUnread(userId)
                        .stream()
                        .map(NotificationResponse::from)
                        .toList()
        );
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@CurrentUserId Long userId, @PathVariable Long id) {
        notificationService.markAsRead(userId, id);
        return ResponseEntity.noContent().build();
    }
}

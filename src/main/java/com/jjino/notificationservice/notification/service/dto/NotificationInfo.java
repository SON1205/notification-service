package com.jjino.notificationservice.notification.service.dto;

import com.jjino.notificationservice.notification.domain.Notification;
import com.jjino.notificationservice.notification.domain.NotificationType;

import java.time.LocalDateTime;

public record NotificationInfo(
        Long id,
        NotificationType type,
        String title,
        String content,
        boolean isRead,
        LocalDateTime createdAt
) {
    public static NotificationInfo from(Notification notification) {
        return new NotificationInfo(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getContent(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}

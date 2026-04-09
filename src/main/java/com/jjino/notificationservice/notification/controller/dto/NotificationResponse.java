package com.jjino.notificationservice.notification.controller.dto;

import com.jjino.notificationservice.notification.domain.NotificationType;
import com.jjino.notificationservice.notification.service.dto.NotificationInfo;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String content,
        boolean isRead,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(NotificationInfo info) {
        return new NotificationResponse(
                info.id(),
                info.type(),
                info.title(),
                info.content(),
                info.isRead(),
                info.createdAt()
        );
    }
}

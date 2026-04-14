package com.jjino.notificationservice.notification.service.dto;

import com.jjino.notificationservice.notification.domain.NotificationType;

public record CreateNotificationCommand(Long userId, NotificationType type, String title, String content) {
}

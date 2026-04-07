package com.jjino.notificationservice.notification.controller.dto;

import com.jjino.notificationservice.notification.domain.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateNotificationRequest(
        @NotNull(message = "userId is required")
        Long userId,

        @NotNull(message = "type is required")
        NotificationType type,

        @NotBlank(message = "title is required")
        String title,

        @NotBlank(message = "content is required")
        String content
) {
}

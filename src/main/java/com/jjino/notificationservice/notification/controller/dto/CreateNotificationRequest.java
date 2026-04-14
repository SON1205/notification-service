package com.jjino.notificationservice.notification.controller.dto;

import com.jjino.notificationservice.notification.domain.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateNotificationRequest(
        @NotNull(message = "userId is required")
        Long userId,

        @NotNull(message = "type is required")
        NotificationType type,

        @NotBlank(message = "title is required")
        @Size(max = 100, message = "title must be 100 characters or less")
        String title,

        @NotBlank(message = "content is required")
        @Size(max = 500, message = "content must be 500 characters or less")
        String content
) {
}

package com.jjino.notificationservice.notification.service.event;

import com.jjino.notificationservice.notification.service.dto.NotificationInfo;

public record NotificationSentEvent(
        Long userId,
        NotificationInfo info
) {
}

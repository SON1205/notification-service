package com.jjino.notificationservice.notification.service;

import static com.jjino.notificationservice.global.error.ErrorCode.NOTIFICATION_NOT_FOUND;

import com.jjino.notificationservice.global.error.BusinessException;
import com.jjino.notificationservice.notification.domain.Notification;
import com.jjino.notificationservice.notification.repository.NotificationRepository;
import com.jjino.notificationservice.notification.service.dto.CreateNotificationCommand;
import com.jjino.notificationservice.notification.service.dto.NotificationInfo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SseEmitterService sseEmitterService;

    @Transactional
    public NotificationInfo send(CreateNotificationCommand command) {
        Notification notification = Notification.builder()
                .userId(command.userId())
                .type(command.type())
                .title(command.title())
                .content(command.content())
                .build();

        Notification saved = notificationRepository.save(notification);

        NotificationInfo info = NotificationInfo.from(saved);
        sseEmitterService.send(command.userId(), info);
        return info;
    }

    @Transactional(readOnly = true)
    public List<NotificationInfo> getUnread(Long userId) {
        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationInfo::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationInfo> getAll(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationInfo::from)
                .toList();
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(n -> n.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(NOTIFICATION_NOT_FOUND));

        notification.markAsRead();
    }
}

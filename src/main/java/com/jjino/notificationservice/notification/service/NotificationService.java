package com.jjino.notificationservice.notification.service;

import static com.jjino.notificationservice.global.error.ErrorCode.NOTIFICATION_NOT_FOUND;

import com.jjino.notificationservice.global.error.BusinessException;
import com.jjino.notificationservice.notification.domain.Notification;
import com.jjino.notificationservice.notification.repository.NotificationRepository;
import com.jjino.notificationservice.notification.service.dto.CreateNotificationCommand;
import com.jjino.notificationservice.notification.service.dto.NotificationInfo;
import com.jjino.notificationservice.notification.service.event.NotificationSentEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 알림을 저장하고, 트랜잭션 커밋 후 SSE 전송을 트리거한다.
     *
     * [기존 방식] TransactionTemplate으로 DB 저장만 감싸고 SSE를 직접 호출
     *   → "커밋 후 전송"이라는 의도가 코드 구조에 드러나지 않고,
     *     선언적 @Transactional과 프로그래매틱 TransactionTemplate이 혼용되어 일관성 저하
     *
     * [현재 방식] @Transactional + ApplicationEventPublisher + @TransactionalEventListener(AFTER_COMMIT)
     *   → DB 커밋이 확정된 후에만 SSE 전송이 실행됨이 구조적으로 보장됨
     *   → 서비스는 "저장 + 이벤트 발행"이라는 도메인 책임만 가지고,
     *     SSE 전송은 NotificationEventListener에서 처리 (관심사 분리)
     *   → 추후 WebSocket, FCM 등 전송 채널 추가 시 리스너만 확장하면 됨
     */
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

        // 트랜잭션 커밋 후 SSE 전송을 위한 이벤트 발행
        eventPublisher.publishEvent(new NotificationSentEvent(command.userId(), info));

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

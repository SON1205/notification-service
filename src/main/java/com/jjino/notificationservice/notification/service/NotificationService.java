package com.jjino.notificationservice.notification.service;

import static com.jjino.notificationservice.global.error.ErrorCode.NOTIFICATION_NOT_FOUND;

import com.jjino.notificationservice.global.error.BusinessException;
import com.jjino.notificationservice.notification.domain.Notification;
import com.jjino.notificationservice.notification.repository.NotificationRepository;
import com.jjino.notificationservice.notification.service.dto.CreateNotificationCommand;
import com.jjino.notificationservice.notification.service.dto.NotificationInfo;
import com.jjino.notificationservice.notification.service.event.NotificationSentEvent;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 알림을 저장하고, 트랜잭션 커밋 후 SSE 전송을 트리거한다.
     * <p>
     * [기존 방식]
     * <br> TransactionTemplate으로 DB 저장만 감싸고 SSE를 직접 호출 <br> → "커밋 후 전송"이라는 의도가 코드 구조에 드러나지 않고, 선언적 @Transactional과
     * 프로그래매틱 TransactionTemplate이 혼용되어 일관성 저하
     * <p>
     * [현재 방식]
     * <br> @Transactional + ApplicationEventPublisher + @TransactionalEventListener(AFTER_COMMIT) <br> → DB 커밋이 확정된
     * 후에만 SSE 전송이 실행됨이 구조적으로 보장됨 <br> → 서비스는 "저장 + 이벤트 발행"이라는 도메인 책임만 가지고, SSE 전송은 NotificationEventListener에서 처리 (관심사
     * 분리) <br> → 추후 WebSocket, FCM 등 전송 채널 추가 시 리스너만 확장하면 됨
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
        return notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId)
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

    // Last-Event-ID는 브라우저 EventSource가 재연결 시 자동 전송하는 SSE 스펙 핵심 계약.
    // 숫자가 아닌 값(프록시 버그, 임의 요청 등)이면 무시하고 신규 연결로 처리.
    @Transactional(readOnly = true)
    public List<NotificationInfo> getMissedAfter(Long userId, String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return Collections.emptyList();
        }

        try {
            return notificationRepository.findByUserIdAndIdGreaterThanOrderByIdAsc(userId, Long.parseLong(lastEventId))
                    .stream()
                    .map(NotificationInfo::from)
                    .toList();
        } catch (NumberFormatException e) {
            log.warn("Invalid Last-Event-ID: {}", lastEventId);
            return Collections.emptyList();
        }
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(n -> n.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(NOTIFICATION_NOT_FOUND));

        notification.markAsRead();
    }
}

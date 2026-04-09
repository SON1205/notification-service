package com.jjino.notificationservice.notification.service.event;

import com.jjino.notificationservice.notification.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림 도메인 이벤트 리스너.
 *
 * AFTER_COMMIT 단계에서 실행되므로 DB 커밋이 확정된 이후에만 SSE 전송이 수행된다.
 * - DB 커넥션을 점유하지 않음 (트랜잭션 이미 종료)
 * - SSE 전송 실패가 트랜잭션 롤백을 유발하지 않음
 * - 추후 전송 채널 추가(WebSocket, FCM 등) 시 리스너만 확장하면 됨
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final SseEmitterService sseEmitterService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationSent(NotificationSentEvent event) {
        sseEmitterService.send(event.userId(), event.info());
    }
}

package com.jjino.notificationservice.notification.service.event;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import com.jjino.notificationservice.notification.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림 도메인 이벤트 리스너.
 * <p>
 * AFTER_COMMIT 단계에서 실행되므로 DB 커밋이 확정된 이후에만 SSE 전송이 수행된다. <br> - DB 커넥션을 점유하지 않음 (트랜잭션 이미 종료) <br> - SSE 전송 실패가 트랜잭션 롤백을
 * 유발하지 않음 <br> - 추후 전송 채널 추가(WebSocket, FCM 등) 시 리스너만 확장하면 됨
 * <p>
 * 현재 같은 스레드에서 실행되므로 SSE 전송 시간이 API 응답에 포함됨. <br> Phase 2 부하 테스트 후 병목 확인 시 @Async 도입 검토.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final SseEmitterService sseEmitterService;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handleNotificationSent(NotificationSentEvent event) {
        sseEmitterService.send(event.userId(), event.info());
    }
}

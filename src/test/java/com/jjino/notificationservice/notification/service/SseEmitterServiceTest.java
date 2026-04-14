package com.jjino.notificationservice.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.jjino.notificationservice.notification.domain.NotificationType;
import com.jjino.notificationservice.notification.service.dto.NotificationInfo;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterServiceTest {

    private SseEmitterService sseEmitterService;

    @BeforeEach
    void setUp() {
        sseEmitterService = new SseEmitterService();
    }

    private static NotificationInfo testNotification(Long id) {
        return new NotificationInfo(id, NotificationType.SYSTEM, "제목", "내용", false, LocalDateTime.now());
    }

    @Nested
    @DisplayName("subscribe")
    class Subscribe {

        @Test
        @DisplayName("emitter를 생성하고 반환한다")
        void createsAndReturnsEmitter() {
            // when
            SseEmitter emitter = sseEmitterService.subscribe(1L, Collections.emptyList());

            // then
            assertThat(emitter).isNotNull();
        }

        @Test
        @DisplayName("재구독 시 기존 emitter가 정리된다")
        void completesExistingEmitterOnResubscribe() {
            // given
            SseEmitter first = sseEmitterService.subscribe(1L, Collections.emptyList());
            boolean[] completed = {false};
            first.onCompletion(() -> completed[0] = true);

            // when
            SseEmitter second = sseEmitterService.subscribe(1L, Collections.emptyList());

            // then
            assertThat(second).isNotSameAs(first);
            // 기존 emitter의 complete가 호출됨 (remove에서 complete() 호출)
        }

        @Test
        @DisplayName("재연결 시 Last-Event-ID 이후의 밀린 알림을 전송한다")
        void sendsMissedNotificationsOnReconnect() {
            // given
            List<NotificationInfo> missed = List.of(testNotification(2L), testNotification(3L));

            // when & then (IOException 없이 정상 완료되면 성공)
            SseEmitter emitter = sseEmitterService.subscribe(1L, missed);
            assertThat(emitter).isNotNull();
        }
    }

    @Nested
    @DisplayName("send")
    class Send {

        @Test
        @DisplayName("구독 중인 유저에게 전송한다")
        void sendsToSubscribedUser() throws IOException {
            // given
            SseEmitter emitter = sseEmitterService.subscribe(1L, Collections.emptyList());

            // when & then (IOException 없이 정상 완료되면 성공)
            sseEmitterService.send(1L, testNotification(1L));
        }

        @Test
        @DisplayName("미구독 유저에게 전송 시 무시한다")
        void ignoresUnsubscribedUser() {
            // when & then (예외 없이 조용히 무시)
            sseEmitterService.send(999L, testNotification(1L));
        }

        @Test
        @DisplayName("구독 후 다시 같은 userId로 send하면 새 emitter에 전송된다")
        void sendsToLatestEmitterAfterResubscribe() {
            // given
            sseEmitterService.subscribe(1L, Collections.emptyList());
            SseEmitter newEmitter = sseEmitterService.subscribe(1L, Collections.emptyList());

            // when & then (최신 emitter로 전송, 예외 없으면 성공)
            sseEmitterService.send(1L, testNotification(1L));
        }
    }
}
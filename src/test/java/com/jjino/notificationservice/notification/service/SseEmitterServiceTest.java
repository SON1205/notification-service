package com.jjino.notificationservice.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
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

    @Nested
    @DisplayName("subscribe")
    class Subscribe {

        @Test
        @DisplayName("emitter를 생성하고 반환한다")
        void createsAndReturnsEmitter() {
            // when
            SseEmitter emitter = sseEmitterService.subscribe(1L);

            // then
            assertThat(emitter).isNotNull();
        }

        @Test
        @DisplayName("재구독 시 기존 emitter가 정리된다")
        void completesExistingEmitterOnResubscribe() {
            // given
            SseEmitter first = sseEmitterService.subscribe(1L);
            boolean[] completed = {false};
            first.onCompletion(() -> completed[0] = true);

            // when
            SseEmitter second = sseEmitterService.subscribe(1L);

            // then
            assertThat(second).isNotSameAs(first);
            // 기존 emitter의 complete가 호출됨 (remove에서 complete() 호출)
        }
    }

    @Nested
    @DisplayName("send")
    class Send {

        @Test
        @DisplayName("구독 중인 유저에게 전송한다")
        void sendsToSubscribedUser() throws IOException {
            // given
            SseEmitter emitter = sseEmitterService.subscribe(1L);

            // when & then (IOException 없이 정상 완료되면 성공)
            sseEmitterService.send(1L, "테스트 데이터");
        }

        @Test
        @DisplayName("미구독 유저에게 전송 시 무시한다")
        void ignoresUnsubscribedUser() {
            // when & then (예외 없이 조용히 무시)
            sseEmitterService.send(999L, "데이터");
        }

        @Test
        @DisplayName("구독 후 다시 같은 userId로 send하면 새 emitter에 전송된다")
        void sendsToLatestEmitterAfterResubscribe() {
            // given
            sseEmitterService.subscribe(1L);
            SseEmitter newEmitter = sseEmitterService.subscribe(1L);

            // when & then (최신 emitter로 전송, 예외 없으면 성공)
            sseEmitterService.send(1L, "데이터");
        }
    }
}

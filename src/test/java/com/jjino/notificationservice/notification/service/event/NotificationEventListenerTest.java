package com.jjino.notificationservice.notification.service.event;

import static org.mockito.BDDMockito.then;

import com.jjino.notificationservice.notification.domain.NotificationType;
import com.jjino.notificationservice.notification.service.SseEmitterService;
import com.jjino.notificationservice.notification.service.dto.NotificationInfo;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @InjectMocks
    private NotificationEventListener listener;

    @Mock
    private SseEmitterService sseEmitterService;

    @Test
    @DisplayName("이벤트 수신 시 SseEmitterService.send를 올바른 인자로 호출한다")
    void delegatesToSseEmitterService() {
        // given
        Long userId = 1L;
        NotificationInfo info = new NotificationInfo(
                1L, NotificationType.SYSTEM, "제목", "내용", false, LocalDateTime.now()
        );
        NotificationSentEvent event = new NotificationSentEvent(userId, info);

        // when
        listener.handleNotificationSent(event);

        // then
        then(sseEmitterService).should().send(userId, info);
    }
}

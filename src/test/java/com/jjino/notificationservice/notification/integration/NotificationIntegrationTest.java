package com.jjino.notificationservice.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.jjino.notificationservice.notification.domain.Notification;
import com.jjino.notificationservice.notification.domain.NotificationType;
import com.jjino.notificationservice.notification.repository.NotificationRepository;
import com.jjino.notificationservice.notification.service.NotificationService;
import com.jjino.notificationservice.notification.service.SseEmitterService;
import com.jjino.notificationservice.notification.service.dto.CreateNotificationCommand;
import com.jjino.notificationservice.notification.service.dto.NotificationInfo;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 통합테스트: 실제 트랜잭션 + DB + 이벤트 리스너가 함께 동작하는지 검증.
 *
 * 주의: 테스트 메서드에 @Transactional을 붙이지 않는다.
 * @Transactional이 있으면 테스트 종료 시점까지 커밋이 미뤄져
 * @TransactionalEventListener(AFTER_COMMIT)이 실행되지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoSpyBean
    private SseEmitterService sseEmitterService;

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
    }

    @Nested
    @DisplayName("send 통합 흐름")
    class SendIntegration {

        @Test
        @DisplayName("알림 저장 후 AFTER_COMMIT 시점에 SSE send가 호출된다")
        void triggersAfterCommitSseSend() {
            // given
            CreateNotificationCommand command = new CreateNotificationCommand(
                    1L, NotificationType.SYSTEM, "통합 테스트", "커밋 후 전송 검증"
            );

            // when
            NotificationInfo result = notificationService.send(command);

            // then
            // DB에 저장되었는지
            List<Notification> saved = notificationRepository.findByUserIdOrderByCreatedAtDesc(1L);
            assertThat(saved).hasSize(1);
            assertThat(saved.getFirst().getTitle()).isEqualTo("통합 테스트");

            // AFTER_COMMIT 후 SSE send가 호출되었는지
            then(sseEmitterService).should().send(eq(1L), any(NotificationInfo.class));
        }

        @Test
        @DisplayName("send 결과의 info가 DB 데이터와 일치한다")
        void returnedInfoMatchesDbData() {
            // given
            CreateNotificationCommand command = new CreateNotificationCommand(
                    1L, NotificationType.SYSTEM, "제목", "내용"
            );

            // when
            NotificationInfo result = notificationService.send(command);

            // then
            Notification fromDb = notificationRepository.findById(result.id()).orElseThrow();
            assertThat(result.title()).isEqualTo(fromDb.getTitle());
            assertThat(result.content()).isEqualTo(fromDb.getContent());
            assertThat(result.isRead()).isFalse();
        }
    }

    @Nested
    @DisplayName("조회/읽음 처리 통합 흐름")
    class ReadIntegration {

        @Test
        @DisplayName("getUnread는 읽지 않은 알림만 반환한다")
        void getUnreadReturnsOnlyUnread() {
            // given
            notificationService.send(new CreateNotificationCommand(
                    1L, NotificationType.SYSTEM, "알림1", "내용1"));
            NotificationInfo read = notificationService.send(new CreateNotificationCommand(
                    1L, NotificationType.SYSTEM, "알림2", "내용2"));
            notificationService.markAsRead(1L, read.id());

            // when
            List<NotificationInfo> unread = notificationService.getUnread(1L);

            // then
            assertThat(unread).hasSize(1);
            assertThat(unread.getFirst().title()).isEqualTo("알림1");
        }

        @Test
        @DisplayName("markAsRead 후 해당 알림이 unread 목록에서 제외된다")
        void markAsReadRemovesFromUnreadList() {
            // given
            NotificationInfo info = notificationService.send(new CreateNotificationCommand(
                    1L, NotificationType.SYSTEM, "제목", "내용"));

            // when
            notificationService.markAsRead(1L, info.id());

            // then
            List<NotificationInfo> unread = notificationService.getUnread(1L);
            assertThat(unread).isEmpty();

            List<NotificationInfo> all = notificationService.getAll(1L);
            assertThat(all).hasSize(1);
            assertThat(all.getFirst().isRead()).isTrue();
        }
    }
}

package com.jjino.notificationservice.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.assertj.core.api.InstanceOfAssertFactories;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.jjino.notificationservice.global.error.BusinessException;
import com.jjino.notificationservice.global.error.ErrorCode;
import com.jjino.notificationservice.notification.domain.Notification;
import com.jjino.notificationservice.notification.domain.NotificationType;
import com.jjino.notificationservice.notification.repository.NotificationRepository;
import com.jjino.notificationservice.notification.service.dto.CreateNotificationCommand;
import com.jjino.notificationservice.notification.service.dto.NotificationInfo;
import com.jjino.notificationservice.notification.service.event.NotificationSentEvent;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @InjectMocks
    private NotificationService notificationService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private Notification createNotification(Long id, Long userId, boolean isRead) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(NotificationType.SYSTEM)
                .title("테스트 알림")
                .content("테스트 내용")
                .build();
        ReflectionTestUtils.setField(notification, "id", id);
        if (isRead) {
            notification.markAsRead();
        }
        return notification;
    }

    @Nested
    @DisplayName("send")
    class Send {

        @Test
        @DisplayName("알림을 저장하고 이벤트를 발행한다")
        void savesNotificationAndPublishesEvent() {
            // given
            CreateNotificationCommand command = new CreateNotificationCommand(
                    1L, NotificationType.SYSTEM, "제목", "내용"
            );
            Notification saved = createNotification(1L, 1L, false);
            given(notificationRepository.save(any(Notification.class))).willReturn(saved);

            // when
            NotificationInfo result = notificationService.send(command);

            // then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.type()).isEqualTo(NotificationType.SYSTEM);
            then(notificationRepository).should().save(any(Notification.class));
        }

        @Test
        @DisplayName("command의 필드가 올바르게 Notification으로 매핑되어 저장된다")
        void mapsCommandFieldsToNotificationCorrectly() {
            // given
            CreateNotificationCommand command = new CreateNotificationCommand(
                    1L, NotificationType.SYSTEM, "제목", "내용"
            );
            Notification saved = createNotification(1L, 1L, false);
            given(notificationRepository.save(any(Notification.class))).willReturn(saved);

            ArgumentCaptor<Notification> notificationCaptor =
                    ArgumentCaptor.forClass(Notification.class);

            // when
            notificationService.send(command);

            // then
            then(notificationRepository).should().save(notificationCaptor.capture());
            Notification captured = notificationCaptor.getValue();
            assertThat(captured.getUserId()).isEqualTo(1L);
            assertThat(captured.getType()).isEqualTo(NotificationType.SYSTEM);
            assertThat(captured.getTitle()).isEqualTo("제목");
            assertThat(captured.getContent()).isEqualTo("내용");
            assertThat(captured.isRead()).isFalse();
        }

        @Test
        @DisplayName("발행된 이벤트에 올바른 userId와 info가 포함된다")
        void publishedEventContainsCorrectData() {
            // given
            CreateNotificationCommand command = new CreateNotificationCommand(
                    1L, NotificationType.SYSTEM, "제목", "내용"
            );
            Notification saved = createNotification(1L, 1L, false);
            given(notificationRepository.save(any(Notification.class))).willReturn(saved);

            ArgumentCaptor<NotificationSentEvent> eventCaptor =
                    ArgumentCaptor.forClass(NotificationSentEvent.class);

            // when
            notificationService.send(command);

            // then
            then(eventPublisher).should().publishEvent(eventCaptor.capture());
            NotificationSentEvent event = eventCaptor.getValue();
            assertThat(event.userId()).isEqualTo(1L);
            assertThat(event.info().id()).isEqualTo(1L);
            assertThat(event.info().title()).isEqualTo("테스트 알림");
        }

        @Test
        @DisplayName("저장 실패 시 이벤트를 발행하지 않는다")
        void doesNotPublishEventWhenSaveFails() {
            // given
            CreateNotificationCommand command = new CreateNotificationCommand(
                    1L, NotificationType.SYSTEM, "제목", "내용"
            );
            given(notificationRepository.save(any(Notification.class)))
                    .willThrow(new RuntimeException("DB error"));

            // when & then
            assertThatThrownBy(() -> notificationService.send(command))
                    .isInstanceOf(RuntimeException.class);
            then(eventPublisher).should(never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("getUnread")
    class GetUnread {

        @Test
        @DisplayName("안 읽은 알림만 반환한다")
        void returnsOnlyUnreadNotifications() {
            // given
            Long userId = 1L;
            List<Notification> unread = List.of(
                    createNotification(1L, userId, false),
                    createNotification(2L, userId, false)
            );
            given(notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId))
                    .willReturn(unread);

            // when
            List<NotificationInfo> result = notificationService.getUnread(userId);

            // then
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(info -> !info.isRead());
        }

        @Test
        @DisplayName("안 읽은 알림이 없으면 빈 리스트를 반환한다")
        void returnsEmptyListWhenNoUnread() {
            // given
            Long userId = 1L;
            given(notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId))
                    .willReturn(List.of());

            // when
            List<NotificationInfo> result = notificationService.getUnread(userId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAll")
    class GetAll {

        @Test
        @DisplayName("전체 알림을 반환한다")
        void returnsAllNotifications() {
            // given
            Long userId = 1L;
            List<Notification> all = List.of(
                    createNotification(1L, userId, false),
                    createNotification(2L, userId, true)
            );
            given(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                    .willReturn(all);

            // when
            List<NotificationInfo> result = notificationService.getAll(userId);

            // then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("알림이 없으면 빈 리스트를 반환한다")
        void returnsEmptyListWhenNoNotifications() {
            // given
            Long userId = 1L;
            given(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                    .willReturn(List.of());

            // when
            List<NotificationInfo> result = notificationService.getAll(userId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("markAsRead")
    class MarkAsRead {

        @Test
        @DisplayName("본인 알림을 읽음 처리한다")
        void marksOwnNotificationAsRead() {
            // given
            Long userId = 1L;
            Long notificationId = 1L;
            Notification notification = createNotification(notificationId, userId, false);
            given(notificationRepository.findById(notificationId))
                    .willReturn(Optional.of(notification));

            // when
            notificationService.markAsRead(userId, notificationId);

            // then
            assertThat(notification.isRead()).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 알림이면 BusinessException을 던진다")
        void throwsExceptionWhenNotificationNotFound() {
            // given
            Long userId = 1L;
            Long notificationId = 999L;
            given(notificationRepository.findById(notificationId))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> notificationService.markAsRead(userId, notificationId))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::getErrorCode)
                    .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
        }

        @Test
        @DisplayName("타인의 알림이면 BusinessException을 던진다")
        void throwsExceptionWhenNotOwnNotification() {
            // given
            Long userId = 1L;
            Long otherUserId = 2L;
            Long notificationId = 1L;
            Notification notification = createNotification(notificationId, otherUserId, false);
            given(notificationRepository.findById(notificationId))
                    .willReturn(Optional.of(notification));

            // when & then
            assertThatThrownBy(() -> notificationService.markAsRead(userId, notificationId))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::getErrorCode)
                    .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
        }

        @Test
        @DisplayName("이미 읽은 알림에 대해 markAsRead를 호출해도 정상 종료된다")
        void idempotentMarkAsRead() {
            // given
            Long userId = 1L;
            Long notificationId = 1L;
            Notification notification = createNotification(notificationId, userId, true);
            given(notificationRepository.findById(notificationId))
                    .willReturn(Optional.of(notification));

            // when
            notificationService.markAsRead(userId, notificationId);

            // then
            assertThat(notification.isRead()).isTrue();
        }

        @Test
        @DisplayName("타인의 알림 접근 시 읽음 상태가 변경되지 않는다")
        void doesNotChangeReadStateForOtherUserNotification() {
            // given
            Long userId = 1L;
            Long otherUserId = 2L;
            Long notificationId = 1L;
            Notification notification = createNotification(notificationId, otherUserId, false);
            given(notificationRepository.findById(notificationId))
                    .willReturn(Optional.of(notification));

            // when & then
            assertThatThrownBy(() -> notificationService.markAsRead(userId, notificationId))
                    .isInstanceOf(BusinessException.class);
            assertThat(notification.isRead()).isFalse();
        }
    }
}

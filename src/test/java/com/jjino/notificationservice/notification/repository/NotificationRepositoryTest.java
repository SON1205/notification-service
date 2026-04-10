package com.jjino.notificationservice.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.jjino.notificationservice.notification.domain.Notification;
import com.jjino.notificationservice.notification.domain.NotificationType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    private Long userId = 1L;
    private Long otherUserId = 2L;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();

        Notification read = Notification.builder()
                .userId(userId).type(NotificationType.SYSTEM)
                .title("읽은 알림").content("내용1").build();
        read.markAsRead();

        Notification unread1 = Notification.builder()
                .userId(userId).type(NotificationType.SYSTEM)
                .title("안읽은 알림1").content("내용2").build();

        Notification unread2 = Notification.builder()
                .userId(userId).type(NotificationType.SYSTEM)
                .title("안읽은 알림2").content("내용3").build();

        Notification otherUserNotification = Notification.builder()
                .userId(otherUserId).type(NotificationType.SYSTEM)
                .title("다른 유저 알림").content("내용4").build();

        notificationRepository.saveAll(List.of(read, unread1, unread2, otherUserNotification));
    }

    @Test
    @DisplayName("특정 유저의 안 읽은 알림만 최신순으로 조회한다")
    void findUnreadByUserIdOrderByCreatedAtDesc() {
        // when
        List<Notification> result = notificationRepository
                .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(n -> !n.isRead());
        assertThat(result).allMatch(n -> n.getUserId().equals(userId));
    }

    @Test
    @DisplayName("특정 유저의 전체 알림을 최신순으로 조회한다")
    void findAllByUserIdOrderByCreatedAtDesc() {
        // when
        List<Notification> result = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId);

        // then
        assertThat(result).hasSize(3);
        assertThat(result).allMatch(n -> n.getUserId().equals(userId));
    }

    @Test
    @DisplayName("다른 유저의 알림은 포함되지 않는다")
    void doesNotIncludeOtherUserNotifications() {
        // when
        List<Notification> result = notificationRepository
                .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);

        // then
        assertThat(result).noneMatch(n -> n.getUserId().equals(otherUserId));
    }

    @Test
    @DisplayName("알림이 없는 유저의 조회는 빈 리스트를 반환한다")
    void returnsEmptyListForUserWithNoNotifications() {
        // when
        List<Notification> result = notificationRepository
                .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(999L);

        // then
        assertThat(result).isEmpty();
    }
}

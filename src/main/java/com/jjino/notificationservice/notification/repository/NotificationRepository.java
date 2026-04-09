package com.jjino.notificationservice.notification.repository;

import com.jjino.notificationservice.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId);

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
}

package com.jjino.notificationservice.notification.domain;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import com.jjino.notificationservice.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    // FK가 아닌 일반 Long. 알림 서비스는 User 엔티티에 의존하지 않는다.
    // 서비스 분리(MSA) 시 깨지지 않도록 의도적으로 연관관계를 맺지 않음.
    // userId 유효성 검증이 필요하면 서비스 레이어에서 처리.
    @Column(nullable = false)
    private Long userId;

    @Enumerated(STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Builder
    public Notification(Long userId, NotificationType type, String title, String content) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.read = false;
    }

    public void markAsRead() {
        this.read = true;
    }
}

package com.jjino.notificationservice.notification.service;

import com.jjino.notificationservice.notification.service.dto.NotificationInfo;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE Emitter 관리 서비스.
 *
 * [동시성 시나리오 및 대응 — Phase 1]
 *
 * 1. subscribe~send 갭 (알림 유실)
 *    - remove() → put() 사이에 send()가 들어오면 emitter가 없어 알림 전송 실패.
 *    - 대응: DB에 저장은 완료된 상태이므로, Last-Event-ID 재연결 시 복구됨.
 *
 * 2. send 중 타임아웃 (complete 중복 호출)
 *    - get()으로 emitter를 꺼낸 뒤 send() 전에 Tomcat이 타임아웃 처리.
 *    - 이미 완료된 emitter에 complete()를 다시 호출하면 IllegalStateException.
 *    - 대응: completeQuietly()로 중복 complete 방어.
 *
 * 3. 동시 재구독 경합 (알림 유실)
 *    - 같은 userId로 subscribe()가 동시 호출되면 remove~put 사이 윈도우에서 유실 가능.
 *    - 대응: 시나리오 1과 동일하게 Last-Event-ID로 복구. Phase 3에서 구조적 해소.
 *
 * 근본 원인: ConcurrentHashMap은 개별 연산은 thread-safe하지만 복합 연산(remove→put)은 원자적이지 않음.
 * Phase 3 Redis Pub/Sub 전환 시 구조적으로 해소 예정.
 */
@Slf4j
@Service
public class SseEmitterService {

    private static final Long EMITTER_TIMEOUT = 60 * 1_000L;
    // Phase 1: 유저당 1 Emitter. 같은 유저가 멀티탭 열면 기존 연결 끊김.
    // 프론트에서 SharedWorker/BroadcastChannel로 탭 간 SSE 공유 필요.
    // Phase 3 스케일아웃 시 Redis Pub/Sub 등으로 교체 예정이므로 현 단계에서는 1:1 유지.
    private final ConcurrentHashMap<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    // https://ko.htmlspecs.com/#the-messageevent-interface
    // SSE 스펙 핵심 계약: 재연결 시 Last-Event-ID 이후의 밀린 알림을 전송하여 유실 방지.
    // 브라우저 EventSource가 재연결 시 Last-Event-ID 헤더를 자동 전송하므로, 서버는 id만 붙이고 헤더만 읽으면 된다.
    public SseEmitter subscribe(Long userId, List<NotificationInfo> missedNotifications) {
        // remove → new → put 사이가 원자적이지 않아 동시 subscribe 시 좀비 emitter 가능.
        // 단, CAS remove(userId, emitter)로 다른 emitter를 오삭제하지 않고, timeout(60초)으로 자동 정리.
        // Phase 3 Redis Pub/Sub 전환 시 구조적으로 해소 예정.
        remove(userId);

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT);
        emitters.put(userId, emitter);

        emitter.onCompletion(() -> emitters.remove(userId, emitter));
        emitter.onTimeout(() -> emitters.remove(userId, emitter));
        emitter.onError(e -> emitters.remove(userId, emitter));

        // 관례: 일부 브라우저/프록시는 첫 데이터가 없으면 연결을 끊어버리는 경우도 있어서, 더미 이벤트를 보내서 연결을 확정
        sendToEmitter(emitter, "connect", "connected", null);

        // 재연결인 경우, 밀린 알림을 순서대로 전송
        for (NotificationInfo notification : missedNotifications) {
            sendToEmitter(emitter, "notification", notification, String.valueOf(notification.id()));
        }

        return emitter;
    }

    public void send(Long userId, NotificationInfo data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            sendToEmitter(emitter, "notification", data, String.valueOf(data.id()));
        }
        // else: 유저 오프라인 — Phase 4에서 FCM fallback 추가 지점
    }

    // send() 시점에 emitter가 이미 타임아웃/완료 상태일 수 있다 (get과 send 사이에 상태 변경).
    // IOException 발생 시 complete()를 호출하는데, 이미 완료된 emitter에 중복 호출하면
    // IllegalStateException이 발생할 수 있으므로 방어적으로 처리.
    private void sendToEmitter(SseEmitter emitter, String eventName, Object data, String id) {
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .name(eventName)
                    .data(data);
            if (id != null) {
                event.id(id);
            }
            emitter.send(event);
        } catch (IOException e) {
            log.warn("SSE 전송 실패: {}", e.getMessage());
            completeQuietly(emitter);
        }
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException e) {
            // 이미 완료된 emitter — 무시
        }
    }

    private void remove(Long userId) {
        SseEmitter existing = emitters.remove(userId);
        if (existing != null) {
            completeQuietly(existing);
        }
    }
}

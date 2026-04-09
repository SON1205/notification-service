package com.jjino.notificationservice.notification.service;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
public class SseEmitterService {

    private static final Long EMITTER_TIMEOUT = 60_000L;
    private final ConcurrentHashMap<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        remove(userId);

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT);
        emitters.put(userId, emitter);

        emitter.onCompletion(() -> emitters.remove(userId, emitter));
        emitter.onTimeout(() -> emitters.remove(userId, emitter));
        emitter.onError(e -> emitters.remove(userId, emitter));

        // 관례: 일부 브라우저/프록시는 첫 데이터가 없으면 연결을 끊어버리는 경우도 있어서, 더미 이벤트를 보내서 연결을 확정
        sendToEmitter(emitter, "connect", "connected");
        return emitter;
    }

    public void send(Long userId, Object data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            sendToEmitter(emitter, "notification", data);
        }
    }

    private void sendToEmitter(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
        } catch (IOException e) {
            log.warn("SSE 전송 실패: {}", e.getMessage());
            emitter.complete();
        }
    }

    private void remove(Long userId) {
        SseEmitter existing = emitters.remove(userId);
        if (existing != null) {
            existing.complete();
        }
    }
}

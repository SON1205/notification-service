package com.jjino.notificationservice.notification.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * SSE 관측 메트릭 등록/관리.
 * <p>
 * 메트릭 이름과 태그 등 "관측 정책"을 SseEmitterService 구현체와 분리한다. <br> Phase 3에서 WebFlux/Redis 기반으로 전환해도 이 클래스는 그대로 재사용 가능.
 * <p>
 * HTTP 지연, JVM, Tomcat, HikariCP 등 범용 메트릭은 Actuator가 자동 수집하므로, <br> 여기서는 SSE emitter 내부 상태처럼 직접 수집해야 하는 것만 등록한다.
 */
@Component
public class SseMetrics {

    // Counter: 누적 횟수. Prometheus가 증가율을 계산하여 "초당 N건" 그래프 생성.
    private final Counter subscribeCounter;
    private final Counter sendSuccessCounter;
    private final Counter sendFailCounter;
    private final Counter timeoutCounter;
    private final Counter reconnectCounter;

    public SseMetrics(MeterRegistry registry) {
        subscribeCounter = Counter.builder("sse.subscribe.total").register(registry);
        sendSuccessCounter = Counter.builder("sse.send.total").tag("result", "success").register(registry);
        sendFailCounter = Counter.builder("sse.send.total").tag("result", "fail").register(registry);
        timeoutCounter = Counter.builder("sse.timeout.total").register(registry);
        reconnectCounter = Counter.builder("sse.reconnect.total").register(registry);
    }

    /**
     * Gauge:
     * <p>
     * 현재 활성 연결 수. <br> emitter map 크기를 실시간 반영한다. <br> 상태(Map) 소유는 SseEmitterService, 관측 등록만 이 클래스가 담당.
     */
    public void bindActiveConnections(MeterRegistry registry, Map<?, ?> emitters) {
        registry.gaugeMapSize("sse.connections.active", List.of(), emitters);
    }

    public void recordSubscribe() {
        subscribeCounter.increment();
    }

    public void recordSendSuccess() {
        sendSuccessCounter.increment();
    }

    public void recordSendFail() {
        sendFailCounter.increment();
    }

    public void recordTimeout() {
        timeoutCounter.increment();
    }

    public void recordReconnect() {
        reconnectCounter.increment();
    }
}

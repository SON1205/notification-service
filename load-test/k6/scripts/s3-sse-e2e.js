/**
 * S3: SSE + 알림 동시 (end-to-end) 부하 테스트
 *
 * 목적: SSE 구독자가 존재하는 상태에서 알림 전송 시
 *       fan-out 비용, E2E 지연, 전송 실패율을 측정
 *
 * 동작 (두 시나리오 동시 실행):
 * - 구독자(subscribers): N명이 SSE 연결을 열고 유지 (per-vu-iterations)
 * - 발행자(publisher): 초당 M건 알림 전송 (ramping-arrival-rate)
 *
 * 구독자는 자신에게 온 알림을 수신할 때 수신 시각을 기록하고,
 * payload에 담긴 발송 시각과 비교하여 E2E 지연을 Trend 메트릭으로 기록한다.
 *
 * 관측 지표:
 * - sse_e2e_latency_ms: 발송~수신 지연 (커스텀 Trend)
 * - sse_received_events: 수신 이벤트 수
 * - sse_published_count: 발행 성공 수
 * - notification_send_failed: 발행 실패율
 * - delivery_rate: published 대비 received 비율 (handleSummary에서 출력)
 * - sse_send_total (서버): 전송 성공/실패
 * - HikariCP, Executor Threads, JVM Heap (Grafana)
 *
 * 실행: ./k6 run scripts/s3-sse-e2e.js
 */
import sse from 'k6/x/sse';
import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';
import { createUserAndGetToken, authHeaders } from '../utils/auth.js';
import {
    BASE_URL, API_PREFIX,
    E2E_SUBSCRIBER_COUNT as SUBSCRIBER_COUNT,
    E2E_SEND_RATE as SEND_RATE,
    E2E_PUBLISHER_START_DELAY_SEC as PUBLISHER_START_DELAY_SEC,
    E2E_RAMP_UP_SEC as RAMP_UP_SEC,
    E2E_PLATEAU_SEC as PLATEAU_SEC,
    E2E_RAMP_DOWN_SEC as RAMP_DOWN_SEC,
} from '../utils/config.js';

const e2eLatency = new Trend('sse_e2e_latency_ms', true);
const receivedEvents = new Counter('sse_received_events');
const publishedCount = new Counter('sse_published_count');
const sendFailed = new Rate('notification_send_failed');

export const options = {
    setupTimeout: `${Math.max(60, Math.ceil(SUBSCRIBER_COUNT / 5))}s`,
    scenarios: {
        // 구독자: 각 VU가 1회만 SSE 연결, 서버 timeout까지 유지
        // 발행 종료 후 SSE timeout까지 수신 여유 확보
        subscribers: {
            executor: 'per-vu-iterations',
            vus: SUBSCRIBER_COUNT,
            iterations: 1,
            maxDuration: '2m',
            exec: 'subscribe',
        },
        // 발행자: 초당 고정 TPS로 알림 전송
        // startTime을 두어 구독자 연결이 어느 정도 맺힌 후 시작
        publisher: {
            executor: 'ramping-arrival-rate',
            startTime: `${PUBLISHER_START_DELAY_SEC}s`,
            startRate: 5,
            timeUnit: '1s',
            preAllocatedVUs: 20,
            maxVUs: 50,
            stages: [
                { duration: `${RAMP_UP_SEC}s`, target: SEND_RATE },
                { duration: `${PLATEAU_SEC}s`, target: SEND_RATE },
                { duration: `${RAMP_DOWN_SEC}s`, target: 0 },
            ],
            exec: 'publish',
        },
    },
    thresholds: {
        'sse_e2e_latency_ms': ['p(95)<1000', 'p(99)<2000'],
        'notification_send_failed': ['rate<0.01'],
    },
};

export function setup() {
    // 각 구독자용 유저 + 발행자가 사용할 token + 대상 userId 목록
    const subscribers = [];

    for (let i = 0; i < SUBSCRIBER_COUNT; i++) {
        const user = createUserAndGetToken(i);
        subscribers.push(user);
    }

    return {
        subscribers,
        // 발행자는 subscribers[0]의 토큰으로 인증하여 전송 (권한 체크 없으므로 가능)
        publisherToken: subscribers[0].token,
    };
}

// ---- 구독자 ----
export function subscribe(data) {
    const vuIndex = __VU - 1;
    const user = data.subscribers[vuIndex];

    if (!user || !user.token) return;

    const url = `${BASE_URL}/api/v1/notifications/stream`;
    const params = {
        headers: { 'Authorization': `Bearer ${user.token}` },
    };

    sse.open(url, params, function (client) {
        client.on('event', function (event) {
            if (event.name !== 'notification') return;

            try {
                // event.data는 NotificationInfo JSON. content 필드에 { _e2e, sentAt, iter } JSON 담김
                const notification = JSON.parse(event.data);
                if (!notification.content || !notification.content.startsWith('{')) return;
                const contentPayload = JSON.parse(notification.content);
                // _e2e 마커로 S3가 보낸 알림만 측정 (이전 테스트 잔여물 제외)
                if (contentPayload._e2e && contentPayload.sentAt) {
                    const latency = Date.now() - contentPayload.sentAt;
                    e2eLatency.add(latency);
                    receivedEvents.add(1);
                }
            } catch (e) {
                // 알림이 아닌 이벤트(connect 등) 또는 파싱 실패는 무시
            }
        });

        client.on('error', function (e) {
            console.error(`SSE error VU ${__VU}: ${e.error()}`);
        });
    });
}

// ---- 발행자 ----
export function publish(data) {
    // 무작위 구독자에게 전송
    const randomIdx = Math.floor(Math.random() * data.subscribers.length);
    const targetUserId = data.subscribers[randomIdx].userId;

    const sentAt = Date.now();
    const payload = JSON.stringify({
        userId: targetUserId,
        type: 'SYSTEM',
        title: `E2E test ${sentAt}`,
        // content에 발송 시각 포함 → 수신 측에서 latency 계산
        content: JSON.stringify({ _e2e: true, sentAt: sentAt, iter: __ITER }),
    });

    const res = http.post(`${API_PREFIX}/notifications`, payload, {
        ...authHeaders(data.publisherToken),
        tags: { name: 'send_notification' },
    });

    const ok = check(res, {
        'send: status 201': (r) => r.status === 201,
    });

    if (ok) {
        publishedCount.add(1);
    }
    sendFailed.add(!ok);
}

// ---- delivery rate 출력 ----
// delivery_rate = 서버 저장 성공(201) 대비 클라이언트 SSE 수신율.
// 엄밀한 transport ack가 아닌, "발행 → DB 저장 → SSE push → 클라이언트 수신"까지의 end-to-end 전달률.
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

export function handleSummary(data) {
    const published = data.metrics.sse_published_count
        ? data.metrics.sse_published_count.values.count
        : 0;
    const received = data.metrics.sse_received_events
        ? data.metrics.sse_received_events.values.count
        : 0;
    const deliveryRate = published > 0
        ? ((received / published) * 100).toFixed(2)
        : 'N/A';

    const deliveryInfo = `\n===== Delivery Rate =====\n`
        + `Published: ${published}\n`
        + `Received:  ${received}\n`
        + `Rate:      ${deliveryRate}%\n`
        + `=========================\n`;

    return {
        stdout: textSummary(data, { indent: ' ', enableColors: true }) + deliveryInfo,
    };
}

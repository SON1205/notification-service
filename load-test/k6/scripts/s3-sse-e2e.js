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
 * - sse_e2e_latency: 발송~수신 지연 (커스텀 Trend)
 * - sse_received_events: 수신 이벤트 수
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
import { BASE_URL, API_PREFIX } from '../utils/config.js';

const e2eLatency = new Trend('sse_e2e_latency_ms', true);
const receivedEvents = new Counter('sse_received_events');
const sendFailed = new Rate('notification_send_failed');

const SUBSCRIBER_COUNT = 100;   // SSE 구독자 수
const SEND_RATE = 50;           // 초당 알림 전송 수 (plateau)

export const options = {
    setupTimeout: `${Math.max(60, Math.ceil(SUBSCRIBER_COUNT / 5))}s`,
    scenarios: {
        // 구독자: 각 VU가 1회만 SSE 연결, 서버 timeout까지 유지
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
            startTime: '10s',
            startRate: 5,
            timeUnit: '1s',
            preAllocatedVUs: 20,
            maxVUs: 50,
            stages: [
                { duration: '10s', target: SEND_RATE },
                { duration: '50s', target: SEND_RATE },   // plateau (서버 timeout 이내)
                { duration: '10s', target: 0 },
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
    sendFailed.add(!ok);
}

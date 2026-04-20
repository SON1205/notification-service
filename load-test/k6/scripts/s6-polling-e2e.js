/**
 * S6: Polling E2E 부하 테스트 (S3 SSE 대칭 구조)
 *
 * 목적: 1초 주기 polling 방식으로 알림 수신 시
 *       E2E 지연, 빈 응답 비율, 총 요청 수를 측정하여
 *       S3(SSE) 결과와 직접 비교
 *
 * 동작 (두 시나리오 동시 실행):
 * - 구독자(subscribers): N명이 1초마다 GET /notifications?afterId= 호출 (per-vu-iterations)
 * - 발행자(publisher): 초당 M건 알림 전송 (ramping-arrival-rate, S3와 동일)
 *
 * 구독자는 응답에서 새 알림의 content.sentAt과 현재 시각을 비교하여
 * E2E 지연을 Trend 메트릭으로 기록한다.
 *
 * 주의:
 * - publisher는 PUBLISHER_START_DELAY_SEC 후 시작. 구독자의 빈 응답 통계는 warm-up 구간을 제외하고 집계.
 * - 구독자는 publisher 종료 후 추가 1초 polling하여 마지막 알림 flush 보장.
 * - delivery_rate는 handleSummary()에서 published 대비 received 비율로 출력.
 *
 * 관측 지표:
 * - poll_e2e_latency_ms: 발송~수신 지연 (커스텀 Trend)
 * - poll_total_requests: 전체 polling 요청 수
 * - poll_empty_responses: 빈 응답 비율 (Rate, warm-up 제외)
 * - poll_empty_count / poll_non_empty_count: 빈/비빈 응답 횟수
 * - poll_received_events: 수신 알림 수
 * - poll_published_count: 발행 성공 수
 * - notification_send_failed: 발행 실패율
 * - delivery_rate: published 대비 received 비율 (handleSummary에서 출력)
 *
 * 실행: k6 run scripts/s6-polling-e2e.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';
import { createUserAndGetToken, authHeaders } from '../utils/auth.js';
import {
    API_PREFIX,
    E2E_SUBSCRIBER_COUNT as SUBSCRIBER_COUNT,
    E2E_SEND_RATE as SEND_RATE,
    E2E_PUBLISHER_START_DELAY_SEC as PUBLISHER_START_DELAY_SEC,
    E2E_RAMP_UP_SEC as RAMP_UP_SEC,
    E2E_PLATEAU_SEC as PLATEAU_SEC,
    E2E_RAMP_DOWN_SEC as RAMP_DOWN_SEC,
} from '../utils/config.js';

// ---- 커스텀 메트릭 ----
const e2eLatency = new Trend('poll_e2e_latency_ms', true);
const totalRequests = new Counter('poll_total_requests');
const emptyResponses = new Rate('poll_empty_responses');
const emptyCount = new Counter('poll_empty_count');
const nonEmptyCount = new Counter('poll_non_empty_count');
const receivedEvents = new Counter('poll_received_events');
const publishedCount = new Counter('poll_published_count');
const sendFailed = new Rate('notification_send_failed');

const PUBLISHER_TOTAL_DURATION_SEC = RAMP_UP_SEC + PLATEAU_SEC + RAMP_DOWN_SEC;
const FLUSH_GRACE_SEC = 1; // publisher 종료 후 마지막 poll 여유

export const options = {
    setupTimeout: `${Math.max(60, Math.ceil(SUBSCRIBER_COUNT / 5))}s`,
    scenarios: {
        // 구독자: 각 VU가 1회 진입 → 내부 while 루프로 1초 주기 polling
        subscribers: {
            executor: 'per-vu-iterations',
            vus: SUBSCRIBER_COUNT,
            iterations: 1,
            maxDuration: '2m',
            exec: 'poll',
        },
        // 발행자: S3와 동일한 rate/duration
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
        'poll_e2e_latency_ms': ['p(95)<1500', 'p(99)<2000'],
        'notification_send_failed': ['rate<0.01'],
    },
};

export function setup() {
    const subscribers = [];

    for (let i = 0; i < SUBSCRIBER_COUNT; i++) {
        const user = createUserAndGetToken(i);
        subscribers.push(user);
    }

    return {
        subscribers,
        publisherToken: subscribers[0].token,
    };
}

// ---- 구독자: 1초 주기 polling ----
export function poll(data) {
    const vuIndex = __VU - 1;
    const user = data.subscribers[vuIndex];

    if (!user || !user.token) return;

    let afterId = 0;
    const headers = authHeaders(user.token);
    const startTime = Date.now();
    const warmUpEnd = startTime + PUBLISHER_START_DELAY_SEC * 1000;
    const endTime = startTime + (PUBLISHER_START_DELAY_SEC + PUBLISHER_TOTAL_DURATION_SEC + FLUSH_GRACE_SEC) * 1000;

    while (Date.now() < endTime) {
        const loopStart = Date.now();
        const isWarmUp = loopStart < warmUpEnd;

        const url = `${API_PREFIX}/notifications?afterId=${afterId}`;
        const res = http.get(url, {
            ...headers,
            tags: { name: 'poll_notifications' },
        });

        totalRequests.add(1);

        const ok = check(res, {
            'poll: status 200': (r) => r.status === 200,
        });

        if (ok) {
            try {
                const notifications = JSON.parse(res.body);

                if (notifications.length === 0) {
                    // warm-up 구간은 빈 응답 통계에서 제외
                    if (!isWarmUp) {
                        emptyResponses.add(true);
                        emptyCount.add(1);
                    }
                } else {
                    if (!isWarmUp) {
                        emptyResponses.add(false);
                        nonEmptyCount.add(1);
                    }

                    for (const notification of notifications) {
                        // afterId 갱신: 수신한 알림 중 가장 큰 id
                        if (notification.id > afterId) {
                            afterId = notification.id;
                        }

                        // latency 계산: content에 sentAt이 포함된 경우
                        if (notification.content && notification.content.startsWith('{')) {
                            try {
                                const payload = JSON.parse(notification.content);
                                if (payload._e2e && payload.sentAt) {
                                    const latency = Date.now() - payload.sentAt;
                                    e2eLatency.add(latency);
                                    receivedEvents.add(1);
                                }
                            } catch (_) {
                                // content 파싱 실패 무시
                            }
                        }
                    }
                }
            } catch (_) {
                // body 파싱 실패 무시
            }
        }

        // polling 주기 보정: 요청 소요 시간을 빼서 정확히 1초 유지
        const elapsed = (Date.now() - loopStart) / 1000;
        const remaining = 1 - elapsed;
        if (remaining > 0) {
            sleep(remaining);
        }
    }
}

// ---- 발행자: S3와 동일 ----
export function publish(data) {
    const randomIdx = Math.floor(Math.random() * data.subscribers.length);
    const targetUserId = data.subscribers[randomIdx].userId;

    const sentAt = Date.now();
    const payload = JSON.stringify({
        userId: targetUserId,
        type: 'SYSTEM',
        title: `E2E test ${sentAt}`,
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
// delivery_rate = 서버 저장 성공(201) 대비 클라이언트 polling 수신율.
// 엄밀한 transport ack가 아닌, "발행 → DB 저장 → polling으로 회수"까지의 end-to-end 전달률.
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

export function handleSummary(data) {
    const published = data.metrics.poll_published_count
        ? data.metrics.poll_published_count.values.count
        : 0;
    const received = data.metrics.poll_received_events
        ? data.metrics.poll_received_events.values.count
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

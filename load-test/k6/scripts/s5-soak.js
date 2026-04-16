/**
 * S5: Soak 테스트 (장시간 안정성)
 *
 * 목적: 일정한 부하를 장시간 유지하여 리소스 누수, GC 압박을 감지
 *
 * 단기 부하로는 안 보이는 문제를 발견한다:
 * - 힙 메모리가 계속 우상향 → 메모리 누수
 * - GC pause 빈도/시간 증가 → 곧 OOM
 * - FD가 계속 증가 → emitter cleanup 누락
 * - sse_connections_active가 유지되지 않고 감소 → 연결 정리 문제
 *
 * 동작 (두 시나리오 동시 실행, 기본 30분):
 * - 구독자(subscribers): 고정된 수의 VU가 주기적으로 재연결하며 유지
 *   * per-vu-iterations가 아닌 constant-vus로 churn 포함
 * - 발행자(publisher): 낮은 고정 TPS로 꾸준히 전송
 *
 * 관측 지표 (Grafana — 시간축을 길게 보고 추세 확인):
 * - jvm_memory_used_bytes{area="heap"}: 힙 사용량 (수렴 vs 우상향)
 * - jvm_gc_pause_seconds_sum rate: GC 부담
 * - process_files_open_files: FD 추세
 * - sse_connections_active: 연결 수 안정성
 * - hikaricp_connections_active: DB 커넥션 안정성
 *
 * 실행: ./k6 run scripts/s5-soak.js
 * 짧게 검증: ./k6 run -e SOAK_DURATION=5m scripts/s5-soak.js
 */
import sse from 'k6/x/sse';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';
import { createUserAndGetToken, authHeaders } from '../utils/auth.js';
import { BASE_URL, API_PREFIX } from '../utils/config.js';

const sseConnectSuccess = new Rate('sse_connect_success');
const sendFailed = new Rate('notification_send_failed');

const SUBSCRIBER_COUNT = 50;
const SEND_RATE = 10;                                // 초당 10건 (가벼운 지속 부하)
const SOAK_DURATION = __ENV.SOAK_DURATION || '30m';  // 기본 30분, 환경변수로 조정 가능

export const options = {
    setupTimeout: `${Math.max(60, Math.ceil(SUBSCRIBER_COUNT / 5))}s`,
    scenarios: {
        // 구독자: 50명이 계속 연결/재연결을 반복. churn 포함.
        subscribers: {
            executor: 'constant-vus',
            vus: SUBSCRIBER_COUNT,
            duration: SOAK_DURATION,
            exec: 'subscribe',
        },
        publisher: {
            executor: 'constant-arrival-rate',
            rate: SEND_RATE,
            timeUnit: '1s',
            duration: SOAK_DURATION,
            preAllocatedVUs: 5,
            maxVUs: 20,
            exec: 'publish',
        },
    },
    thresholds: {
        'sse_connect_success': ['rate>0.95'],
        'notification_send_failed': ['rate<0.01'],
    },
};

export function setup() {
    const subscribers = [];
    for (let i = 0; i < SUBSCRIBER_COUNT; i++) {
        const user = createUserAndGetToken(i);
        subscribers.push(user);
    }
    return { subscribers, publisherToken: subscribers[0].token };
}

// ---- 구독자: 연결 → 서버 timeout 대기 → 재연결 반복 ----
export function subscribe(data) {
    const vuIndex = __VU - 1;
    const user = data.subscribers[vuIndex];
    if (!user || !user.token) return;

    const url = `${BASE_URL}/api/v1/notifications/stream`;
    const params = { headers: { 'Authorization': `Bearer ${user.token}` } };

    const response = sse.open(url, params, function (client) {
        client.on('event', function () { /* ok */ });
        client.on('error', function (e) {
            console.error(`SSE error VU ${__VU}: ${e.error()}`);
        });
    });

    sseConnectSuccess.add(response && response.status === 200);

    // 재연결까지 1~3초 간격 (지터)
    sleep(1 + Math.random() * 2);
}

// ---- 발행자 ----
export function publish(data) {
    const target = data.subscribers[Math.floor(Math.random() * data.subscribers.length)];

    const payload = JSON.stringify({
        userId: target.userId,
        type: 'SYSTEM',
        title: `Soak ${__ITER}`,
        content: `Soak test notification ${Date.now()}`,
    });

    const res = http.post(`${API_PREFIX}/notifications`, payload, {
        ...authHeaders(data.publisherToken),
        tags: { name: 'send_notification' },
    });

    const ok = check(res, { 'send: status 201': (r) => r.status === 201 });
    sendFailed.add(!ok);
}

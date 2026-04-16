/**
 * S2: 알림 전송 TPS 부하 테스트
 *
 * 목적: SSE 연결 없이 POST /notifications API의 처리량을 목표 TPS로 제어하며 측정
 *
 * 동작:
 * 1. setup에서 유저 생성 + 토큰 발급 (알림 대상 userId도 보존)
 * 2. ramping-arrival-rate로 목표 요청률을 고정하여 서버에 부하
 * 3. 서버 응답시간과 무관하게 일정 TPS를 유지 → 정확한 처리량 측정
 *
 * 관측 지표 (Grafana):
 * - HTTP Request Duration: API 응답시간
 * - HTTP Request Rate: 초당 요청 수 (TPS)
 * - HikariCP Connections: DB 커넥션 사용량
 * - Executor Threads: 스레드 사용량
 *
 * 실행: ./k6 run scripts/s2-notification-tps.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';
import { createUserAndGetToken, authHeaders } from '../utils/auth.js';
import { API_PREFIX } from '../utils/config.js';

const sendFailed = new Rate('notification_send_failed');

export const options = {
    setupTimeout: '60s',
    // ramping-arrival-rate: 서버 응답시간과 무관하게 목표 TPS를 유지
    // "서버가 N TPS를 버틸 수 있는가"를 정확히 측정할 수 있다
    scenarios: {
        send_notifications: {
            executor: 'ramping-arrival-rate',
            startRate: 10,          // 초당 10 요청으로 시작
            timeUnit: '1s',
            preAllocatedVUs: 100,   // 미리 할당할 VU 수
            maxVUs: 300,            // 부하 증가 시 추가 할당 가능 VU
            stages: [
                { duration: '15s', target: 50 },    // 초당 50
                { duration: '15s', target: 100 },   // 초당 100
                { duration: '15s', target: 200 },   // 초당 200
                { duration: '1m', target: 200 },    // 초당 200 유지
                { duration: '15s', target: 0 },     // 정리
            ],
        },
    },
    thresholds: {
        'http_req_duration{name:send_notification}': ['p(95)<500', 'p(99)<1000'],
        'notification_send_failed': ['rate<0.01'],
        'dropped_iterations': ['count==0'],
    },
};

export function setup() {
    const user = createUserAndGetToken(0);
    return { userId: user.userId, token: user.token };
}

export default function (data) {
    const payload = JSON.stringify({
        userId: data.userId,
        type: 'SYSTEM',
        title: `Load test ${__VU}-${__ITER}`,
        content: `Notification from VU ${__VU}, iteration ${__ITER}`,
    });

    const res = http.post(`${API_PREFIX}/notifications`, payload, {
        ...authHeaders(data.token),
        tags: { name: 'send_notification' },
    });

    const success = check(res, {
        'send: status 201': (r) => r.status === 201,
    });

    sendFailed.add(!success);

    if (!success) {
        console.error(`Send failed VU ${__VU}: status=${res.status}, body=${res.body}`);
    }
}

/**
 * S1: SSE 동시 연결 검증 테스트
 *
 * 목적: "MAX_VUS개의 SSE 연결을 서버가 안정적으로 유지할 수 있는가"를 검증한다.
 *       최대치를 자동으로 찾는 시나리오가 아니라, 특정 동시 연결 수를 버티는지 검증하는 시나리오다.
 *       최대치를 찾으려면 MAX_VUS를 500, 1000, 1500, 2000처럼 올려가며 반복 실행하여
 *       성공률/에러/서버 메트릭이 무너지기 시작하는 지점을 한계 후보로 판단한다.
 *
 * 동작:
 * 1. setup에서 MAX_VUS만큼 유저를 생성하고 JWT 토큰을 발급
 * 2. per-vu-iterations로 각 VU가 딱 1회만 SSE 연결을 열고 유지 (재연결/churn 없음)
 * 3. SSE 서버 timeout(60초) 내에 연결이 유지되는 동안 관측
 *
 * 판정 기준:
 * - sse_connect_success rate > 0.95 (연결 성공률)
 * - sse_runtime_error rate < 0.05 (연결 중 에러 비율)
 * - Grafana에서 리소스 한계(스레드/FD/힙/DB)에 도달했는지 동시 확인
 *
 * 관측 지표 (Grafana):
 * - sse_connections_active: 현재 활성 연결 수
 * - executor_active_threads: Tomcat 스레드 사용량
 * - process_files_open_files: FD 사용량
 * - jvm_memory_used_bytes: 힙 메모리
 *
 * 실행: ./k6 run scripts/s1-sse-connections.js
 * 한계 탐색: MAX_VUS를 늘려가며 반복 실행
 */
import sse from 'k6/x/sse';
import { Rate } from 'k6/metrics';
import { createUserAndGetToken } from '../utils/auth.js';
import { BASE_URL } from '../utils/config.js';

const sseConnectSuccess = new Rate('sse_connect_success');
const sseRuntimeError = new Rate('sse_runtime_error');

const MAX_VUS = 500;

// per-vu-iterations: 각 VU가 정확히 1회만 실행.
// 재연결/churn 없이 "500개 연결을 동시에 유지할 수 있는가"를 측정한다.
export const options = {
    setupTimeout: `${Math.max(120, Math.ceil(MAX_VUS / 5))}s`,
    scenarios: {
        sse_connections: {
            executor: 'per-vu-iterations',
            vus: MAX_VUS,
            iterations: 1,
            maxDuration: '2m',
        },
    },
    thresholds: {
        'sse_connect_success': ['rate>0.95'],
        'sse_runtime_error': ['rate<0.05'],
    },
};

export function setup() {
    const users = [];

    for (let i = 0; i < MAX_VUS; i++) {
        const user = createUserAndGetToken(i);
        users.push(user);
    }

    return { users };
}

export default function (data) {
    const vuIndex = __VU - 1;
    const user = data.users[vuIndex];

    if (!user || !user.token) {
        sseConnectSuccess.add(false);
        sseRuntimeError.add(true);
        return;
    }

    const url = `${BASE_URL}/api/v1/notifications/stream`;
    const params = {
        headers: {
            'Authorization': `Bearer ${user.token}`,
        },
    };

    let hadError = false;

    const response = sse.open(url, params, function (client) {
        client.on('open', function () {
            // 연결 성공 — 서버 timeout(60초)까지 유지
        });

        client.on('event', function (event) {
            // 이벤트 수신
        });

        client.on('error', function (e) {
            hadError = true;
            console.error(`SSE error VU ${__VU}: ${e.error()}`);
        });
    });

    const connected = response && response.status === 200;
    sseConnectSuccess.add(connected);
    if (!connected) {
        console.error(`SSE connect failed VU ${__VU}: status=${response ? response.status : 'null'}`);
    }
    // 성공/실패 모두 기록하여 Rate 비율이 정확하게 계산됨
    sseRuntimeError.add(hadError);
}

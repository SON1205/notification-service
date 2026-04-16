/**
 * S1: SSE 동시 연결 한계 테스트
 *
 * 목적: 단일 인스턴스에서 SSE 동시 연결을 몇 개까지 유지할 수 있는지 측정
 *
 * 동작:
 * 1. setup에서 VU 수만큼 유저를 생성하고 JWT 토큰을 발급
 * 2. 각 VU가 SSE 연결을 열고 유지
 * 3. VU를 점진적으로 올리며 연결 성공률/에러율 관측
 *
 * 관측 지표 (Grafana):
 * - sse_connections_active: 현재 활성 연결 수
 * - tomcat_threads_busy_threads: Tomcat 스레드 사용량
 * - process_files_open_files: FD 사용량
 * - jvm_memory_used_bytes: 힙 메모리
 *
 * 실행: ./k6 run scripts/s1-sse-connections.js
 */
import sse from 'k6/x/sse';
import {sleep} from 'k6';
import {Rate} from 'k6/metrics';
import {createUserAndGetToken} from '../utils/auth.js';
import {BASE_URL} from '../utils/config.js';

// 커스텀 메트릭: 단계별 성공률을 분리 추적
const sseConnectSuccess = new Rate('sse_connect_success');
const sseRuntimeError = new Rate('sse_runtime_error');

// 서버 SSE timeout은 60초.
// plateau(50초)를 timeout보다 짧게 두어 재연결이 섞이지 않게 한다.
const MAX_VUS = 1500;

export const options = {
    // setup()에서 유저 생성이 순차 실행되므로 VU 수에 비례하여 시간 필요
    setupTimeout: `${Math.max(120, Math.ceil(MAX_VUS / 5))}s`,
    stages: [
        {duration: '30s', target: 50},
        {duration: '30s', target: 200},
        {duration: '30s', target: MAX_VUS},
        {duration: '50s', target: MAX_VUS},   // plateau < 서버 timeout(60초)
        {duration: '30s', target: 0},
    ],
    thresholds: {
        'sse_connect_success': ['rate>0.95'],
        'sse_runtime_error': ['rate<0.05'],
    },
};

export function setup() {
    const tokens = [];

    for (let i = 0; i < MAX_VUS; i++) {
        const token = createUserAndGetToken(i);
        tokens.push(token);
    }

    return {tokens};
}

export default function (data) {
    const vuIndex = __VU - 1;
    const token = data.tokens[vuIndex];

    if (!token) {
        console.error(`No token for VU ${__VU}`);
        sseConnectSuccess.add(false);
        sleep(1);
        return;
    }

    const url = `${BASE_URL}/api/v1/notifications/stream`;
    const params = {
        headers: {
            'Authorization': `Bearer ${token}`,
        },
    };

    const response = sse.open(url, params, function (client) {
        client.on('open', function () {
            // 연결 성공 — plateau 동안 유지
        });

        client.on('event', function (event) {
            // 이벤트 수신 확인
        });

        client.on('error', function (e) {
            sseRuntimeError.add(true);
            console.error(`SSE error VU ${__VU}: ${e.error()}`);
        });
    });

    const connected = response && response.status === 200;
    sseConnectSuccess.add(connected);

    if (!connected) {
        console.error(`SSE connect failed VU ${__VU}: status=${response ? response.status : 'null'}`);
    }

    // 연결 종료 후 짧은 대기 — 바로 재연결하면 churn 부하가 됨
    sleep(1);
}

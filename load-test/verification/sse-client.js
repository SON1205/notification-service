/**
 * SSE 수동 재연결 복구 검증 클라이언트
 *
 * 목적: Last-Event-ID 헤더를 수동으로 넣어 재연결했을 때,
 *       서버가 오프라인 구간에 쌓인 알림을 순서대로 복구해 전달하는지 확인한다.
 *
 * 주의: 브라우저 EventSource의 "자동 재연결"은 여기서 검증하지 않는다.
 *       라이브러리가 자동 재연결할 때 Last-Event-ID가 어떻게 실리는지 보려면
 *       별도 시나리오로 강제 네트워크 끊김 + 라이브러리 재연결 관찰이 필요하다.
 *
 * 시나리오:
 * 1. 유저 생성 → 로그인 → SSE 연결 (open 이벤트 대기)
 * 2. 알림 1~5 발송 → 5건 수신 polling
 * 3. close() 후 오프라인 상태에서 알림 6~10 발송
 * 4. 이전 Last-Event-ID를 헤더에 실어 재연결 (open 이벤트 대기)
 * 5. 6~10 복구 수신 polling
 * 6. 검증: 순서, 개수, ID 중복 여부
 *
 * 실행: npm install && npm run verify
 *       BASE_URL=http://localhost:8080 npm run verify
 */
import EventSource from 'eventsource';

const BASE_URL = process.env.BASE_URL || 'http://localhost:8080';
const API = `${BASE_URL}/api/v1`;
const POLL_TIMEOUT_MS = 10000;
const POLL_INTERVAL_MS = 50;

const username = `verify_${Date.now()}`;
const password = 'VerifyTest1234!';

// ---- API 호출 유틸 ----
async function signup() {
    const res = await fetch(`${API}/auth/signup`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
    });
    if (!res.ok && res.status !== 409) {
        throw new Error(`signup failed: ${res.status} ${await res.text()}`);
    }
}

async function login() {
    const res = await fetch(`${API}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
    });
    if (!res.ok) throw new Error(`login failed: ${res.status}`);
    return (await res.json()).token;
}

function getUserIdFromToken(token) {
    const payload = token.split('.')[1];
    const decoded = Buffer.from(payload, 'base64url').toString('utf-8');
    return Number(JSON.parse(decoded).sub);
}

async function sendNotification(token, userId, title) {
    const res = await fetch(`${API}/notifications`, {
        method: 'POST',
        headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({ userId, type: 'SYSTEM', title, content: title }),
    });
    if (!res.ok) throw new Error(`send failed: ${res.status}`);
    return await res.json();
}

// ---- SSE 연결 헬퍼 ----
// open 이벤트가 올 때까지 기다렸다가 { es, received } 반환
function connectSSE(token, lastEventId = null) {
    const headers = { 'Authorization': `Bearer ${token}` };
    if (lastEventId) headers['Last-Event-ID'] = lastEventId;

    const es = new EventSource(`${API}/notifications/stream`, { headers });
    const received = [];

    es.addEventListener('notification', (event) => {
        received.push({ id: event.lastEventId, data: JSON.parse(event.data) });
    });

    return new Promise((resolve, reject) => {
        const timer = setTimeout(() => {
            es.close();
            reject(new Error('SSE open timeout'));
        }, POLL_TIMEOUT_MS);

        // "connect" 더미 이벤트가 서버에서 오면 연결 확정
        es.addEventListener('connect', () => {
            clearTimeout(timer);
            resolve({ es, received });
        });

        es.onerror = (err) => {
            // 일부 EventSource 구현은 onerror를 재연결 신호로도 씀 → readyState로 구분
            if (es.readyState === EventSource.CLOSED) {
                clearTimeout(timer);
                reject(new Error(`SSE connection closed: ${JSON.stringify(err)}`));
            }
        };
    });
}

// 기대 개수만큼 수신될 때까지 polling
async function waitForCount(received, expected) {
    const start = Date.now();
    while (received.length < expected && Date.now() - start < POLL_TIMEOUT_MS) {
        await new Promise(r => setTimeout(r, POLL_INTERVAL_MS));
    }
    if (received.length < expected) {
        throw new Error(`waitForCount timeout: expected=${expected}, actual=${received.length}`);
    }
}

// ---- 메인 시나리오 ----
async function main() {
    console.log(`[SETUP] user=${username}`);
    await signup();
    const token = await login();
    const userId = getUserIdFromToken(token);
    console.log(`[SETUP] userId=${userId}`);

    // ---- Step 1: 첫 연결 (open 대기) + 알림 1~5 발송 + 5건 수신 ----
    console.log('\n[STEP 1] 첫 연결 + 알림 1~5 수신');
    const { es: es1, received: received1 } = await connectSSE(token);

    for (let i = 1; i <= 5; i++) {
        await sendNotification(token, userId, `Msg ${i}`);
    }

    await waitForCount(received1, 5);
    es1.close();
    console.log(`  수신 ${received1.length}건: ${received1.map(r => r.data.title).join(', ')}`);

    // ---- Step 2: 오프라인 상태에서 알림 6~10 발송 ----
    console.log('\n[STEP 2] 오프라인 상태에서 알림 6~10 발송');
    const lastId = received1[received1.length - 1].id;
    console.log(`  Last-Event-ID=${lastId}`);

    for (let i = 6; i <= 10; i++) {
        await sendNotification(token, userId, `Msg ${i}`);
    }

    // ---- Step 3: 재연결 (Last-Event-ID 수동 주입) + 6~10 복구 수신 ----
    console.log('\n[STEP 3] 재연결 (Last-Event-ID 수동 주입)');
    const { es: es2, received: received2 } = await connectSSE(token, lastId);

    await waitForCount(received2, 5);
    es2.close();
    console.log(`  복구 수신 ${received2.length}건: ${received2.map(r => r.data.title).join(', ')}`);

    // ---- 검증 ----
    console.log('\n[VERIFY]');
    const step1Ok = received1.length === 5
        && received1.every((r, i) => r.data.title === `Msg ${i + 1}`);
    console.log(`  첫 연결 5건 순서대로 수신: ${step1Ok ? '✓ PASS' : '✗ FAIL'}`);

    const step2Ok = received2.length === 5
        && received2.every((r, i) => r.data.title === `Msg ${i + 6}`);
    console.log(`  재연결 시 오프라인 5건 순서대로 복구: ${step2Ok ? '✓ PASS' : '✗ FAIL'}`);

    const allIds = [...received1, ...received2].map(r => r.id);
    const noDup = allIds.length === new Set(allIds).size;
    console.log(`  전체 수신 알림 ID 중복 없음: ${noDup ? '✓ PASS' : '✗ FAIL'}`);

    const allOk = step1Ok && step2Ok && noDup;
    console.log(`\n[RESULT] ${allOk ? '✓ ALL PASS' : '✗ FAILED'}`);
    process.exit(allOk ? 0 : 1);
}

main().catch(err => {
    console.error('ERROR:', err);
    process.exit(1);
});

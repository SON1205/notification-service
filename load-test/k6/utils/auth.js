import http from 'k6/http';
import encoding from 'k6/encoding';
import { check } from 'k6';
import { API_PREFIX, TEST_USER_PREFIX, TEST_PASSWORD } from './config.js';

function decodeBase64Url(input) {
    const normalized = input.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized + '='.repeat((4 - normalized.length % 4) % 4);
    return encoding.b64decode(padded, 'std', 's');
}

function extractUserIdFromToken(token) {
    try {
        const payload = token.split('.')[1];
        if (!payload) {
            return null;
        }

        const decoded = decodeBase64Url(payload);
        const claims = JSON.parse(decoded);
        const subject = claims.sub;
        return subject ? Number(subject) : null;
    } catch {
        return null;
    }
}

// 테스트 유저를 회원가입시키고 { userId, token }을 반환한다.
// 이미 가입된 유저면 회원가입은 실패하지만 로그인은 성공한다.
// 실패 시 null이 아닌 예외를 던져 setup 단계에서 즉시 중단한다.
export function createUserAndGetToken(userIndex) {
    const username = `${TEST_USER_PREFIX}${userIndex}`;
    const jsonHeaders = { headers: { 'Content-Type': 'application/json' } };

    // 회원가입 (이미 존재하면 409 등 — 정상 흐름)
    const signupRes = http.post(`${API_PREFIX}/auth/signup`, JSON.stringify({
        username: username,
        password: TEST_PASSWORD,
    }), jsonHeaders);

    check(signupRes, {
        'signup: 201 or already exists': (r) => r.status === 201 || r.status === 409,
    });

    // 회원가입 성공 시 userId 추출
    let userId = null;
    if (signupRes.status === 201) {
        try { userId = JSON.parse(signupRes.body).userId; }
        catch { /* 로그인 후 별도 처리 */ }
    }

    // 로그인 → JWT 토큰 획득
    const loginRes = http.post(`${API_PREFIX}/auth/login`, JSON.stringify({
        username: username,
        password: TEST_PASSWORD,
    }), jsonHeaders);

    const loginOk = check(loginRes, {
        'login: status 200': (r) => r.status === 200,
        'login: body has token': (r) => {
            try { return JSON.parse(r.body).token !== undefined; }
            catch { return false; }
        },
    });

    if (!loginOk) {
        throw new Error(`Login failed for ${username}: status=${loginRes.status}, body=${loginRes.body}`);
    }

    const token = JSON.parse(loginRes.body).token;
    if (userId === null) {
        userId = extractUserIdFromToken(token);
    }

    if (userId === null || Number.isNaN(userId)) {
        throw new Error(`Failed to resolve userId for ${username}`);
    }

    return { userId, token };
}

// Authorization 헤더 생성
export function authHeaders(token) {
    return {
        headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json',
        },
    };
}

import http from 'k6/http';
import { check } from 'k6';
import { API_PREFIX, TEST_USER_PREFIX, TEST_PASSWORD } from './config.js';

// 테스트 유저를 회원가입시키고 JWT 토큰을 반환한다.
// 이미 가입된 유저면 회원가입은 실패하지만 로그인은 성공한다.
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
        console.error(`Login failed for ${username}: status=${loginRes.status}, body=${loginRes.body}`);
        return null;
    }

    return JSON.parse(loginRes.body).token;
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

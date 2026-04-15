// k6 부하 테스트 공통 설정
// 환경변수로 오버라이드 가능: k6 run -e BASE_URL=http://... script.js

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const API_PREFIX = `${BASE_URL}/api/v1`;

// 테스트용 기본 계정 (setup에서 회원가입/로그인에 사용)
// 실행마다 고유 prefix를 생성하여 이전 실행의 누적 데이터(알림, unread 등)와 격리
const RUN_ID = __ENV.RUN_ID || Date.now().toString(36);
export const TEST_USER_PREFIX = `lt_${RUN_ID}_`;
export const TEST_PASSWORD = 'LoadTest1234!';

// 공통 임계값
export const DEFAULT_THRESHOLDS = {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.01'],
};

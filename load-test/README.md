# 부하 테스트 가이드

Phase 2 부하 테스트 실행 방법과 결과 해석 가이드.

## 구성

```
load-test/
├── k6/                       # k6 기반 부하 테스트
│   ├── build-k6.sh           # xk6-sse 포함 k6 바이너리 빌드 스크립트
│   ├── utils/                # 공통 유틸 (config, auth, SSE 헬퍼)
│   └── scripts/              # 시나리오별 k6 스크립트
│       ├── s1-sse-connections.js   # S1 동시 연결 검증
│       ├── s2-notification-tps.js  # S2 알림 전송 TPS
│       ├── s3-sse-e2e.js           # S3 SSE + 알림 E2E 지연
│       └── s5-soak.js              # S5 장시간 안정성
└── verification/             # 기능 검증 (Node.js EventSource)
    └── sse-client.js         # Last-Event-ID 재연결 복구 검증
```

## 사전 준비

### 1. 앱 + 모니터링 스택 기동

```bash
# 프로젝트 루트
docker compose up -d
```

| 서비스 | 포트 | 용도 |
|--------|------|------|
| app | 8080 | 알림 서비스 |
| mysql | 3306 | DB |
| prometheus | 9090 | 메트릭 수집 |
| grafana | 3000 | 대시보드 |

### 2. k6 커스텀 바이너리 빌드 (최초 1회)

k6는 SSE를 네이티브 지원하지 않으므로 `xk6-sse` 확장 포함 바이너리가 필요하다.

```bash
cd load-test/k6
./build-k6.sh
./k6 version    # 확장 포함 여부 확인
```

호스트 OS/아키텍처를 감지하여 자동 크로스 컴파일한다. Docker가 필요하다.

## k6 시나리오 실행

### S1 — SSE 동시 연결 검증

MAX_VUS 개의 SSE 연결을 서버가 안정적으로 유지 가능한지 검증.
최대치를 자동 탐색하지 않으므로, 한계를 찾으려면 MAX_VUS를 올려가며 반복 실행한다.

```bash
./k6 run scripts/s1-sse-connections.js
```

**판정 기준**
- `sse_connect_success` > 0.95
- `sse_runtime_error` < 0.05
- Grafana 상단 "테스트 판정" 섹션에서 `sse_connections_active`가 MAX_VUS까지 올라가는지 확인

### S2 — 알림 전송 TPS

SSE 연결 없이 POST /notifications의 처리량을 목표 TPS로 제어하며 측정.

```bash
./k6 run scripts/s2-notification-tps.js
```

**판정 기준**
- `http_req_duration{name:send_notification}` p95 < 500ms, p99 < 1000ms
- `notification_send_failed` < 0.01
- `dropped_iterations` == 0 (부하 과포화 감지)

### S3 — SSE + 알림 E2E 지연

구독자 N명 유지 + 발행자 M TPS로 알림 전송 시 end-to-end 지연 측정.

```bash
./k6 run scripts/s3-sse-e2e.js
```

**판정 기준**
- `sse_e2e_latency_ms` p95 < 1000, p99 < 2000
- `notification_send_failed` < 0.01

### S5 — Soak (장시간 안정성)

일정한 부하를 장시간 유지하여 메모리 누수, GC 압박, FD 누수 감지.

```bash
./k6 run scripts/s5-soak.js                      # 기본 30분
./k6 run -e SOAK_DURATION=5m scripts/s5-soak.js  # 검증용 5분
```

**판정 기준**
- Grafana 하단 "서버 리소스" 섹션에서 시간축 전체 확인:
  - 힙 메모리 수렴 vs 우상향
  - GC pause 빈도 증가 없음
  - FD 안정적
  - `sse_connections_active` 안정 유지

## 기능 검증 실행 (S4)

Last-Event-ID 기반 재연결 복구가 정상 동작하는지 검증.

```bash
cd load-test/verification
npm install
npm run verify
```

**확인 항목**
- 첫 연결 5건 순서 수신
- 재연결 시 오프라인 중 쌓인 5건 복구
- 알림 ID 중복 없음

## 결과 해석

### Grafana 대시보드 구성

`http://localhost:3000` → **SSE Notification Service** 대시보드

| 섹션 | 용도 |
|------|------|
| **테스트 판정 (SSE)** | 활성 연결 수, Send rate, Subscribe/Timeout/Reconnect |
| **테스트 판정 (HTTP)** | HTTP Request Rate/Duration (엔드포인트/상태별) |
| **서버 리소스 (원인 분석)** | Executor Threads, HikariCP, JVM Heap/GC, FD, CPU |

### 해석 흐름

1. **판정 섹션**에서 테스트 임계값 통과 여부 확인
2. **이상 지점이 있으면 같은 시간축으로 하단 리소스 섹션** 확인 (무엇이 먼저 한계에 닿았는가)
3. k6 터미널 출력의 custom 메트릭과 Grafana 그래프를 교차 검증

## 로컬 vs AWS

로컬 테스트는 **스크립트 검증과 병목 패턴 발견**에 목적이 있다. 절대 수치(TPS/연결 수)는 맥북 리소스 공유 때문에 실운영 환경을 대표하지 않는다.
의미 있는 baseline 수치는 AWS 배포 후 측정한다.

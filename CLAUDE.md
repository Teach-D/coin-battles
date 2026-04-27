# CLAUDE.md

이 파일은 Claude Code가 이 저장소에서 작업할 때 참고하는 가이드입니다.

## 프로젝트 개요

**CoinBattle** — 트레이딩 배틀 게임

> "코인 투자 시뮬레이터가 아니라 트레이딩 배틀 게임"

핵심 루프: 10분 PVP 한 판 → 결과 카드 자동 생성 → SNS 공유 → 바이럴 유입

우리가 주는 것: 경쟁 / 자랑 / 승부 / 짧은 도파민
우리가 주지 않는 것: 투자 연습 / 실력 측정 / 리스크 교육

- 원격 저장소: https://github.com/Teach-D/coin-battle.git
- 작성자: 김동현 (Backend Engineer)

## 프로젝트 상태

현재 초기 설정 단계 — 소스 파일, 빌드 시스템, 의존성 미설정.
스캐폴딩 완료 후 아래 섹션을 채울 것:
- 빌드/린트/테스트 명령어
- 로컬 실행 방법

## 기술 스택

| 영역 | 기술                                                   |
|------|------------------------------------------------------|
| 프론트엔드 | React 18 (Vite), TailwindCSS, Recharts, Zustand, TanStack Query |
| 모바일 | React 웹뷰 (PWA + 앱 래핑), FCM 푸시                           |
| 백엔드 | Spring Boot 3.x + Kotlin Coroutine                   |
| 실시간 통신 | Spring WebSocket + STOMP, Redis Pub/Sub              |
| 데이터베이스 | PostgreSQL (월별 Range 파티셔닝, 커버링 인덱스), Redis 7.x       |
| 비동기 처리 | Spring ApplicationEventPublisher + @Async (MVP 단계)   |
| 외부 시세 | 업비트 WebSocket (원화 200개+), 바이낸스 WebSocket (글로벌 800개+) |
| 배포 | Oracle Cloud Free Tier + Docker + GitHub Actions     |
| 모니터링 | Prometheus + Grafana                                 |

## MVP 핵심 기능 5가지

1. **실시간 코인 시세** — 업비트 200개 + 바이낸스 800개+ 동시 수신, 1~3초 브로드캐스트
2. **매수/매도 + 레버리지 + 숏** — 레버리지 1x~10x, 공매도, 시장가/지정가, 분할매수
3. **PVP 배틀 모드** — 1v1/3인/5인, 10분/30분/1시간, 랜덤 매칭 + 친구 초대
4. **단순 수익률 랭킹** — Redis Sorted Set, 전체/데일리/PVP 승률
5. **결과 공유 카드** — 승리 카드, 청산 카드(💀 밈), 데일리/시즌 결산 카드

## 핵심 아키텍처 흐름

### 시세 흐름
```
업비트/바이낸스 WebSocket
  → Spring Client
  → Redis Hash 캐싱 (TTL 3초)
  → Redis Pub/Sub
  → 모든 서버 인스턴스
  → STOMP /topic/coin/{ticker}
  → 유저
```

### 주문 흐름
```
유저 매수/매도 요청
  → Redisson 분산 락 (user:{id}:order, TTL 3초)
  → 잔고 검증
  → DB 트랜잭션 + 낙관적 락 (@Version)
  → Spring ApplicationEvent 발행
  → @Async 팬아웃: 체결기록 저장 / 랭킹 갱신 / 알림 / 카드 생성
```

### 결과 카드 생성
```
배틀 종료 / 강제청산 발생
  → @Async 비동기 메서드 호출
  → 유저 거래 데이터 조회 + 이미지 생성 (Canvas / Headless Chrome)
  → S3 업로드
  → Redis Pub/Sub → 유저에게 카드 URL 푸시
```

### 기타 비동기 처리
- **알림 발송**: Redis Pub/Sub → WebSocket / FCM
- **펀딩비 정산**: `@Scheduled` (8시간 주기) + `@Async` 배치 실행
- **강제청산 모니터링**: Kotlin Coroutine Scheduler (1초 주기)

## 동시성 3단계 방어

| 단계 | 방식 | 역할 |
|------|------|------|
| 1 | Redisson 분산 락 (`user:{id}:order`, TTL 3초) | 동시 주문 직렬화 |
| 2 | 낙관적 락 (`@Version`) | DB 레벨 잔고 이중 차감 방지 |
| 3 | 멱등성 키 (클라이언트 UUID) | 동일 요청 재처리 차단 |

## 주요 설계 결정

### 레버리지별 차등 강제청산
```
청산 기준 손실률 = -(1 / 레버리지) × 0.9

2x  →  -45%   /   3x  →  -30%   /   5x  →  -18%   /   10x  →  -9%
```
10배 레버리지 시 -9% 하락만으로 청산 → 실전 선물 거래소 경험 제공

### 슬리피지 시뮬레이션
| 주문 금액 | 체결가 보정 |
|----------|------------|
| 100만원 이하 | 없음 (즉시 체결가) |
| 100만 ~ 500만원 | ±0.05% |
| 500만원 ~ 전액 | ±0.1~0.3% (랜덤) |

### 펀딩비
- 주기: 8시간 (00:00 / 08:00 / 16:00 UTC)
- Spring Scheduler + @Async → 전체 오픈 포지션 일괄 정산

### PVP 공정성
- 체결 시각 = **서버 수신 시각** 기준 (클라이언트 시각 무시)
- 매칭 시 Redis에 모든 참가자의 기준가 스냅샷 고정

### 랭킹 (Redis Sorted Set)
```
ZADD leaderboard:season {평가금액} {userId}   # 시즌 전체
ZADD leaderboard:daily  {평가금액} {userId}   # 데일리 (자정 초기화)
ZREVRANK  → O(log n) 본인 순위
ZREVRANGE 0 99 → Top 100
```

## 코딩 규칙

- 코드 작성 시 주석을 달지 않는다.

## 개발 가이드

각 영역별 상세 개발 가이드는 하위 디렉토리 CLAUDE.md 참조:
- `backend/CLAUDE.md` — Gradle 빌드/테스트 명령어, 패키지 구조, 구현 패턴
- `frontend/CLAUDE.md` — (스캐폴딩 후 작성)

현재 Phase: **Phase 1** (회원/인증, 업비트 시세, 매수/매도, 레버리지+숏, 기본 랭킹)

## 배포 환경

- **VM 1** (Spring Boot 백엔드): 2 OCPU, 12GB RAM
- **VM 2** (Redis + PostgreSQL): 2 OCPU, 12GB RAM
- Nginx 리버스 프록시 + Let's Encrypt SSL
- Blue-Green 무중단 배포
- WebSocket 끊김 → Corout-+
- 업비트 장애 → 빗썸 REST API -+r)

## 외부 API

| API | 용도              | 비용 |
|-----|-----------------|------|
| 업비트 WebSocket | 원화 마켓-+PI 키 불필요 |
| 바이낸스 WebSocket | 글로벌 -+I 키 불필요   |
| 빗썸 REST API | 업비트 장애 -+        

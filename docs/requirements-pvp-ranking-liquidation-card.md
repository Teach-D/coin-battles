# 요구사항 명세서 — PVP 승률 랭킹 + 청산 밈 카드 생성

---

## 1. 개요

### 기능 1: PVP 승률 랭킹

- **기능 목적**: PVP 배틀 결과를 기반으로 유저별 승률을 집계해 별도 랭킹 탭에서 조회할 수 있게 한다.
- **핵심 사용자**: USER (인증된 플레이어), 시스템 (배틀 종료 이벤트)
- **범위**
  - In Scope: PVP 승률 Sorted Set 관리, 배틀 종료 시 승률 갱신, 전용 랭킹 조회 API, 프론트엔드 탭 추가
  - Out of Scope: 시즌 초기화, 실시간 WebSocket 브로드캐스트, 무승부/기권 별도 처리

### 기능 2: 청산 밈 카드 생성

- **기능 목적**: 강제청산 발생 시 💀 밈 이미지 카드를 자동 생성·업로드하고 유저에게 WebSocket으로 알린다.
- **핵심 사용자**: 시스템 (LiquidationScheduler), USER (카드 수신 및 공유)
- **범위**
  - In Scope: 청산 카드 이미지 생성, S3 업로드, STOMP 개인 큐 알림
  - Out of Scope: SNS 자동 공유, 배틀 결과 카드와 UI 공유 모달 재사용 여부

---

## 2. 도메인 모델

### 기능 1: PVP 승률 랭킹

#### 신규 Redis 구조

| 키 패턴 | 타입 | 용도 |
|---------|------|------|
| `pvp:stats:{userId}` | Hash (`wins`, `total`) | 승/패 횟수 누적 |
| `leaderboard:pvp-winrate` | Sorted Set (score = winRate × 100) | 승률 랭킹 정렬 |

- `winRate` = wins / total × 100.0 (double, 예: 75.5% → score 7550)
- score를 정수 `winRate × 100`으로 관리해 `RankingEntryResponse.evaluatedValue: Long`에 그대로 담음
- total = 0인 유저는 Sorted Set에 미등재 (나누기 0 방지)

#### 기존 엔티티 참조

- `Battle`: FINISHED 상태 + `winnerId` 필드로 승자 판별
- `BattleSession`: `participantId`로 패자 식별
- `User`: userId로 Redis 키 구성

### 기능 2: 청산 밈 카드

신규 Redis 키 없음. 기존 파이프라인 확장:

| 클래스 | 역할 |
|--------|------|
| `BattleCardImageService` | `generateLiquidationCard()` 메서드 추가 |
| `BattleCardPipelineService` | `generateAndBroadcastLiquidationCard()` 오버로드 추가 |
| `LiquidationScheduler` | forceClose 성공 후 카드 파이프라인 트리거 |

신규 DTO:

```kotlin
data class LiquidationCardReadyMessage(
    val type: String = "LIQUIDATION_CARD_READY",
    val ticker: String,
    val cardImageUrl: String,
    val timestamp: String = Instant.now().toString()
)
```

---

## 3. 비즈니스 규칙

### 기능 1: PVP 승률 랭킹

1. **BR-01** 승패 갱신 원자성
   - 조건: `updatePvpWinRate(userId, isWin)` 호출 시
   - 처리: Redis HINCRBY로 `pvp:stats:{userId}` 의 `total` +1, `isWin=true`면 `wins` +1 (원자적 HINCRBY 2회)
   - 직후: 최신 wins / total 로 Sorted Set 점수 갱신 (ZADD)

2. **BR-02** total = 0 유저 미등재
   - 조건: 첫 배틀 전까지
   - 처리: Sorted Set에 삽입하지 않음 (ZeroDivision 방지)

3. **BR-03** 다인 배틀 승패 판정
   - 조건: 3인/5인 배틀에서 `battle.winnerId`로 단독 승자 판별
   - 처리: 승자 외 모든 참가자는 패배로 기록 (`isWin = participantId == winnerId`)
   - 위반 시: 위반 케이스 없음 — winnerId null이면 전원 무처리

4. **BR-04** 동시 배틀 종료 중복 갱신 방지
   - 조건: `BattleEndService.finishBattleInTransaction` 내 @Transactional 보호
   - 처리: Redis 갱신은 DB 트랜잭션 커밋 후 `@TransactionalEventListener(AFTER_COMMIT) + @Async`로 실행
   - 위반 시: 동일 battleId로 중복 이벤트 발생하지 않도록 `battle.finish()` 멱등성 보장

5. **BR-05** 랭킹 조회 limit
   - 조건: `?limit` 파라미터 생략 시 기본값 100, 최대 100
   - 위반 시: 100 초과 요청은 100으로 cap

### 기능 2: 청산 밈 카드

6. **BR-06** 카드 생성 실패 무중단
   - 조건: S3 업로드 실패, 이미지 생성 예외 등
   - 처리: `catch` 후 로깅 — 청산 자체는 이미 완료됐으므로 카드 실패가 청산 롤백을 유발하지 않음

7. **BR-07** 카드 생성은 비동기
   - 조건: `LiquidationScheduler` 는 1초 주기 Coroutine 루프로 동작
   - 처리: `BattleCardPipelineService` 호출을 `CoroutineScope(Dispatchers.IO).launch { }` 안에서 별도 코루틴으로 실행하여 메인 루프 블로킹 방지

8. **BR-08** 청산 카드 S3 경로
   - 형식: `liquidation-cards/{userId}/{uuid}.png`
   - 기존 배틀 카드 경로 `battle-cards/{battleId}/{uuid}.png`와 분리

---

## 4. 사용자 & 권한

| 역할 | JWT 인증 | 접근 가능 리소스 |
|------|----------|-----------------|
| `GUEST` (비인증) | 불필요 | PVP 랭킹 조회 (선택적 공개 허용 가능) |
| `USER` | 필요 | 내 PVP 랭킹 조회, STOMP 청산 카드 알림 수신 |
| 시스템 | — | 승률 갱신, 청산 카드 생성·발송 |

---

## 5. 주요 시나리오

### 기능 1: PVP 승률 랭킹

#### Happy Path — 배틀 종료 시 승률 갱신

1. 배틀 시간 만료 → `BattleEndService.processExpiredBattles()` 호출
2. `finishBattleInTransaction()` 내 승자 확정 → `BattleFinishedEvent` 발행
3. `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`: 승자 `updatePvpWinRate(winnerId, isWin=true)`, 나머지 참가자 `updatePvpWinRate(userId, isWin=false)` 순차 호출
4. `pvp:stats:{userId}` HINCRBY, `leaderboard:pvp-winrate` ZADD 갱신
5. 프론트엔드 PVP 탭 클릭 → `GET /api/rankings?type=pvp` 호출 → Redis Sorted Set reverseRange 반환

#### Happy Path — PVP 랭킹 조회

1. 클라이언트 `GET /api/rankings?type=pvp&limit=100` 요청
2. `RankingController` → `RankingService.getTopPvpRankings()` → `leaderboard:pvp-winrate` reverseRangeWithScores
3. score(= winRate × 100) → `evaluatedValue: Long` 변환 반환
4. 프론트엔드: `evaluatedValue / 100` 로 계산해 "75.5%" 형태로 표시

#### 예외 시나리오

| 시나리오 | 처리 방식 |
|----------|-----------|
| winnerId = null (무승부) | 전원 승률 갱신 스킵 |
| Redis 연결 실패 | @Async 내 예외 → 로깅 후 종료, 배틀 종료에 영향 없음 |
| 이미 랭킹 없는 유저 조회 | `pvp` slot rank = null, evaluatedValue = 0 반환 |

### 기능 2: 청산 밈 카드

#### Happy Path

1. `LiquidationScheduler.checkLiquidations()` — `shouldLiquidate = true` 조건 충족
2. `orderService.forceClose(positionId, liquidationPrice)` 완료 (포지션 CLOSED)
3. 기존 `LiquidationNotificationMessage` STOMP 발송 (현재 동작 유지)
4. `BattleCardPipelineService.generateAndBroadcastLiquidationCard(userId, ticker, lossAmount, leverage)` 비동기 실행
5. `BattleCardImageService.generateLiquidationCard(...)` → 💀 이미지 ByteArray 생성
6. S3 업로드 → URL 획득
7. `messagingTemplate.convertAndSendToUser(userId, "/queue/notification", LiquidationCardReadyMessage(...))` 발송
8. 프론트엔드: STOMP 수신 → 카드 이미지 표시/공유 유도

#### 예외 시나리오

| 시나리오 | 처리 방식 |
|----------|-----------|
| 이미지 생성 중 예외 | catch → log.error, 청산 알림은 이미 발송 완료 |
| S3 업로드 실패 | catch → log.error, STOMP 미발송 |
| forceClose 실패 | 카드 파이프라인 미진입 (기존 동작 동일) |

---

## 6. 비기능 요구사항

### 기능 1: PVP 승률 랭킹

- **성능**: Redis Sorted Set reverseRangeWithScores O(log N + K) — 100건 기준 < 5ms 목표
- **동시성**: `updatePvpWinRate` 는 Redis HINCRBY (원자 연산) 사용 — 별도 분산 락 불필요. DB 레벨 보호는 `finishBattleInTransaction` @Transactional로 충분
- **Redis 캐시**: `pvp:stats:{userId}` — TTL 없음 (영구), `leaderboard:pvp-winrate` — TTL 없음 (시즌 초기화 스케줄 별도 고려)
- **비동기 팬아웃**: `BattleFinishedEvent` 핸들러에서 `@Async` + `@TransactionalEventListener(AFTER_COMMIT)` 패턴 적용

### 기능 2: 청산 밈 카드

- **성능**: 이미지 생성 + S3 업로드 ≈ 500ms~2s 예상 — 비동기 실행 필수 (메인 루프 블로킹 금지)
- **동시성**: 청산 카드 생성은 forceClose 이후 독립 코루틴으로 실행 — 카드 실패가 청산 상태에 영향 없음
- **실시간**: STOMP `/user/{userId}/queue/notification` 개인 큐 — 기존 `LiquidationNotificationMessage` 발송 직후 순차 발송
- **외부 연동**: S3 업로드 (기존 `S3StorageService` 재사용)
- **비동기 팬아웃**: `CoroutineScope(Dispatchers.IO).launch { }` 내에서 카드 파이프라인 호출

---

## 7. 미결 사항 (TBD)

- [ ] PVP 랭킹의 최소 게임 수 필터 여부 — 1판만 해도 100% 승률이 상단 노출됨. 예: total ≥ 5 미만은 랭킹 미등재 옵션
- [ ] `leaderboard:pvp-winrate` 시즌 초기화 주기 (현재 `leaderboard:daily` 는 `resetDailyRanking()` 존재)
- [ ] 청산 카드 프론트엔드 UI — `LIQUIDATION_CARD_READY` 수신 후 표시 방식 (팝업 / 하단 토스트 / 전용 모달)
- [ ] 밈 텍스트 목록 — 고정 문구 1개 vs 랜덤 선택 (예: "💀 청산당했습니다", "🔥 레버리지의 맛", "RIP")
- [ ] PVP 내 순위(MyRanking)에 pvp slot 추가 여부 — 현재 `MyRankingResponse`는 season/daily 슬롯만 보유

# 도메인 모델 — PVP 승률 랭킹 + 청산 밈 카드 생성

> 기반: docs/requirements-pvp-ranking-liquidation-card.md
> 작성일: 2026-05-17

---

## 1. 유비쿼터스 언어 (Ubiquitous Language)

| 용어 | 정의 |
|------|------|
| PVP 승률 (PvpWinRate) | 전체 PVP 배틀 중 승리한 비율 (wins / total × 100) |
| 승률 점수 (WinRateScore) | Sorted Set score = winRate × 100 (Long, 예: 75.5% → 7550) |
| 청산 밈 카드 (LiquidationCard) | 강제청산 발생 시 자동 생성되는 💀 이미지 카드 |
| 카드 파이프라인 (CardPipeline) | 이미지 생성 → S3 업로드 → STOMP 발송의 일련 흐름 |
| PVP 통계 (PvpStats) | 유저별 PVP 승/패/총 게임 수 집계 (Redis Hash) |

---

## 2. 바운디드 컨텍스트

```
┌─────────────────────────────┐   ┌──────────────────────────────┐
│       Ranking Context       │   │       Card Context           │
│                             │   │                              │
│  PvpStats (Redis Hash)      │   │  BattleCardImageService      │
│  leaderboard:pvp-winrate    │   │  BattleCardPipelineService   │
│  RankingService             │   │  LiquidationScheduler        │
│  RankingController          │   │                              │
└─────────────────────────────┘   └──────────────────────────────┘
             ▲                                  ▲
             │ BattleFinishedEvent              │ forceClose 완료
             │                                  │
┌────────────┴─────────────────────────────────┴─────────────────┐
│                      Battle Context                             │
│  BattleEndService · BattleFinishedEventListener                 │
│  LiquidationScheduler (order 도메인)                            │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. 애그리거트

### Aggregate: Ranking (PVP 확장)

#### 책임
유저별 PVP 전적을 원자적으로 누적하고, 승률 기반 Sorted Set 순위를 일관성 있게 유지한다.

#### 애그리거트 루트
`RankingService` (도메인 서비스, JPA 엔티티 없음 — Redis 전용)

#### 값 객체

| 구분 | 이름 | 핵심 속성 | 설명 |
|------|------|-----------|------|
| VO | `PvpStats` | wins: Long, total: Long | Redis Hash `pvp:stats:{userId}` 조회 결과 표현 |
| VO | `WinRateScore` | score: Double | wins / total × 100, Sorted Set에 저장되는 점수 |

#### 비즈니스 불변식 (Invariants)

- **INV-01**: total = 0인 유저는 `leaderboard:pvp-winrate`에 존재하지 않는다
  - 위반 시: 첫 배틀 완료 전 조회 → 랭킹 미집계 응답 (rank = null)
- **INV-02**: `leaderboard:pvp-winrate` score = wins / total × 100 (항상 최신 전적 반영)
  - 위반 시: HINCRBY 후 ZADD 미실행 — 두 연산을 한 메서드에서 순서 보장
- **INV-03**: winnerId = null인 배틀의 경우 모든 참가자 승률 갱신 스킵
  - 위반 시: 무처리, 로깅 없음

#### 라이프사이클 (Redis 키 기준)

```
[최초 배틀 참가]
  → pvp:stats:{userId} 미존재
  → 배틀 종료 후 첫 HINCRBY → 키 자동 생성, total=1
  → total ≥ 1 이면 ZADD로 leaderboard:pvp-winrate 등재
```

#### 트랜잭션 경계

Redis 연산 전용 — JPA 트랜잭션 외부에서 실행
(`@TransactionalEventListener(AFTER_COMMIT) + @Async` 보장)

#### 동시성 고려사항

| 단계 | 방식 | 적용 여부 | 이유 |
|------|------|-----------|------|
| 1 | Redisson 분산 락 | N | Redis HINCRBY 자체가 원자 연산 |
| 2 | 낙관적 락 (@Version) | N | DB 엔티티 없음 |
| 3 | 멱등성 키 | N | 이벤트 중복은 `battle.finish()` DB 수준에서 차단 |

#### 도메인 이벤트

- `BattleFinishedEvent` (기존): `BattleEndService` 발행
  - 구독: `BattleFinishedEventListener.onBattleFinished()` → `rankingService.updatePvpWinRate()` 추가
  - 처리 방식: `@TransactionalEventListener(AFTER_COMMIT) + @Async` (기존 패턴 유지)

---

### Aggregate: LiquidationCard

#### 책임
강제청산 완료 후 카드 이미지를 비동기로 생성·업로드하며, 청산 메인 흐름에 영향을 주지 않는다.

#### 애그리거트 루트
`BattleCardPipelineService` (도메인 서비스, JPA 엔티티 없음)

#### 값 객체

| 구분 | 이름 | 핵심 속성 | 설명 |
|------|------|-----------|------|
| VO | `LiquidationCardContext` | userId, ticker, lossAmount, leverage | 카드 생성에 필요한 컨텍스트 집약 |

#### 비즈니스 불변식

- **INV-04**: 카드 생성 실패는 청산 결과를 롤백하지 않는다
  - 위반 시: 예외 catch → log.error → 종료, 청산은 이미 `forceClose` 완료
- **INV-05**: 카드 파이프라인은 forceClose 성공 후에만 진입한다
  - 위반 시: forceClose 예외 시 catch 블록에서 미진입 (기존 `onFailure` 패턴 유지)
- **INV-06**: S3 경로는 `liquidation-cards/{userId}/{uuid}.png` 형식이다
  - 위반 시: 기존 `battle-cards/` 경로와 충돌하여 덮어쓰기 위험

#### 라이프사이클

```
[LiquidationScheduler.checkLiquidations()]
  forceClose 성공
    → 기존 LiquidationNotificationMessage STOMP 발송 (동기)
    → scope.launch { generateAndBroadcastLiquidationCard() }  ← 신규 (비동기)
        generateLiquidationCard() → ByteArray
        s3StorageService.upload()  → url
        convertAndSendToUser() → LIQUIDATION_CARD_READY
```

#### 트랜잭션 경계

트랜잭션 없음 — Coroutine IO 스레드에서 순수 I/O 연산

#### 동시성 고려사항

| 단계 | 방식 | 적용 여부 | 이유 |
|------|------|-----------|------|
| 1 | Redisson 분산 락 | N | 카드 생성은 읽기+I/O만, 공유 자원 변경 없음 |
| 2 | 낙관적 락 | N | DB 엔티티 없음 |
| 3 | 멱등성 키 | N | 동일 포지션 중복 청산은 `forceClose` 레벨에서 차단 |

---

## 4. 애그리거트 관계도

```
Battle (기존)
  └─ BattleFinishedEvent ──→ BattleFinishedEventListener
                                  ├──→ Ranking.updatePvpWinRate()  [신규]
                                  ├──→ STOMP /topic/battle/{id}
                                  └──→ BattleCardPipeline.generateAndBroadcastCard()

Position (기존)
  └─ forceClose() ──→ LiquidationScheduler
                          ├──→ STOMP LiquidationNotificationMessage  [기존]
                          └──→ BattleCardPipeline.generateAndBroadcastLiquidationCard()  [신규]
```

---

## 5. 도메인 이벤트

| 이벤트명 | 발행 주체 | 발행 시점 | 구독 주체 | 처리 내용 |
|----------|-----------|-----------|-----------|-----------|
| `BattleFinishedEvent` (기존) | `BattleEndService` | 배틀 DB 커밋 직후 | `BattleFinishedEventListener` | STOMP 브로드캐스트, 결과 카드 생성 **+ PVP 승률 갱신 [신규]** |

> 청산 카드는 이벤트 방식이 아닌 `LiquidationScheduler` 내 코루틴 직접 호출로 트리거됨.
> (이벤트 발행 없음 — 청산 루프의 성격상 즉시 비동기 분기가 더 단순)

---

## 6. 도메인 서비스

### RankingService (확장)

- **책임**: PVP 전적 갱신 + PVP 랭킹 조회 추가
- **관여 애그리거트**: Ranking, (Battle ID·User ID 참조)
- **로직 요약**:
  ```
  updatePvpWinRate(userId, isWin):
    1. HINCRBY pvp:stats:{userId} total 1
    2. if isWin: HINCRBY pvp:stats:{userId} wins 1
    3. HGET pvp:stats:{userId} → wins, total
    4. ZADD leaderboard:pvp-winrate (wins/total×100) userId

  getTopPvpRankings(limit):
    1. ZREVRANGEWITHSCORES leaderboard:pvp-winrate 0 limit-1
    2. userId 목록으로 User 닉네임 일괄 조회
    3. RankingEntryResponse(evaluatedValue = score.toLong()) 반환
  ```
- **트랜잭션 전략**: Redis 연산 전용, 트랜잭션 없음

### BattleCardPipelineService (확장)

- **책임**: 청산 카드 생성 파이프라인 추가 오버로드
- **관여 애그리거트**: LiquidationCard (Position userId, ticker, lossAmount, leverage)
- **로직 요약**:
  ```
  generateAndBroadcastLiquidationCard(userId, ticker, lossAmount, leverage):
    1. battleCardImageService.generateLiquidationCard(ticker, lossAmount, leverage)
    2. s3StorageService.upload("liquidation-cards/{userId}/{uuid}.png", bytes, "image/png")
    3. convertAndSendToUser(userId, "/queue/notification", LiquidationCardReadyMessage)
  ```
- **트랜잭션 전략**: 없음, Coroutine IO 컨텍스트

---

## 7. 크로스-애그리거트 상호작용

| 상황 | 관여 컨텍스트 | 일관성 전략 | 이유 |
|------|--------------|-------------|------|
| 배틀 종료 → PVP 승률 갱신 | Battle → Ranking | 최종 일관성 (AFTER_COMMIT + @Async) | 승률은 배틀 결과와 동시성 없음, 잠깐의 지연 허용 |
| 청산 → 카드 생성 | Order → Card | 최종 일관성 (Coroutine launch) | 카드 실패가 청산 상태에 영향 없어야 함 |

---

## 8. 레포지토리 인터페이스

신규 JPA Repository 없음. Redis 키 패턴만 추가:

```kotlin
// RankingService 내 Redis 키 상수 추가
const val PVP_WINRATE_KEY = "leaderboard:pvp-winrate"
const val PVP_STATS_KEY_PREFIX = "pvp:stats:"  // + userId

// 조회 패턴 (StringRedisTemplate 직접 사용)
opsForHash<String, String>().increment("pvp:stats:{userId}", "total", 1)
opsForHash<String, String>().increment("pvp:stats:{userId}", "wins", 1)
opsForZSet().reverseRangeWithScores("leaderboard:pvp-winrate", 0, limit - 1)
```

---

## 9. 패키지 구조 제안

```
com.coinbattle
└── domain/
    ├── ranking/
    │   ├── service/
    │   │   └── RankingService.kt          # updatePvpWinRate(), getTopPvpRankings() 추가
    │   ├── dto/response/
    │   │   └── RankingEntryResponse.kt    # 재사용 (evaluatedValue = winRate×100 Long)
    │   └── controller/
    │       └── RankingController.kt       # GET /api/rankings?type=pvp 분기 추가
    └── battle/
        ├── event/
    │   │   └── BattleFinishedEventListener.kt  # updatePvpWinRate() 호출 추가
        ├── service/
        │   ├── BattleCardImageService.kt        # generateLiquidationCard() 추가
        │   └── BattleCardPipelineService.kt     # generateAndBroadcastLiquidationCard() 추가
        └── dto/response/
            └── LiquidationCardReadyMessage.kt   # 신규 (order 도메인 dto에 추가 가능)
```

---

## 10. 설계 결정 사항 (ADR)

### ADR-01: PVP 승률 응답에 RankingEntryResponse 재사용
- **결정**: 신규 DTO 없이 `evaluatedValue: Long = Math.round(winRate × 100)` 방식으로 재사용
- **이유**: 프론트엔드 공통 랭킹 목록 컴포넌트 재사용 가능, 백엔드 DTO 수 최소화
- **trade-off**: `evaluatedValue` 필드 의미가 탭에 따라 다름 (금액 vs 승률×100) — 프론트엔드에서 탭별 렌더링 분기 필요

### ADR-02: PVP 승률 갱신은 BattleFinishedEventListener에 추가
- **결정**: 별도 EventListener 클래스 생성 없이 기존 `BattleFinishedEventListener.onBattleFinished()`에 `rankingService.updatePvpWinRate()` 추가
- **이유**: 이미 `@Async + @TransactionalEventListener(AFTER_COMMIT)` 패턴이 적용된 올바른 위치
- **trade-off**: Listener가 STOMP 브로드캐스트 + 카드 생성 + 승률 갱신을 모두 담당 — 단일 책임 원칙 다소 희석, 향후 EventListener 분리 고려

### ADR-03: 청산 카드는 Spring ApplicationEvent 없이 직접 코루틴 분기
- **결정**: `LiquidationScheduler` 내부에서 `scope.launch { pipeline.generateAndBroadcastLiquidationCard() }` 직접 호출
- **이유**: `LiquidationScheduler`는 이미 `CoroutineScope`를 보유, 이벤트 발행보다 단순
- **trade-off**: 카드 생성 실패 시 ApplicationEvent 재시도 패턴 적용 불가 — 실패 시 로깅 후 드롭 (MVP 수준 허용)

### ADR-04: GET /api/rankings?type= 통합 엔드포인트
- **결정**: 기존 `/api/ranking/season`, `/api/ranking/daily`는 유지하고, **신규 통합 엔드포인트** `GET /api/rankings?type=season|daily|pvp` 추가
- **이유**: 기존 API 하위 호환성 유지, 프론트엔드에서 단일 hook으로 타입 분기 가능
- **trade-off**: 두 가지 URL 패턴 공존 — 추후 `/api/ranking/pvp` 별도 endpoint로 통일하거나 기존 경로 deprecated 처리 고려

---

## 11. 아키텍처 위험 요소

- **HINCRBY → ZADD 비원자성**: 두 Redis 명령 사이 서버 재시작 시 stats는 갱신됐으나 Sorted Set은 미갱신 상태 가능. MVP 단계 허용 (재시작 후 다음 배틀에서 자연 수렴)
- **PVP Sorted Set 무한 누적**: 시즌 초기화 로직이 없으면 탈퇴 유저 포함 영구 누적 — TBD로 분류
- **BattleFinishedEventListener 단일 장애점**: STOMP + 카드 생성 + 승률 갱신이 한 메서드에 집중 — 중간 예외 발생 시 이후 동작 스킵 위험. 각 동작을 독립 try-catch로 감싸야 함
- **청산 카드 동시 다발**: 시장 급락 시 수십 건 청산이 동시 발생 → 다수 코루틴 동시 S3 업로드 가능 → S3 rate limit 또는 메모리 압박 가능성. MVP는 허용, 이후 Queue 처리 고려

---

## 12. TBD

- [ ] PVP 랭킹 최소 게임 수 필터 (total < N 미등재) — N 기준 미결
- [ ] `leaderboard:pvp-winrate` 시즌/일별 초기화 주기
- [ ] `MyRankingResponse`에 pvp 슬롯 추가 여부
- [ ] 밈 텍스트 목록 — 고정 1개 vs 랜덤 선택
- [ ] 청산 카드 수신 후 프론트엔드 표시 방식 (토스트/팝업/모달)

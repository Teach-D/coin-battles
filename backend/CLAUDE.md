# CoinBattle Backend — CLAUDE.md

> 전체 프로젝트 개요, 아키텍처 흐름, 설계 결정은 루트 `../CLAUDE.md`를 참조하라.

## 빌드 및 실행 (Gradle Kotlin DSL)

```bash
./gradlew build                          # 컴파일 + 테스트 포함 전체 빌드
./gradlew bootRun                        # 로컬 실행 (application.yml 기본 프로필)
./gradlew bootRun --args='--spring.profiles.active=local'
./gradlew test                           # 단위 테스트만 (Docker 불필요, 빠름)
./gradlew test --tests "*.OrderServiceTest"                    # 특정 클래스만
./gradlew test --tests "*.OrderServiceTest.주문금액_100만원_초과시_슬리피지_적용"
./gradlew integrationTest                # 통합 테스트 (Testcontainers, Docker 필요)
./gradlew ktlintCheck                    # 린트 검사
./gradlew ktlintFormat                   # 린트 자동 수정
```

## 패키지 구조

```
src/main/kotlin/com/coinbattle/
├── CoinbattleApplication.kt
├── common/
│   ├── config/
│   │   ├── AsyncConfig.kt
│   │   ├── EncryptionConfig.kt
│   │   ├── JwtAuthenticationFilter.kt
│   │   ├── RedisConfig.kt
│   │   ├── SecurityConfig.kt
│   │   ├── WebClientConfig.kt
│   │   └── WebSocketConfig.kt
│   ├── dto/ApiResponse.kt
│   ├── exception/
│   │   ├── CoinBattleException.kt
│   │   ├── ErrorCode.kt
│   │   └── GlobalExceptionHandler.kt
│   └── util/
│       ├── AesEncryptor.kt
│       └── JwtProvider.kt
└── domain/
    ├── user/
    │   ├── entity/
    │   │   ├── User.kt                    # @Version 낙관적 락, balance 10,000,000 초기값
    │   │   ├── CoinBattlePrincipal.kt
    │   │   └── EmailEncryptConverter.kt
    │   ├── repository/UserRepository.kt
    │   ├── service/
    │   │   ├── UserService.kt
    │   │   ├── OAuth2UserService.kt
    │   │   └── OAuth2LoginSuccessHandler.kt
    │   └── controller/AuthController.kt
    ├── market/
    │   ├── client/UpbitWebSocketClient.kt
    │   ├── repository/TickerRedisRepository.kt
    │   ├── service/
    │   │   ├── MarketService.kt
    │   │   ├── CandleService.kt
    │   │   ├── TickerPubSubPublisher.kt
    │   │   └── TickerPubSubSubscriber.kt
    │   └── controller/MarketController.kt
    ├── order/
    │   ├── entity/
    │   │   ├── Order.kt                   # idempotencyKey (UNIQUE), executedPrice 포함
    │   │   └── Position.kt                # @Version, liquidationThreshold() 내장
    │   ├── event/
    │   │   ├── OrderFilledEvent.kt
    │   │   └── OrderFilledEventListener.kt
    │   ├── repository/
    │   │   ├── OrderRepository.kt
    │   │   └── PositionRepository.kt
    │   ├── service/
    │   │   ├── OrderService.kt
    │   │   └── LiquidationScheduler.kt    # Coroutine 1초 주기 청산 모니터링
    │   └── controller/OrderController.kt
    ├── battle/
    │   ├── entity/
    │   │   ├── Battle.kt
    │   │   └── BattleSession.kt
    │   ├── enum/BattleStatus.kt           # WAITING/IN_PROGRESS/FINISHED
    │   ├── event/BattleCreatedEvent.kt
    │   ├── repository/
    │   │   ├── BattleRepository.kt
    │   │   └── BattleSessionRepository.kt
    │   ├── service/
    │   │   ├── BattleService.kt
    │   │   └── BattleMatchingService.kt   # 랜덤 매칭 큐 처리
    │   └── controller/BattleController.kt
    └── ranking/
        ├── scheduler/RankingScheduler.kt
        ├── service/RankingService.kt
        └── controller/RankingController.kt
```

DB 마이그레이션 파일 (`resources/db/migration/`):
- `V1__init_schema.sql` — users 테이블
- `V2__order_position_schema.sql` — orders, positions 테이블 + 커버링 인덱스
- `V3__battle_schema.sql` — battles, battle_sessions 테이블

## 클래스명 규칙

| 역할 | 규칙 | 예시 |
|------|------|------|
| 엔티티 (JPA) | `{Domain}` | `Order`, `Position`, `User` |
| Enum | `{Domain}{속성}` | `OrderStatus`, `PositionSide` |
| Repository | `{Domain}Repository` | `OrderRepository` |
| Service | `{Domain}Service` | `OrderService` |
| Controller | `{Domain}Controller` | `OrderController` |
| WebSocket Client | `{거래소}WebSocketClient` | `UpbitWebSocketClient` |
| Scheduler | `{Domain}Scheduler` | `LiquidationScheduler` |
| Request DTO | `{동사}{Domain}Request` | `PlaceOrderRequest` |
| Response DTO | `{Domain}Response` | `OrderResponse` |
| 내부 커맨드 | `{동사}{Domain}Command` | `PlaceOrderCommand` |
| 이벤트 | `{Domain}{과거형}Event` | `OrderFilledEvent` |
| 예외 | `{이유}Exception` | `InsufficientBalanceException` |
| 에러코드 | enum `ErrorCode` | `ErrorCode.INSUFFICIENT_BALANCE` |
| Config | `{영역}Config` | `RedisConfig` |
| 유틸 | `{Domain}{기능}` | `SlippageCalculator`, `JwtProvider` |

Service 인터페이스 없음: 구현체가 하나인 경우 클래스 직접 사용.

## Redis 키 네이밍 규칙

| 키 | 용도 | TTL |
|---|---|---|
| `coin:price:{ticker}` | 업비트 시세 캐시 | 3초 |
| `user:{id}:order` | Redisson 분산 락 | 3초 |
| `order:idempotency:{key}` | 중복 주문 방지 | 주문 TTL |
| `leaderboard:season` | 시즌 랭킹 Sorted Set | 영구 |
| `leaderboard:daily` | 일별 랭킹 Sorted Set | 하루 |
| `battle:{id}:snapshot` | 배틀 기준가 스냅샷 | 배틀 종료 후 TTL |
| `battle:invite:{inviteCode}` | 초대 코드 → battleId 매핑 | 10분 |

## 동시성 3단계 방어

1. Redisson 분산 락: `user:{id}:order`, TTL 3초 — 락 획득 실패 시 즉시 에러, 재시도 없음
2. 낙관적 락: `@Version` on User, Position — `OptimisticLockingFailureException` 시 에러 반환
3. 멱등성 키: 클라이언트 UUID → orders.idempotency_key (UNIQUE 제약)

## 핵심 구현 패턴

### 비동기 팬아웃 — 주문 체결 후

```kotlin
applicationEventPublisher.publishEvent(OrderFilledEvent(orderId))

// @Async + @TransactionalEventListener(phase = AFTER_COMMIT)
// 팬아웃: 체결기록 저장 / 랭킹 갱신 / 알림 / 카드 생성
```

### 강제청산 모니터링

- Kotlin `CoroutineScheduler` 1초 주기
- 청산 기준: `-(1 / leverage) × 0.9` (10x → -9%)
- 청산 발생 시 동일한 주문 흐름으로 강제 매도 (별도 경로 없음)

### STOMP 토픽

```
/topic/coin/{ticker}             # 시세 브로드캐스트
/topic/battle/{battleId}         # 배틀 실시간 현황
/user/queue/notification         # 개인 알림
```


## 테스트 전략

### 원칙

- **TDD 필수**: 구현 전 테스트 작성, Red → Green 순서 준수
- **통합 테스트**: 실제 PostgreSQL + Redis (Testcontainers) — DB mock 금지
- **단위 테스트**: 순수 도메인 로직만 (MockK 사용, Mockito 사용 금지)

### 테스트 소스셋 분리 — 에이전트 필수 준수

| 소스셋 | 경로 | Gradle 태스크 | Docker | 속도 |
|--------|------|--------------|--------|------|
| 단위 테스트 | `src/test/kotlin/` | `./gradlew test` | 불필요 | 빠름 |
| 통합 테스트 | `src/integrationTest/kotlin/` | `./gradlew integrationTest` | 필요 | 느림 |

**파일 배치 규칙**
- `@SpringBootTest` + `@Testcontainers` 사용 → `src/integrationTest/kotlin/` 에 작성
- MockK만 사용하는 순수 단위 테스트 → `src/test/kotlin/` 에 작성

### TDD Red→Green 사이클 — 에이전트 실행 순서

**반복 사이클 (빠른 피드백):**
```bash
./gradlew test --tests "*.{작성한테스트클래스명}"   # 해당 단위 테스트만 실행
```

**단위 테스트 전체 통과 후:**
```bash
./gradlew test   # 전체 단위 테스트 확인
```

**구현 완료 후 최종 1회만:**
```bash
./gradlew integrationTest   # Testcontainers 통합 테스트
```

> Testcontainers 컨테이너 기동 비용(10~30초)을 마지막 1회로 제한한다.
> Red→Green 사이클 중간에 `integrationTest` 태스크를 실행하지 않는다.

### 테스트 파일 위치

- 구현: `src/main/kotlin/com/coinbattle/{domain}/{Class}.kt`
- 단위 테스트: `src/test/kotlin/com/coinbattle/{domain}/{Class}Test.kt`
- 통합 테스트: `src/integrationTest/kotlin/com/coinbattle/{domain}/{Class}Test.kt`

### 단위 테스트 대상

- `OrderService` — 슬리피지 계산, 잔고 검증, 멱등성 키 중복 처리
- `Position.liquidationThreshold()` — 레버리지별 청산 임계가 계산
- `RankingService` — Redis Sorted Set 점수 계산 로직
- `BattleService` — 매칭 로직, 배틀 종료 조건

### 통합 테스트 대상

- `OrderService` — 분산 락 + 낙관적 락 동시성 시나리오
- `AuthController` — 회원가입/로그인 전체 흐름
- `MarketService` — Redis 시세 캐시 저장/조회

## 주요 설정값

- 초기 배틀 자본금: 10,000,000원
- JWT access 만료: 1시간 (3600000ms)
- JWT refresh 만료: 7일 (604800000ms)
- 펀딩비 주기: 8시간 (00:00/08:00/16:00 UTC)

## 코딩 규칙

- 주석 없음 — 코드로 의도 표현
- Kotlin 관용 문법 (data class, sealed class, scope function)
- suspend fun / Flow 적극 활용
- 비즈니스 로직은 Entity 또는 Service에 집중, Controller는 얇게 유지

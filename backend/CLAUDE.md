# CoinBattle Backend — CLAUDE.md

> 전체 프로젝트 개요, 아키텍처 흐름, 설계 결정은 루트 `../CLAUDE.md`를 참조하라.

## 빌드 및 실행 (Gradle Kotlin DSL)

```bash
./gradlew build                          # 컴파일 + 테스트 포함 전체 빌드
./gradlew bootRun                        # 로컬 실행 (application.yml 기본 프로필)
./gradlew bootRun --args='--spring.profiles.active=local'
./gradlew test                           # 전체 테스트
./gradlew test --tests "com.coinbattle.order.OrderServiceTest"
./gradlew test --tests "*.OrderServiceTest.주문금액_100만원_초과시_슬리피지_적용"
./gradlew ktlintCheck                    # 린트 검사
./gradlew ktlintFormat                   # 린트 자동 수정
```

## 패키지 구조

```
src/main/kotlin/com/coinbattle/
├── CoinBattleApplication.kt
├── common/
│   ├── config/
│   │   ├── AsyncConfig.kt
│   │   ├── RedisConfig.kt
│   │   ├── SecurityConfig.kt
│   │   └── WebSocketConfig.kt
│   ├── exception/
│   │   ├── CoinBattleException.kt
│   │   ├── ErrorCode.kt
│   │   └── GlobalExceptionHandler.kt
│   └── util/
│       └── JwtProvider.kt
└── domain/
    ├── user/
    │   ├── entity/User.kt              # @Version 낙관적 락, balance 10,000,000 초기값
    │   ├── repository/UserRepository.kt
    │   ├── service/UserService.kt
    │   └── controller/AuthController.kt
    ├── market/
    │   ├── client/UpbitWebSocketClient.kt
    │   ├── client/BinanceWebSocketClient.kt
    │   ├── service/MarketService.kt
    │   └── controller/MarketController.kt
    ├── order/
    │   ├── entity/Order.kt             # idempotencyKey (UNIQUE), executedPrice 포함
    │   ├── entity/Position.kt          # @Version, liquidationThreshold() 내장
    │   ├── repository/OrderRepository.kt
    │   ├── repository/PositionRepository.kt
    │   ├── service/OrderService.kt
    │   └── controller/OrderController.kt
    ├── battle/
    │   ├── entity/Battle.kt            # BattleStatus: WAITING/IN_PROGRESS/FINISHED
    │   ├── repository/BattleRepository.kt
    │   ├── service/BattleService.kt
    │   └── controller/BattleController.kt
    ├── ranking/
    │   ├── service/RankingService.kt
    │   └── controller/RankingController.kt
    ├── card/
    │   └── service/CardService.kt
    └── notification/
        └── service/NotificationService.kt
```

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

## DB 마이그레이션

`resources/db/migration/V1__init_schema.sql`
- users, battles, positions, orders 테이블
- 커버링 인덱스: idx_positions_user_ticker_status, idx_orders_user_created 등

## Redis 키 네이밍 규칙

| 키 | 용도 | TTL |
|---|---|---|
| `coin:price:{ticker}` | 업비트 시세 캐시 | 3초 |
| `user:{id}:order` | Redisson 분산 락 | 3초 |
| `order:idempotency:{key}` | 중복 주문 방지 | 주문 TTL |
| `leaderboard:season` | 시즌 랭킹 Sorted Set | 영구 |
| `leaderboard:daily` | 일별 랭킹 Sorted Set | 하루 |
| `battle:{id}:snapshot` | 배틀 기준가 스냅샷 | 배틀 종료 후 TTL |

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

## 슬리피지 계산

`OrderService` 내부에서만 계산 — 컨트롤러·레포지토리 참조 금지:
- ≤100만원: 보정 없음
- 100만~500만원: ±0.05%
- 500만원~전액: ±0.1~0.3% (SecureRandom)

## 강제청산 계산식

```kotlin
청산 임계가 = avgEntryPrice × (1 - 1/leverage × 0.9)
// Position.liquidationThreshold() 참조
```

## 펀딩비 정산

- `@Scheduled(cron = "0 0 0,8,16 * * *", zone = "UTC")` + `@Async`
- 전체 오픈 포지션 배치 처리 — 부분 실패 시 로깅 후 계속 진행

## 업비트 장애 Fallback

Circuit Breaker (Resilience4j) — 업비트 WebSocket 실패 시 빗썸 REST API 폴링 자동 전환.

## 테스트 전략

### 원칙

- **TDD 필수**: 구현 전 테스트 작성, Red → Green 순서 준수
- **통합 테스트**: 실제 PostgreSQL + Redis (Testcontainers) — DB mock 금지
- **단위 테스트**: 순수 도메인 로직만 (MockK 사용, Mockito 사용 금지)

### 의존성

- 단위 테스트 목킹: `io.mockk:mockk:1.13.13`
- 통합 테스트: `org.testcontainers:postgresql`, `org.testcontainers:redis`
- 비동기 테스트: `@SpringBootTest` + `CompletableFuture.get()` 완료 대기
- Coroutine 테스트: `kotlinx-coroutines-test` + `runTest { }`

### 테스트 파일 위치

- 구현: `src/main/kotlin/com/coinbattle/{domain}/{Class}.kt`
- 테스트: `src/test/kotlin/com/coinbattle/{domain}/{Class}Test.kt`

### 단위 테스트 패턴 (MockK)

```kotlin
@ExtendWith(MockKExtension::class)
class OrderServiceTest {
    @MockK lateinit var orderRepository: OrderRepository
    @InjectMockKs lateinit var orderService: OrderService

    @Test
    fun `주문금액_100만원_초과시_슬리피지_적용`() {
        every { orderRepository.save(any()) } answers { firstArg() }
        // when / then
    }
}
```

### 통합 테스트 패턴 (Testcontainers)

```kotlin
@SpringBootTest
@Testcontainers
class OrderIntegrationTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16")
        @Container @JvmStatic
        val redis = GenericContainer<Nothing>("redis:7-alpine").withExposedPorts(6379)
    }

    @DynamicPropertySource
    fun configureProperties(registry: DynamicPropertyRegistry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl)
        registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
    }
}
```

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

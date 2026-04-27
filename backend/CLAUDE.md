# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> 전체 프로젝트 개요, 아키텍처 흐름, 설계 결정은 루트 `../CLAUDE.md`를 참조하라.

## 빌드 및 실행 (Gradle Kotlin DSL)

```bash
./gradlew build                          # 컴파일 + 테스트 포함 전체 빌드
./gradlew bootRun                        # 로컬 실행 (application.yml 기본 프로필)
./gradlew bootRun --args='--spring.profiles.active=local'
./gradlew test                           # 전체 테스트
./gradlew test --tests "com.coinbattle.order.OrderServiceTest"  # 단일 테스트 클래스
./gradlew test --tests "*.OrderServiceTest.should_reject_duplicate_order"  # 단일 메서드
./gradlew ktlintCheck                    # 린트 검사
./gradlew ktlintFormat                   # 린트 자동 수정
```

> 스캐폴딩 완료 후 이 섹션에 실제 명령어를 채울 것.

## 패키지 구조

```
src/main/kotlin/com/coinbattle/
├── config/              # Spring 설정 (Redis, WebSocket, Security, Async, Coroutine)
├── common/              # 공통 예외(CoinBattleException, ErrorCode), ApiResponse, 유틸
└── domain/
    ├── auth/            # 회원/인증 — User 엔티티, JWT
    │   ├── controller/
    │   ├── service/
    │   ├── repository/
    │   ├── entity/
    │   └── dto/
    │       ├── request/
    │       └── response/
    ├── market/          # 시세 수신 — 업비트/바이낸스 WebSocket 클라이언트
    │   ├── client/      # UpbitWebSocketClient, BinanceWebSocketClient
    │   ├── service/
    │   └── dto/
    │       └── response/
    ├── order/           # 매수/매도 — 분산 락, 낙관적 락, 멱등성
    │   ├── controller/
    │   ├── service/
    │   ├── repository/
    │   ├── entity/
    │   ├── event/
    │   └── dto/
    │       ├── request/
    │       └── response/
    ├── position/        # 포지션 관리 — 레버리지, 숏, 강제청산
    │   ├── controller/
    │   ├── service/
    │   ├── repository/
    │   ├── entity/
    │   ├── event/
    │   └── dto/
    │       ├── request/
    │       └── response/
    ├── battle/          # PVP 배틀 — 매칭, 세션, 결과
    │   ├── controller/
    │   ├── service/
    │   ├── repository/
    │   ├── entity/
    │   ├── event/
    │   └── dto/
    │       ├── request/
    │       └── response/
    ├── ranking/         # 랭킹 — Redis Sorted Set
    │   ├── service/
    │   └── dto/
    │       └── response/
    ├── card/            # 결과 카드 생성 + S3 업로드
    │   ├── service/
    │   └── dto/
    │       └── response/
    └── notification/    # FCM + WebSocket 알림
        ├── service/
        └── dto/
            └── response/
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

## 핵심 구현 패턴

### 동시성 — 주문 처리 3단계 방어 순서
1. **Redisson 분산 락** `user:{id}:order` (TTL 3초) — 락 획득 실패 시 즉시 에러 반환, 재시도 없음
2. **낙관적 락** `@Version` on `UserBalance` — `OptimisticLockingFailureException` 발생 시 재시도 **하지 말 것**, 에러 반환
3. **멱등성 키** 클라이언트 UUID — DB `UNIQUE` 제약으로 중복 INSERT 차단

### 비동기 팬아웃 — 주문 체결 후
```kotlin
// 순서: 락 해제 → 트랜잭션 커밋 → 이벤트 발행 (같은 스레드, @TransactionalEventListener 사용)
applicationEventPublisher.publishEvent(OrderFilledEvent(orderId))

// 리스너는 @Async + @TransactionalEventListener(phase = AFTER_COMMIT)
// 팬아웃: 체결기록 저장 / 랭킹 갱신 / 알림 / 카드 생성
```

### 강제청산 모니터링
- Kotlin `CoroutineScheduler` 1초 주기
- 청산 기준: `-(1 / leverage) × 0.9` (예: 10x → -9%)
- 청산 발생 시 동일한 주문 흐름으로 강제 매도 처리 (별도 경로 없음)

### Redis 키 네이밍 규칙
```
coin:price:{ticker}              # Hash, TTL 3초 (시세 캐시)
user:{id}:order                  # 분산 락
leaderboard:season               # Sorted Set (시즌 전체)
leaderboard:daily                # Sorted Set (자정 초기화)
battle:{id}:snapshot             # Hash (매칭 시 기준가 스냅샷)
```

### WebSocket STOMP 토픽
```
/topic/coin/{ticker}             # 시세 브로드캐스트
/topic/battle/{battleId}         # 배틀 실시간 현황
/user/queue/notification         # 개인 알림
```

## 슬리피지 계산 위치

`order/` 패키지 내 체결가 보정 로직 — **OrderService 내부에서만** 계산, 컨트롤러·레포지토리에서 참조 금지:
- ≤100만원: 보정 없음
- 100만~500만원: ±0.05%
- 500만원~전액: ±0.1~0.3% (SecureRandom)

## 펀딩비 정산

- `@Scheduled(cron = "0 0 0,8,16 * * *", zone = "UTC")` + `@Async`
- 전체 오픈 포지션 배치 처리 — 한 번에 전체 처리, 부분 실패 시 로깅 후 계속 진행

## 업비트 장애 Fallback

Circuit Breaker (Resilience4j) — 업비트 WebSocket 연결 실패 시 빗썸 REST API 폴링으로 자동 전환. `market/` 패키지에서 관리.

## 테스트 전략

- **통합 테스트**: 실제 PostgreSQL + Redis (Testcontainers) — DB mock 금지
- **단위 테스트**: 순수 도메인 로직 (슬리피지 계산, 청산 기준 계산 등)
- **@Async 테스트**: `@SpringBootTest` + `CompletableFuture.get()` 로 완료 대기

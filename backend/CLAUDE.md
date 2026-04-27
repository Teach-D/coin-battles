# CoinBattle Backend — CLAUDE.md

## 프로젝트 개요

Spring Boot 3.x + Kotlin Coroutine 기반 트레이딩 배틀 게임 백엔드.
핵심 루프: 10분 PVP 한 판 → 결과 카드 자동 생성 → SNS 공유 → 바이럴 유입

## 패키지 구조

```
src/main/kotlin/com/coinbattle/
├── CoinBattleApplication.kt
├── common/
│   ├── config/
│   │   ├── AsyncConfig.kt          # @Async 스레드 풀 (taskExecutor, rankingExecutor)
│   │   ├── RedisConfig.kt          # RedisTemplate, ObjectMapper, MessageListenerContainer
│   │   ├── SecurityConfig.kt       # JWT 필터, stateless 보안 설정
│   │   └── WebSocketConfig.kt      # STOMP 엔드포인트 /ws, 브로커 /topic /queue
│   ├── exception/
│   │   ├── CoinBattleException.kt  # 도메인 예외 래퍼
│   │   ├── ErrorCode.kt            # sealed class 에러코드 목록
│   │   └── GlobalExceptionHandler.kt
│   └── util/
│       └── JwtUtil.kt              # access(1h) / refresh(7d) 토큰 생성·검증
├── domain/
│   ├── user/
│   │   ├── entity/User.kt          # @Version 낙관적 락, balance 10,000,000 초기값
│   │   ├── repository/UserRepository.kt
│   │   ├── service/UserService.kt
│   │   └── controller/AuthController.kt  # POST /api/auth/register, /api/auth/login
│   ├── market/
│   │   ├── client/UpbitWebSocketClient.kt
│   │   ├── service/MarketService.kt
│   │   └── controller/MarketController.kt  # GET /api/market/**
│   ├── order/
│   │   ├── entity/Order.kt         # idempotencyKey (UNIQUE), executedPrice 포함
│   │   ├── entity/Position.kt      # @Version, liquidationThreshold() 내장
│   │   ├── repository/OrderRepository.kt
│   │   ├── repository/PositionRepository.kt
│   │   ├── service/OrderService.kt
│   │   └── controller/OrderController.kt  # POST /api/orders/**
│   ├── battle/
│   │   ├── entity/Battle.kt        # BattleStatus: WAITING/IN_PROGRESS/FINISHED
│   │   ├── repository/BattleRepository.kt
│   │   ├── service/BattleService.kt
│   │   └── controller/BattleController.kt  # /api/battles/**
│   └── ranking/
│       ├── service/RankingService.kt
│       └── controller/RankingController.kt  # GET /api/ranking/**
└── infra/
    ├── redis/
    │   ├── RedisKeyConstants.kt    # 키 네이밍 규칙 중앙화
    │   └── RedisPubSubService.kt
    └── websocket/
        └── StompEventHandler.kt
```

## DB 마이그레이션

`resources/db/migration/V1__init_schema.sql`
- users, battles, positions, orders 테이블
- 커버링 인덱스: idx_positions_user_ticker_status, idx_orders_user_created 등

## Redis 키 네이밍 규칙

| 키 | 용도 | TTL |
|---|---|---|
| `market:price:{ticker}` | 업비트 시세 캐시 | 3초 |
| `user:order:lock:{userId}` | Redisson 분산 락 | 3초 |
| `order:idempotency:{key}` | 중복 주문 방지 | 주문 TTL |
| `leaderboard:season` | 시즌 랭킹 Sorted Set | 영구 |
| `leaderboard:daily` | 일별 랭킹 Sorted Set | 하루 |
| `battle:session:{battleId}` | 배틀 세션 상태 | 배틀 종료 후 TTL |

## 동시성 3단계 방어

1. Redisson 분산 락: `user:order:lock:{userId}`, TTL 3초
2. 낙관적 락: User.version, Position.version (@Version)
3. 멱등성 키: 클라이언트 UUID → orders.idempotency_key (UNIQUE)

## 강제청산 계산식

```kotlin
청산 임계가 = avgEntryPrice × (1 - 1/leverage × 0.9)
// Position.liquidationThreshold() 참조
```

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

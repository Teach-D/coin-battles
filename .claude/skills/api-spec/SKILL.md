---
name: api-spec
description: |
  기능 설명을 받아 API 명세서를 작성합니다.
  "API 명세 작성해줘", "이 기능 API 설계해줘", "엔드포인트 설계해줘", "API 스펙 만들어줘",
  "API 어떻게 만들어야 해?", "이 기능 어떤 API 필요해?" 등
  새 기능이나 엔드포인트 설계 요청이 오면 반드시 이 스킬을 사용하세요.
  기능 개요만 있어도, 코드 구현 전에도, 백엔드/프론트엔드 어느 쪽에서 요청하든 사용합니다.
---

## 역할

사용자가 설명한 기능을 CoinBattle 아키텍처 맥락에서 분석하고,
REST / WebSocket API 명세를 구조화된 Markdown으로 작성한다.

결과는 **채팅창에만** 출력한다. 파일을 생성하거나 수정하지 않는다.

> **requirement 스킬과의 분업**
> - requirement 스킬: 도메인 모델 / 비즈니스 규칙 / 상태 전이 (WHAT & WHY)
> - 이 스킬: HTTP 엔드포인트 / DTO / 에러 코드 (HOW)
>
> 권장 순서: `/requirement` → `/api-spec` → 백엔드/프론트엔드 구현

---

## Step 1 — 도메인 파악

기능 설명에서 아래 항목을 추출한다. 명확하지 않은 것은 합리적인 기본값으로 채운다.

- **도메인**: `auth` / `market` / `order` / `position` / `battle` / `ranking` / `card` / `notification` 중 어디?
- **호출 주체**: 클라이언트 / 서버 내부 / 스케줄러
- **인증 필요 여부**: JWT Bearer 토큰 필요?
- **실시간 여부**: 단발 REST인가, STOMP 구독/브로드캐스트가 필요한가?
- **상태 변경 여부**: 데이터를 쓰는가, 읽는가?

### 기존 URL 패턴

```
인증:   /api/auth/**
시세:   /api/market/**
주문:   /api/orders/**
포지션: /api/positions/**
배틀:   /api/battles/**
랭킹:   /api/ranking/**
카드:   /api/cards/**
알림:   /api/notifications/**

WebSocket 브로드캐스트: /topic/{domain}/{id}
WebSocket 개인 알림:   /user/queue/{domain}
```

---

## Step 2 — 엔드포인트 설계

### URL 규칙

| 패턴 | 사용 상황 |
|------|----------|
| `GET /api/{domains}` | 목록 조회 |
| `POST /api/{domains}` | 새 리소스 생성 / 액션 실행 |
| `GET /api/{domains}/{id}` | 단건 조회 |
| `PATCH /api/{domains}/{id}` | 부분 수정 |
| `PATCH /api/{domains}/{id}/status` | 상태 변경 |
| `DELETE /api/{domains}/{id}` | 삭제 / 취소 |
| `POST /api/{domains}/{id}/{subResource}` | 서브 리소스 생성 |

### HTTP 메서드

| 메서드 | 용도 |
|--------|------|
| GET | 조회, 상태 변경 없음 |
| POST | 새 리소스 생성, 액션 실행 (주문, 배틀 참가 등) |
| PATCH | 부분 수정, 상태 전이 |
| DELETE | 삭제 또는 취소 |

### 권한 태그

- `[AUTH]` — JWT Bearer 필요 (`@AuthenticationPrincipal`)
- `[ADMIN]` — 관리자 전용
- 태그 없음 — 누구나 접근 가능 (시세 조회 등)

---

## Step 3 — DTO 설계

### DTO 네이밍 규칙

| 역할 | 패턴 | 예시 |
|------|------|------|
| 요청 바디 | `{Verb}{Domain}Request` | `PlaceOrderRequest`, `JoinBattleRequest` |
| 응답 바디 | `{Domain}Response` | `OrderResponse`, `BattleResponse` |
| 상세 응답 | `{Domain}DetailResponse` | `BattleDetailResponse` |
| 내부 커맨드 | `{Verb}{Domain}Command` | `PlaceOrderCommand` |

### 필드 타입 기준

| 상황 | Kotlin 타입 |
|------|------------|
| PK / FK | `Long` |
| 금액 / 가격 | `BigDecimal` |
| 수익률 (%) | `Double` |
| 수량 / 레버리지 | `Int` |
| 문자열 | `String` |
| 불린 | `Boolean` |
| 날짜시간 | `LocalDateTime` |
| 날짜만 | `LocalDate` |
| 열거형 | 실제 enum 클래스명 (예: `OrderType`, `BattleStatus`) |
| 리스트 | `List<타입>` |

Nullable 필드는 `타입?`으로 표기하고 설명란에 `nullable` 표기.

### 페이징

- 오프셋 기반: `page`, `size` Query Param → `Page<{Domain}Response>`
- 커서 기반: `lastId`, `size` Query Param → `List<{Domain}Response>`

---

## Step 4 — 에러 코드 선택

기능과 직접 관련된 에러 코드만 포함한다.

### 공통 (항상 고려)

| 에러 코드 | HTTP | 조건 |
|-----------|------|------|
| `UNAUTHORIZED` | 401 | 토큰 없음 / 만료 |
| `ACCESS_DENIED` | 403 | 권한 없음 |
| `VALIDATION_FAILED` | 400 | 입력값 오류 |

### 도메인별 주요 에러 코드

| 도메인 | 에러 코드 |
|--------|---------|
| auth | `USER_NOT_FOUND`, `EMAIL_ALREADY_EXISTS`, `INVALID_PASSWORD` |
| order | `ORDER_NOT_FOUND`, `ORDER_NOT_OWNED_BY_USER`, `INSUFFICIENT_BALANCE`, `INVALID_LEVERAGE`, `ORDER_ALREADY_CANCELLED` |
| position | `POSITION_NOT_FOUND`, `POSITION_NOT_OWNED_BY_USER`, `POSITION_ALREADY_CLOSED`, `POSITION_LIQUIDATED` |
| battle | `BATTLE_NOT_FOUND`, `BATTLE_NOT_IN_PROGRESS`, `BATTLE_ALREADY_JOINED`, `BATTLE_FULL`, `BATTLE_NOT_OWNED_BY_USER` |
| market | `TICKER_NOT_FOUND`, `MARKET_DATA_UNAVAILABLE` |
| ranking | `RANKING_DATA_NOT_FOUND` |
| card | `CARD_NOT_FOUND`, `CARD_GENERATION_FAILED` |
| notification | `NOTIFICATION_NOT_FOUND` |

신규 도메인이라 에러 코드가 없는 경우, 필요한 에러 코드명을 명세에 포함하고
출력 후 `ErrorCode.kt`에 추가가 필요하다고 안내한다.

---

## Step 5 — 출력 전 준비

출력 전 `.claude/skills/api-spec/references/api-spec-example.md`를 Read 툴로 읽고,
엔드포인트 서술 방식 · Schemas 테이블 형식 · 에러 코드 표기 · Kotlin DTO 스니펫 패턴을 확인한다.

---

## 출력 형식

아래 형식을 정확히 따른다.

````markdown
## {기능명} API 명세서

### Endpoints

---

#### {HTTP메서드} {URL}
- **권한**: [AUTH] / [ADMIN] / 없음
- **설명**: 한 줄 설명
- **Request**:
  - Content-Type: `application/json`
  - Body: `{Verb}{Domain}Request`
- **Response**: `{Domain}Response`
- **에러**:
  - `ERROR_CODE` (HTTP상태) — 발생 조건

---

(엔드포인트 반복)

#### STOMP `/topic/{경로}`   (WebSocket이 필요한 경우)
- **방향**: 서버 → 클라이언트 (브로드캐스트)
- **설명**: 한 줄 설명
- **Payload**: `{Event}Message`
- **발행 시점**: {언제 발행}

---

### Schemas

#### {Verb}{Domain}Request

| 필드 | 타입 | 필수 | 설명 |
|------|------|:----:|------|
| fieldName | String | Y | 설명 |
| fieldName | Long? | N | nullable, 설명 |

#### {Domain}Response

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 고유 ID |
| fieldName | String | 설명 |

---

### Kotlin DTO

```kotlin
// Request
package com.coinbattle.domain.{domain}.dto.request

data class {Verb}{Domain}Request(
    @field:NotBlank val fieldName: String,
    @field:Min(1) @field:Max(10) val leverage: Int
)

// Response
package com.coinbattle.domain.{domain}.dto.response

data class {Domain}Response(
    val id: Long,
    val fieldName: String,
    val fieldName2: BigDecimal?
)
```
````

---

## 출력 후 안내

명세서 출력 후 다음을 안내한다:

1. 추가하거나 수정할 엔드포인트가 있으면 말해달라
2. 신규 에러 코드가 있다면 `ErrorCode.kt`에 추가가 필요하다
3. 명세 확정 후 `/feature` 스킬로 이슈 생성과 구현을 바로 시작할 수 있다

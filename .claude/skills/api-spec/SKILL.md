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
REST/WebSocket API 명세를 구조화된 Markdown으로 작성한다.

---

## 1단계: 기능 파악

명세를 쓰기 전에, 기능 설명에서 아래 항목을 추출한다.
명확하지 않은 것은 작성하면서 합리적인 기본값으로 채운다 — 사소한 것으로 먼저 묻지 않는다.

- **도메인**: auth / market / order / position / battle / ranking / card / notification 중 어디?
- **호출 주체**: 클라이언트(브라우저/앱) / 서버 내부 / 스케줄러
- **인증 필요 여부**: JWT Bearer 토큰 필요?
- **실시간 여부**: 단발 REST 요청인가, 구독/브로드캐스트가 필요한가?
- **상태 변경 여부**: 데이터를 쓰는가, 읽는가?

---

## 2단계: 명세 작성

### URL 규칙

```
REST:      /api/{domain}/{리소스}/{id}/{하위리소스}
WebSocket: /topic/{domain}/{id}          # 브로드캐스트
           /user/queue/{domain}           # 개인 알림
```

기존 경로 패턴:
- 인증: `/api/auth/register`, `/api/auth/login`
- 시세: `/api/market/**`
- 주문: `/api/orders/**`
- 배틀: `/api/battles/**`
- 랭킹: `/api/ranking/**`

### HTTP 메서드 선택 기준

| 메서드 | 사용 상황 |
|--------|----------|
| GET | 조회, 상태 변경 없음 |
| POST | 새 리소스 생성, 액션 실행 (주문 제출, 배틀 참가 등) |
| PATCH | 부분 수정 |
| DELETE | 삭제 또는 취소 |

### DTO 명명 규칙

| 역할 | 패턴 | 예시 |
|------|------|------|
| 요청 바디 | `{동사}{Domain}Request` | `PlaceOrderRequest` |
| 응답 바디 | `{Domain}Response` | `OrderResponse` |
| 내부 커맨드 | `{동사}{Domain}Command` | `PlaceOrderCommand` |

---

## 3단계: 출력 형식

각 엔드포인트마다 아래 템플릿으로 작성한다.

---

### `{HTTP 메서드} {경로}`

> {한 줄 설명}

**인증**: Bearer JWT 필요 / 불필요

**Path Parameters** (해당 시)

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `{name}` | `{type}` | {설명} |

**Query Parameters** (해당 시)

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| `{name}` | `{type}` | Y/N | `{default}` | {설명} |

**Request Body** (해당 시): `application/json`

```json
{
  "field": "type — 설명 (제약)"
}
```

**Response** `200 OK`

```json
{
  "field": "type — 설명"
}
```

**Error Cases**

| 상태 코드 | ErrorCode | 발생 조건 |
|----------|-----------|----------|
| 400 | `{ErrorCode}` | {조건} |
| 401 | `UNAUTHORIZED` | 토큰 없음 / 만료 |
| 409 | `{ErrorCode}` | {조건} |

---

WebSocket 구독이 있는 경우:

### STOMP `/topic/{경로}`

> {설명}

**방향**: 서버 → 클라이언트 (브로드캐스트)

**Payload**

```json
{
  "field": "type — 설명"
}
```

**발행 시점**: {언제 발행되는지}

---

## 4단계: Kotlin DTO 코드 스니펫

API 명세 아래에 항상 Request/Response DTO의 Kotlin data class를 작성한다.

### 작성 규칙

- `data class` 사용, 불변 (`val`)
- 유효성 검증은 Bean Validation 애노테이션 (`@NotBlank`, `@Min`, `@Max`, `@NotNull`)
- Enum은 `sealed class` 또는 `enum class` — 허용 값이 고정된 경우
- 응답 DTO는 `@JsonInclude(JsonInclude.Include.NON_NULL)` 적용 (nullable 필드가 있을 때)
- 패키지 경로 명시: `com.coinbattle.domain.{domain}.dto.request` / `.response`

### 코드 스니펫 템플릿

```kotlin
// Request
package com.coinbattle.domain.{domain}.dto.request

data class {동사}{Domain}Request(
    val field: Type,
    @field:NotNull val field2: Type
)

// Response
package com.coinbattle.domain.{domain}.dto.response

data class {Domain}Response(
    val field: Type,
    val field2: Type?
)
```

Enum이 있는 경우:

```kotlin
enum class {Domain}{속성} {
    VALUE_A, VALUE_B, VALUE_C
}
```

---

## 5단계: 구현 고려사항 (옵션)

API 명세 아래에, 구현 시 중요한 사항이 있으면 간략히 추가한다.

- **동시성**: 분산 락 / 낙관적 락 필요 여부
- **Redis 캐시**: 캐시 전략 (키, TTL)
- **비동기 팬아웃**: ApplicationEvent 발행 여부
- **강제청산 연동**: 포지션 변경 시 청산 모니터링 영향

---

## 작성 스타일

- 한국어로 설명, 필드명/타입/코드는 영어
- 필드 설명에 유효성 조건 포함 — 예: `"amount": "number — 매수 금액, 최솟값 1000"`
- 아직 없는 ErrorCode는 `{SUGGESTED}` 표시 후 제안
- 정보가 불확실한 부분은 명세 아래 **"미결 사항"** 섹션으로 정리

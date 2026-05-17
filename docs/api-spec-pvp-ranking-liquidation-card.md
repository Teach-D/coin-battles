# PVP 승률 랭킹 + 청산 밈 카드 API 명세서

## Endpoints

---

### GET /api/rankings
- **권한**: 없음 (공개 조회)
- **설명**: 타입별 랭킹 Top N을 조회한다. `type=pvp`일 때 `evaluatedValue`는 승률×100(Long)이다.
- **Request**:
  - Query: `type` (String, 기본값 `season`, 허용값 `season` | `daily` | `pvp`), `limit` (Int, 기본값 `100`, 최대 `100`)
- **Response**: `List<RankingEntryResponse>`
- **에러**:
  - `VALIDATION_FAILED` (400) — `type`이 season|daily|pvp 이외의 값

---

### GET /api/rankings/me  [AUTH]
- **권한**: [AUTH]
- **설명**: 로그인한 유저의 시즌·데일리 랭킹 슬롯을 조회한다.
- **Request**: 없음
- **Response**: `MyRankingResponse` (기존 재사용)
- **에러**:
  - `USER_NOT_FOUND` (404) — 존재하지 않는 유저

---

### STOMP `/user/queue/notification` — LiquidationCardReadyMessage
- **방향**: 서버 → 클라이언트 (개인 큐)
- **설명**: 강제청산 완료 후 생성된 💀 밈 카드 URL을 청산 당사자에게 푸시한다.
- **Payload**: `LiquidationCardReadyMessage`
- **발행 시점**: `LiquidationScheduler`가 `forceClose()` 성공 → S3 업로드 완료 직후
- **참고**: 동일 경로로 기존 `LiquidationNotificationMessage`(type=`LIQUIDATED`)도 발송됨. 클라이언트는 `type` 필드로 구분

---

## Schemas

### RankingEntryResponse (기존 재사용)

| 필드 | 타입 | 설명 |
|------|------|------|
| rank | Int | 순위 (1-based) |
| userId | Long | 유저 ID |
| nickname | String | 닉네임 |
| evaluatedValue | Long | type=season/daily: 평가금액(원) / type=pvp: 승률×100 (예: 7550 → 75.50%) |

### LiquidationCardReadyMessage (신규)

| 필드 | 타입 | 설명 |
|------|------|------|
| type | String | 고정값 "LIQUIDATION_CARD_READY" |
| ticker | String | 청산된 코인 티커 (예: "KRW-BTC") |
| cardImageUrl | String | S3 업로드된 청산 카드 이미지 URL |
| timestamp | String | ISO-8601 발행 시각 |

---

## Kotlin DTO

```kotlin
// 기존 재사용
package com.coinbattle.domain.ranking.dto.response

data class RankingEntryResponse(
    val rank: Int,
    val userId: Long,
    val nickname: String,
    val evaluatedValue: Long
)

// 신규
package com.coinbattle.domain.battle.dto.response

import java.time.Instant

data class LiquidationCardReadyMessage(
    val type: String = "LIQUIDATION_CARD_READY",
    val ticker: String,
    val cardImageUrl: String,
    val timestamp: String = Instant.now().toString()
)
```

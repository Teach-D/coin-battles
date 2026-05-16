# 예시 출력 — 배틀 참가 API

> `/api-spec 배틀 참가 기능` 요청 시의 출력 예시입니다.

---

## 배틀 참가 API 명세서

### Endpoints

---

#### POST /api/battles/{battleId}/join
- **권한**: [AUTH]
- **설명**: 대기 중인 배틀에 참가 신청한다
- **Request**: 없음 (Path Parameter만 사용)
- **Response**: `BattleJoinResponse`
- **에러**:
  - `BATTLE_NOT_FOUND` (404) — 배틀 미존재
  - `BATTLE_NOT_IN_PROGRESS` (400) — WAITING 상태가 아님
  - `BATTLE_ALREADY_JOINED` (409) — 이미 참가 중
  - `BATTLE_FULL` (400) — 정원 초과

---

#### GET /api/battles/{battleId}
- **권한**: [AUTH]
- **설명**: 배틀 상세 정보와 현재 참가자 목록을 조회한다
- **Request**: 없음
- **Response**: `BattleDetailResponse`
- **에러**:
  - `BATTLE_NOT_FOUND` (404) — 배틀 미존재

---

#### GET /api/battles
- **권한**: [AUTH]
- **설명**: 참가 가능한 배틀 목록을 조회한다
- **Request**:
  - Query: `status` (BattleStatus?, 기본값 WAITING), `page` (Int, 기본값 0), `size` (Int, 기본값 20)
- **Response**: `Page<BattleResponse>`

---

#### STOMP `/topic/battle/{battleId}/ranking`
- **방향**: 서버 → 클라이언트 (브로드캐스트)
- **설명**: 배틀 진행 중 실시간 랭킹 변동을 전송한다
- **Payload**: `BattleRankingMessage`
- **발행 시점**: 참가자의 포지션 평가금액 변경 시 (1~3초 주기)

---

### Schemas

#### BattleJoinResponse

| 필드 | 타입 | 설명 |
|------|------|------|
| battleId | Long | 참가한 배틀 ID |
| participantId | Long | 참가자 레코드 ID |
| joinedAt | LocalDateTime | 참가 시각 |

#### BattleDetailResponse

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 배틀 ID |
| title | String | 배틀 제목 |
| status | BattleStatus | WAITING / IN_PROGRESS / FINISHED |
| mode | BattleMode | PVP_1V1 / PVP_3 / PVP_5 |
| durationMinutes | Int | 배틀 시간 (분) |
| startedAt | LocalDateTime? | nullable, 시작 시각 |
| finishedAt | LocalDateTime? | nullable, 종료 시각 |
| participants | List\<ParticipantSummary\> | 참가자 목록 |

#### BattleResponse

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 배틀 ID |
| title | String | 배틀 제목 |
| status | BattleStatus | 현재 상태 |
| mode | BattleMode | 배틀 모드 |
| currentParticipants | Int | 현재 참가 인원 |
| maxParticipants | Int | 최대 참가 인원 |
| durationMinutes | Int | 배틀 시간 (분) |

#### BattleRankingMessage (WebSocket Payload)

| 필드 | 타입 | 설명 |
|------|------|------|
| battleId | Long | 배틀 ID |
| rankings | List\<RankingEntry\> | 순위 목록 |
| updatedAt | LocalDateTime | 갱신 시각 |

---

### Kotlin DTO

```kotlin
// Response
package com.coinbattle.domain.battle.dto.response

data class BattleJoinResponse(
    val battleId: Long,
    val participantId: Long,
    val joinedAt: LocalDateTime
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class BattleDetailResponse(
    val id: Long,
    val title: String,
    val status: BattleStatus,
    val mode: BattleMode,
    val durationMinutes: Int,
    val startedAt: LocalDateTime?,
    val finishedAt: LocalDateTime?,
    val participants: List<ParticipantSummary>
)

data class BattleResponse(
    val id: Long,
    val title: String,
    val status: BattleStatus,
    val mode: BattleMode,
    val currentParticipants: Int,
    val maxParticipants: Int,
    val durationMinutes: Int
)

// WebSocket Payload
data class BattleRankingMessage(
    val battleId: Long,
    val rankings: List<RankingEntry>,
    val updatedAt: LocalDateTime
)

// Enum
enum class BattleStatus { WAITING, IN_PROGRESS, FINISHED }
enum class BattleMode { PVP_1V1, PVP_3, PVP_5 }
```

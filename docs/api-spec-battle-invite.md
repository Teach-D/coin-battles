# 친구 초대 배틀 API 명세서

> 기반: docs/domain-model-battle-invite.md
> 작성일: 2026-05-16

---

## Endpoints

---

### POST /api/battles/{battleId}/invite
- **권한**: [AUTH]
- **설명**: 진행 중인 배틀의 참가자가 10분 유효기간 초대 코드를 생성한다
- **Request**: 없음 (Path Parameter만 사용)
  - Path: `battleId` (UUID) — 초대할 배틀 ID
- **Response**: `InviteCodeResponse`
- **에러**:
  - `BATTLE_NOT_FOUND` (404) — 배틀 미존재
  - `BATTLE_ACCESS_DENIED` (403) — 요청자가 해당 배틀의 참가자가 아님
  - `BATTLE_NOT_IN_PROGRESS` (400) — 배틀이 IN_PROGRESS 상태가 아님 (신규)

---

### POST /api/battles/join/{inviteCode}
- **권한**: [AUTH]
- **설명**: 초대 코드로 진행 중인 배틀에 즉시 참가한다. 내부적으로 `battle:{battleId}:join` Redisson 락 (TTL 3초) 획득 후 처리한다
- **Request**: 없음 (Path Parameter만 사용)
  - Path: `inviteCode` (String) — UUID 형식 초대 코드
- **Response**: `JoinByInviteResponse`
- **에러**:
  - `INVITE_CODE_NOT_FOUND` (404) — 코드 만료 또는 미존재 (신규)
  - `ALREADY_JOINED_BATTLE` (409) — 이미 해당 배틀에 참가 중 (신규)
  - `BATTLE_ALREADY_FINISHED` (400) — 배틀이 이미 종료됨 (신규)
  - `BATTLE_LOCK_TIMEOUT` (423) — 배틀 처리 중 락 획득 실패

---

## Schemas

### InviteCodeResponse

| 필드 | 타입 | 설명 |
|------|------|------|
| inviteCode | String | UUID 형식 초대 코드 |
| inviteUrl | String | 공유 가능한 초대 링크 (`{appBaseUrl}/join/{inviteCode}`) |
| expiresAt | LocalDateTime | 코드 만료 시각 (생성 시각 + 10분) |

### JoinByInviteResponse

| 필드 | 타입 | 설명 |
|------|------|------|
| battleId | String | 참가한 배틀 UUID |
| battleRoomUrl | String | 배틀 룸 이동 경로 (`/battle/{battleId}`) |
| joinedAt | LocalDateTime | 참가 시각 |

---

## Kotlin DTO

```kotlin
package com.coinbattle.domain.battle.dto.response

import java.time.LocalDateTime

data class InviteCodeResponse(
    val inviteCode: String,
    val inviteUrl: String,
    val expiresAt: LocalDateTime
)

data class JoinByInviteResponse(
    val battleId: String,
    val battleRoomUrl: String,
    val joinedAt: LocalDateTime
)
```

---

## ErrorCode.kt 추가 항목

```kotlin
BATTLE_NOT_IN_PROGRESS(400, "배틀이 진행 중이 아닙니다"),
INVITE_CODE_NOT_FOUND(404, "초대 코드가 만료되었거나 존재하지 않습니다"),
ALREADY_JOINED_BATTLE(409, "이미 이 배틀에 참가 중입니다"),
BATTLE_ALREADY_FINISHED(400, "이미 종료된 배틀입니다"),
```

---

## 흐름 요약

```
[초대 코드 생성]
클라이언트 → POST /api/battles/{battleId}/invite
서버: BattleSession 존재 확인 → Battle.canGenerateInvite() → UUID 생성
     → Redis SET battle:invite:{uuid} = battleId, TTL 600s
     → InviteCodeResponse 반환

[초대 코드로 참가]
클라이언트 → POST /api/battles/join/{inviteCode}
서버: Redis GET battle:invite:{inviteCode} → battleId 획득
     → battle:{battleId}:join 락 획득 (TTL 3s)
     → Battle 상태 검증 (IN_PROGRESS 확인)
     → BattleSession 중복 확인
     → Battle.addLateParticipant() + BattleSession INSERT (단일 트랜잭션)
     → 락 해제 → JoinByInviteResponse 반환
```

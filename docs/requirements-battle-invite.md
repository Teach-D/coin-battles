# 요구사항 명세서 — 친구 초대 배틀

## 1. 개요

- **기능 목적**: 진행 중인 배틀 참가자가 초대 코드를 생성해 외부 친구를 배틀에 직접 참가시킨다. 랜덤 매칭 대기 없이 즉시 입장해 바이럴 유입 경로를 단축한다.
- **핵심 사용자**: USER (인증된 플레이어)
- **범위**
  - In Scope: 초대 코드 생성, 초대 링크 공유, 코드로 배틀 참가, 중복 참가 방지
  - Out of Scope: 관전자(스펙테이터) 모드, 초대 수락/거절 UX, 초대장 푸시 알림, WAITING 상태 배틀 초대

---

## 2. 도메인 모델

### 신규 도메인 객체 (Redis-only, DB 미저장)

| 항목 | 내용 |
|------|------|
| 키 패턴 | `battle:invite:{inviteCode}` |
| 값 | `battleId` (UUID 문자열) |
| TTL | 10분 |
| 저장 위치 | Redis (StringRedisTemplate) |

초대 코드는 일회성 단기 데이터이므로 DB 엔티티를 생성하지 않는다.

### 기존 엔티티와의 관계

- `Battle` — 초대 코드는 `Battle.battleId`에 연결된다
- `BattleSession` — 초대로 참가 시 신규 `BattleSession` 레코드 생성
- `User` — 초대받은 유저의 `id`로 세션 생성

### 상태 전이 — Battle.status 제약

```
WAITING     → 초대 코드 생성 불가 (일반 매칭 큐 사용)
IN_PROGRESS → 초대 코드 생성 가능 / 초대 코드로 참가 가능
FINISHED    → 초대 코드 생성 불가 / 코드가 있어도 참가 불가
```

> **설계 노트**: `Battle.canAddParticipant()`는 현재 WAITING 상태만 허용한다.
> IN_PROGRESS 배틀 참가는 별도 `addLateParticipant()` 메서드를 추가해 처리한다.
> 늦게 참가한 유저는 초기 시드머니(`seedMoney`)로 시작하며, 배틀 잔여 시간 동안 거래할 수 있다.

---

## 3. 비즈니스 규칙

1. **BR-01** 초대 코드 생성 가능 상태 제한
   - 조건: 요청자가 해당 배틀의 참가자이고, 배틀이 IN_PROGRESS 상태인 경우에만 코드 생성 가능
   - 위반 시: `400 BATTLE_NOT_IN_PROGRESS`

2. **BR-02** 참가자만 초대 가능
   - 조건: 요청자(`userId`)의 `BattleSession`이 해당 `battleId`에 존재해야 한다
   - 위반 시: `403 BATTLE_ACCESS_DENIED`

3. **BR-03** 만료된 코드 접근 차단
   - 조건: Redis에 키가 없으면 만료된 것으로 간주
   - 위반 시: `404 INVITE_CODE_NOT_FOUND`

4. **BR-04** 이미 참가 중인 유저 재참가 차단
   - 조건: `BattleSession`에 동일 `battleId` + `participantId` 레코드가 이미 존재하면 참가 불가
   - 위반 시: `409 ALREADY_JOINED_BATTLE`

5. **BR-05** 종료된 배틀 참가 차단
   - 조건: 코드 유효기간 내라도 배틀이 FINISHED 상태면 참가 불가
   - 위반 시: `400 BATTLE_ALREADY_FINISHED`

6. **BR-06** 초대 코드 1인 1코드 제한 없음
   - 동일 배틀에 여러 번 코드 생성 허용 (각 코드 TTL 10분 독립 적용)
   - 코드는 UUID 랜덤 생성이므로 충돌 확률 무시

---

## 4. 사용자 & 권한

| 역할 | JWT 인증 | 접근 가능 API |
|------|----------|--------------|
| `GUEST` (비인증) | 불필요 | 없음 — 초대 링크 착지 시 로그인 리다이렉트 |
| `USER` | 필요 | 코드 생성 (`POST /api/battles/{battleId}/invite`) |
| `USER` | 필요 | 코드로 참가 (`POST /api/battles/join/{inviteCode}`) |

비인증 유저가 초대 링크(`/join/:inviteCode`)에 접근하면 로그인 후 원래 링크로 리다이렉트한다. 프론트엔드 `AuthGuard`가 처리한다.

---

## 5. 주요 시나리오

### Happy Path — 초대 코드 생성 및 참가

1. 유저 A가 IN_PROGRESS 배틀에 참가 중
2. `POST /api/battles/{battleId}/invite` 호출 (JWT 인증)
3. 서버에서 UUID 생성 → Redis `battle:invite:{uuid}` = `battleId`, TTL 10분
4. 응답으로 `{ inviteCode, inviteUrl, expiresAt }` 반환
5. 유저 A가 Web Share API 또는 클립보드 복사로 링크 공유
6. 유저 B가 `/join/{inviteCode}` 링크 접근 (로그인 상태 가정)
7. 프론트엔드에서 `POST /api/battles/join/{inviteCode}` 자동 호출
8. 서버: Redis에서 코드 조회 → `battleId` 획득 → 배틀 상태 검증 → `BattleSession` 생성
9. 응답으로 `{ battleId, battleRoomUrl }` 반환
10. 프론트엔드가 `/battle/{battleId}` (BattleRoom)으로 이동

### 예외 시나리오

| 시나리오 | 처리 방식 |
|----------|-----------|
| 배틀이 WAITING 상태일 때 초대 코드 생성 시도 | `400 BATTLE_NOT_IN_PROGRESS` |
| 참가자가 아닌 유저가 초대 코드 생성 시도 | `403 BATTLE_ACCESS_DENIED` |
| 10분 경과 후 코드 사용 시도 | `404 INVITE_CODE_NOT_FOUND` |
| 이미 참가 중인 유저가 코드로 재참가 시도 | `409 ALREADY_JOINED_BATTLE` |
| 배틀이 FINISHED된 후 코드 사용 시도 | `400 BATTLE_ALREADY_FINISHED` |
| 비인증 유저가 초대 링크 접근 | 로그인 후 `/join/{inviteCode}` 리다이렉트 |

---

## 6. 비기능 요구사항

- **성능**: 코드 생성/조회 모두 Redis 단일 연산 — 목표 응답 50ms 이내
- **동시성**: 동일 코드로 동시 참가 요청 시 `BattleSession` UNIQUE 제약(`battle_id + participant_id`)으로 DB 레벨 중복 방지. Redisson 분산 락 불필요 (DB 제약으로 충분).
- **Redis 캐시**: `battle:invite:{inviteCode}` — TTL 10분, 값은 `battleId` 문자열
- **실시간**: 초대 참가 성공 시 기존 `/topic/battle/{battleId}` STOMP 채널로 참가자 목록 업데이트 불필요 (BattleRankingScheduler가 5초 주기로 자동 반영)
- **비동기 팬아웃**: 없음 — 초대 참가는 동기 처리로 충분
- **데이터 보존**: 초대 코드는 Redis-only, 만료 후 자동 삭제. BattleSession은 DB 영구 저장.

---

## 7. 미결 사항 (TBD)

- [ ] 늦게 참가한 유저의 최종 결과 처리 — `BattleEndService`에서 늦은 참가자도 수익률 계산/랭킹 포함할지 여부
- [ ] 초대 참가 가능 인원 제한 — 현재 `maxParticipants` 초과 시도 허용 여부 (BR 추가 필요)
- [ ] 초대 코드 공개 URL 형식 — `https://{domain}/join/{inviteCode}` 도메인 확정 필요 (환경변수 `VITE_APP_URL`)

# 도메인 모델 — 친구 초대 배틀

> 기반: docs/requirements-battle-invite.md
> 작성일: 2026-05-16

---

## 1. 유비쿼터스 언어 (Ubiquitous Language)

| 용어 | 정의 |
|------|------|
| 초대 코드 (InviteCode) | 배틀 참가자가 생성하는 UUID 기반 단기 접근 토큰. Redis에만 존재하며 TTL 10분 |
| 초대 링크 (InviteUrl) | `/join/{inviteCode}` 경로로 구성된 공유 가능한 URL |
| 늦은 참가 (Late Join) | 배틀이 IN_PROGRESS 상태일 때 초대 코드로 합류하는 행위. 초기 시드머니로 잔여 시간 동안 거래 |
| 초대자 (Inviter) | 현재 배틀 참가 중이며 초대 코드를 생성한 유저 |
| 피초대자 (Invitee) | 초대 링크를 받아 배틀에 합류하는 유저 |

---

## 2. 바운디드 컨텍스트

```
[ Battle Context ]                    [ User Context ]
  Battle (Aggregate Root)  ←──────────  User (ID 참조)
  BattleSession
  InviteCode (Redis Value Object)
```

초대 기능은 Battle 컨텍스트 내에서 완결된다. User 컨텍스트는 ID 참조로만 연관된다.

---

## 3. 애그리거트

### Aggregate: Battle

#### 책임
배틀의 상태(WAITING → IN_PROGRESS → FINISHED)와 참가자 구성의 일관성을 보호한다.

#### 애그리거트 루트
`Battle`

#### 엔티티 & 값 객체

| 구분 | 이름 | 핵심 속성 | 설명 |
|------|------|-----------|------|
| Entity (Root) | `Battle` | battleId(UUID), status, hostUserId, seedMoney, duration, maxParticipants, currentParticipants, @Version | 배틀 메타데이터 및 상태 관리 |
| Entity | `BattleSession` | id, battleId, participantId, joinedAt, finalValuation, rank | 참가자별 세션 레코드 |
| Value Object (Redis) | `InviteCode` | code(UUID), battleId(UUID), ttl(10분) | 일회성 초대 토큰. DB 저장 없음 |

#### 비즈니스 불변식 (Invariants)

- **INV-01**: 초대 코드는 Battle.status == IN_PROGRESS일 때만 생성 가능하다
  - 위반 시: `BATTLE_NOT_IN_PROGRESS` (400)

- **INV-02**: 초대 코드 생성 요청자는 해당 배틀의 BattleSession 보유자여야 한다
  - 위반 시: `BATTLE_ACCESS_DENIED` (403)

- **INV-03**: 만료(TTL 초과)된 초대 코드로는 참가할 수 없다
  - 위반 시: `INVITE_CODE_NOT_FOUND` (404)

- **INV-04**: 동일 배틀에 이미 BattleSession이 존재하는 유저는 재참가할 수 없다
  - 위반 시: `ALREADY_JOINED_BATTLE` (409)

- **INV-05**: Battle.status == FINISHED인 배틀에는 참가할 수 없다
  - 위반 시: `BATTLE_ALREADY_FINISHED` (400)

#### 라이프사이클 & 상태 머신

```
WAITING     -[일반 매칭 완료]→ IN_PROGRESS
IN_PROGRESS -[배틀 종료]→     FINISHED
IN_PROGRESS  ↑ 초대 코드 생성/사용 가능 구간
```

**Battle 엔티티 신규 메서드:**
```
canGenerateInvite(): Boolean
  = status == IN_PROGRESS

addLateParticipant(): Unit
  - status != IN_PROGRESS 이면 예외
  - currentParticipants++  (참가 카운트 증가)
```

> **설계 결정 — `addLateParticipant()` maxParticipants 제한**: 초대 경로는 maxParticipants
> 상한을 적용하지 않는다. 이미 IN_PROGRESS 상태의 배틀에 초대받은 것이므로 게임 밸런스보다
> 바이럴 유입을 우선한다. (TBD: 제한이 필요하다면 BR 추가)

#### 트랜잭션 경계

- **초대 코드 생성**: Redis 단일 SET 연산. DB 트랜잭션 불필요
- **초대 코드로 참가**: 단일 DB 트랜잭션 — Battle.addLateParticipant() + BattleSession INSERT
  - Battle 낙관적 락(@Version)이 동시 참가 충돌을 감지한다
  - BattleSession의 UNIQUE(battle_id, participant_id) 제약이 중복 삽입을 막는다

#### 동시성 고려사항

| 단계 | 방식 | 적용 여부 | 이유 |
|------|------|-----------|------|
| 1 | Redisson 분산 락 | Y | 기존 `battle:{battleId}:join` 락 재사용. 동시 초대 참가 직렬화 |
| 2 | 낙관적 락 (@Version) | Y | Battle.currentParticipants 동시 증가 방지 (Battle에 이미 @Version 있음) |
| 3 | 멱등성 키 | N | BattleSession UNIQUE 제약으로 충분. 클라이언트 UUID 불필요 |

> **기존 락 재사용**: `BattleService.joinBattle()`에서 사용하는 `battle:{battleId}:join` Redisson
> 락을 InviteService에서도 동일하게 획득한다. TTL 3초.

#### 도메인 이벤트

신규 도메인 이벤트 없음. `BattleRankingScheduler`가 5초 주기로 IN_PROGRESS 배틀의 참가자 목록을
재조회해 STOMP 브로드캐스트하므로, 별도 이벤트 없이 5초 이내 자동 반영된다.

---

## 4. 애그리거트 관계도

```
Battle (root) ──1:N──> BattleSession (battle_id 참조)
Battle        ──1:N──> InviteCode (Redis, battle_id 값으로 연결, DB 관계 없음)
User          ──1:N──> BattleSession (participant_id 참조, ID만)
```

---

## 5. 도메인 이벤트

| 이벤트명 | 발행 주체 | 발행 시점 | 구독 주체 | 처리 내용 |
|----------|-----------|-----------|-----------|-----------|
| (없음) | — | — | — | BattleRankingScheduler가 자동 반영 |

---

## 6. 도메인 서비스

### InviteService

- **책임**: Battle 참가 검증(Redis), 초대 코드 생성/조회, BattleSession 생성 오케스트레이션
- **관여 애그리거트**: Battle, BattleSession, InviteCode(Redis)
- **로직 요약**:
  1. 코드 생성: 참가자 검증 → 배틀 상태 검증 → UUID 생성 → Redis SET
  2. 코드 참가: Redis GET(코드→배틀ID) → 배틀 상태 검증 → 분산 락 획득 → Battle.addLateParticipant() → BattleSession INSERT → 락 해제
- **트랜잭션 전략**: 코드 생성은 트랜잭션 없음. 코드 참가는 단일 DB 트랜잭션

---

## 7. 크로스-애그리거트 상호작용

| 상황 | 관여 애그리거트 | 일관성 전략 | 이유 |
|------|----------------|-------------|------|
| 코드로 배틀 참가 | Battle + BattleSession | 강한 일관성 (단일 트랜잭션) | currentParticipants 증가와 세션 생성이 원자적이어야 함 |
| 코드 생성 시 참가자 검증 | Battle + BattleSession | 강한 일관성 (읽기 전용) | BattleSession 존재 여부로 참가자임을 확인 |

---

## 8. 레포지토리 인터페이스

### BattleSessionRepository (기존, 추가 메서드)
```kotlin
fun existsByParticipantIdAndBattleId(participantId: Long, battleId: UUID): Boolean  // 기존
fun findParticipantIdsByBattleId(battleId: UUID): List<Long>                         // 기존
```

신규 메서드 불필요. 기존 `existsByParticipantIdAndBattleId`로 INV-02, INV-04 모두 처리 가능.

### BattleRepository (기존)
```kotlin
fun findById(battleId: UUID): Optional<Battle>  // 기존 JPA 기본 제공
```

---

## 9. 패키지 구조 제안

```
com.coinbattle
└── domain/
    └── battle/
        ├── entity/
        │   ├── Battle.kt            # addLateParticipant(), canGenerateInvite() 추가
        │   └── BattleSession.kt     # 변경 없음
        ├── dto/
        │   ├── request/
        │   │   └── (없음)           # path variable만 사용
        │   └── response/
        │       ├── InviteCodeResponse.kt   # inviteCode, inviteUrl, expiresAt
        │       └── JoinByInviteResponse.kt # battleId, battleRoomUrl
        ├── service/
        │   ├── InviteService.kt     # 신규
        │   └── BattleService.kt     # 변경 없음
        └── controller/
            └── BattleController.kt  # 엔드포인트 2개 추가
```

---

## 10. 설계 결정 사항 (ADR)

### ADR-01: InviteCode를 DB 엔티티 없이 Redis Value로 처리
- **결정**: Redis `battle:invite:{uuid}` = `battleId` 문자열, TTL 10분
- **이유**: 초대 코드는 단기 일회성 데이터. DB 스키마와 마이그레이션 오버헤드가 비가치적
- **trade-off**: 코드 발급 이력을 DB에서 추적 불가. 감사(audit) 요구가 생기면 별도 테이블 필요

### ADR-02: 기존 `battle:{battleId}:join` Redisson 락 재사용
- **결정**: `InviteService.joinByInvite()`에서 동일 락 키 사용
- **이유**: 일반 참가와 초대 참가가 동일한 `currentParticipants` 변수를 수정하므로 같은 락 필요
- **trade-off**: 일반 참가와 초대 참가가 상호 직렬화됨. 성능 영향 미미 (락 경합 빈도 낮음)

### ADR-03: `addLateParticipant()`에서 maxParticipants 상한 미적용
- **결정**: IN_PROGRESS 배틀의 초대 참가는 maxParticipants 제한을 받지 않는다
- **이유**: 바이럴 유입 극대화. 초대는 의도적 행위이므로 호스트가 원하는 것으로 간주
- **trade-off**: 의도치 않게 많은 인원이 합류할 수 있음. 향후 BR 추가로 제한 가능

---

## 11. 아키텍처 위험 요소

- **Redis 장애 시 초대 코드 생성 불가**: InviteService는 Redis에 의존. 장애 시 초대 기능 전체 중단.
  Redis가 이미 시세 캐시 / 랭킹 등에 critical 경로이므로 추가 위험 없음.
- **Battle.currentParticipants 무결성**: addLateParticipant() 호출 시 @Version 충돌이 발생하면
  초대 참가 실패. 재시도 없이 에러 반환 (일반 주문과 동일한 낙관적 락 정책).

---

## 12. TBD

- [ ] 늦은 참가자가 배틀 종료 시 `BattleEndService` 수익률 계산에 포함되는지 여부
- [ ] 초대 링크 base URL — `VITE_APP_URL` 환경변수로 구성 (프론트엔드 `.env` 확정 필요)
- [ ] 초대 참가 가능 인원 상한 제한 — 향후 BR로 추가 가능

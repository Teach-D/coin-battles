# 예시 출력 — 배틀 관전(Spectator) 기능

> `/requirement 배틀 관전 기능` 요청 시의 출력 예시입니다.

---

## 1. 개요

- **기능 목적**: 진행 중인 PVP 배틀을 비참가자가 실시간으로 관전할 수 있는 기능
- **핵심 사용자**: USER (관전자), 시스템 (배틀 브로드캐스트)
- **범위**
  - In Scope: 관전 참가/퇴장, 실시간 랭킹/포지션 수신, 관전자 수 표시
  - Out of Scope: 관전자 채팅, 관전자 베팅, 배틀 미참가자의 주문 실행

---

## 2. 도메인 모델 후보

### 엔티티 목록

| 엔티티 | 핵심 속성 | 기존 엔티티 참조 |
|--------|-----------|-----------------|
| `BattleSpectator` | id, battleId, userId, joinedAt | Battle 1 ↔ N BattleSpectator |

### 엔티티 간 관계

- `Battle` 1 ↔ N `BattleSpectator`
- `User` 1 ↔ N `BattleSpectator` (한 유저가 여러 배틀 관전 가능, 동시 1개 제한 TBD)

---

## 3. 비즈니스 규칙

1. **BR-01** IN_PROGRESS 상태 배틀만 관전 가능
   - 위반 시: 400 BATTLE_NOT_IN_PROGRESS

2. **BR-02** 배틀 참가자는 관전자 목록에서 제외
   - 처리: 관전 요청 시 참가자 여부 검증

3. **BR-03** 배틀 종료(FINISHED) 시 모든 관전 세션 자동 종료
   - 처리: BattleFinishedEvent 수신 → WebSocket 세션 정리

---

## 4. 사용자 & 권한

| 역할 | JWT 인증 | 접근 가능 리소스 |
|------|----------|-----------------|
| `GUEST` | 불필요 | 관전자 수 조회 |
| `USER` | 필요 | 관전 참가/퇴장, 실시간 랭킹 구독 |

---

## 5. 주요 시나리오

### Happy Path

1. USER가 진행 중인 배틀에 관전 요청을 보낸다
2. 서버가 BattleSpectator 레코드를 생성하고 관전자 수를 Redis에서 증가시킨다
3. 클라이언트가 `/topic/battle/{battleId}/ranking` 구독을 시작한다
4. 배틀 종료 시 BattleFinishedEvent로 관전 세션이 자동 해제된다

### 예외 시나리오

| 시나리오 | 처리 방식 |
|----------|-----------|
| WAITING 상태 배틀에 관전 요청 | 400 BATTLE_NOT_IN_PROGRESS |
| 참가자가 관전 요청 | 400 PARTICIPANT_CANNOT_SPECTATE |
| 관전 중 배틀 종료 | 서버가 STOMP 세션 정리, 클라이언트 알림 |

---

## 6. 비기능 요구사항

- **성능**: 관전자 수 조회 응답 50ms 이내 (Redis counter 사용)
- **동시성**: 관전자 수 증감 — Redis INCR/DECR 원자 연산으로 처리
- **Redis 캐시**: `battle:{battleId}:spectator_count` (TTL 없음, 배틀 종료 시 삭제)
- **실시간**: `/topic/battle/{battleId}/ranking` 기존 브로드캐스트 재사용
- **비동기 팬아웃**: BattleFinishedEvent → @Async로 관전 세션 정리

---

## 7. 미결 사항 (TBD)

- [ ] 동시 관전 배틀 수 제한 여부 (1개 vs 무제한)
- [ ] 관전자에게 포지션 상세(진입가, 레버리지) 노출 여부
- [ ] 관전자 수 최대치 제한 여부 (서버 부하 고려)

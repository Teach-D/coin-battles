# 예시 출력 — 이슈 본문

> `/github-issue 배틀 참가 기능` 요청 시 생성되는 이슈 본문 예시입니다.

---

## feat 타입 이슈 예시

**제목**: `[feat] 배틀 참가 기능`

```markdown
## 개요
대기 중인 PVP 배틀에 사용자가 참가 신청하는 기능을 구현합니다.
배틀 정원 초과·중복 참가·배틀 상태 검증 등 비즈니스 규칙을 서버에서 처리합니다.

## 작업 목록
- [ ] `BattleParticipant` 엔티티 및 JPA 매핑
- [ ] `POST /api/battles/{battleId}/join` 엔드포인트 구현
- [ ] 배틀 정원 초과 검증 (INV-01)
- [ ] 중복 참가 방지 검증 (INV-02)
- [ ] 배틀 상태 WAITING 검증 (INV-03)
- [ ] 참가 완료 시 `BattleJoinedEvent` 발행 → 실시간 브로드캐스트

## API 설계
| Method | URL | 권한 | 설명 |
|--------|-----|------|------|
| POST | /api/battles/{battleId}/join | [AUTH] | 배틀 참가 신청 |
| GET | /api/battles/{battleId} | [AUTH] | 배틀 상세 조회 |

## 비즈니스 규칙
- INV-01: 배틀 정원(maxParticipants) 초과 불가
- INV-02: 이미 참가 중인 배틀에 중복 참가 불가
- INV-03: WAITING 상태 배틀에만 참가 가능

## 엣지 케이스
- [ ] 동시에 여러 사용자가 마지막 자리에 참가 시도: Redisson 락으로 직렬화
- [ ] 참가 직후 배틀이 취소되는 경우: BattleCancelledEvent 구독 후 환불 처리

## 에러 케이스
| 상황 | HTTP Status | ErrorCode |
|------|-------------|-----------|
| 배틀 미존재 | 404 | `BATTLE_NOT_FOUND` |
| WAITING 상태 아님 | 400 | `BATTLE_NOT_IN_PROGRESS` |
| 이미 참가 중 | 409 | `BATTLE_ALREADY_JOINED` |
| 정원 초과 | 400 | `BATTLE_FULL` |

## 참고
- 관련 문서: `docs/api-spec.md`, `docs/domain-model-battle.md`
```

---

## chore 타입 이슈 예시

**제목**: `[chore] 공통 예외 핸들러 및 ErrorCode 설정`

```markdown
## 개요
전 도메인에서 공통으로 사용할 예외 핸들러와 ErrorCode enum을 설정합니다.
모든 에러 응답이 일관된 포맷(`{ code, message, status }`)을 갖도록 합니다.

## 작업 목록
- [ ] `ErrorCode` enum 정의 (공통 + 도메인별)
- [ ] `GlobalExceptionHandler` (`@RestControllerAdvice`) 구현
- [ ] `ApiResponse<T>` 공통 응답 래퍼 클래스 정의
- [ ] `CoinBattleException` 기반 예외 계층 구성

## 참고
- 선행 이슈: 없음
- 관련 문서: `docs/api-spec.md`
```

---

## add 타입 이슈 예시

**제목**: `[add] Position 응답에 슬리피지 정보 추가`

```markdown
## 개요
포지션 체결 응답에 슬리피지 적용 여부와 보정된 체결가를 노출합니다.
프론트엔드에서 실제 체결가와 요청가 차이를 사용자에게 표시하기 위해 필요합니다.

## 작업 목록
- [ ] `PositionResponse`에 `slippageRate`, `executedPrice` 필드 추가
- [ ] `OrderService`에서 슬리피지 계산 결과를 DTO에 반영
- [ ] 프론트엔드 계약 업데이트 (별도 이슈)

## 비즈니스 규칙
- 슬리피지 적용 기준: 주문 금액 100만 이하 → 없음, 100~500만 → ±0.05%, 500만~ → ±0.1~0.3%

## 참고
- 선행 이슈: #9 (매수/매도 주문 시스템)
- 관련 문서: `docs/api-spec.md`
```

# 예시 출력 — issue-list.md

> `/github-issue 배틀 참가 기능` 요청 시 저장되는 `docs/issue-list.md` 예시입니다.

---

# Issue List — 배틀 참가 기능

> 생성일: 2026-05-16
> 총 이슈 수: 5개

| 순서 | 번호 | 타입 | 제목 | 선행 이슈 | 브랜치 |
|------|------|------|------|-----------|--------|
| 1 | #41 | chore | 공통 예외 핸들러 및 ErrorCode 설정 | 없음 | `chore/global-exception-handler-41` |
| 2 | #42 | feat | 배틀 참가 기능 | #41 | `feat/battle-join-42` |
| 3 | #43 | feat | 배틀 상세 조회 기능 | #42 | `feat/battle-detail-43` |
| 4 | #44 | feat | 참가 가능한 배틀 목록 조회 | #41 | `feat/battle-list-44` |
| 5 | #45 | add | 배틀 실시간 참가자 수 브로드캐스트 | #42 | `add/battle-participant-broadcast-45` |

---

## 브랜치 네이밍 규칙

```
{type}/{kebab-description}-{issue_number}
```

| 타입 | 예시 |
|------|------|
| `feat` | `feat/battle-join-42` |
| `fix` | `fix/order-status-sync-43` |
| `chore` | `chore/global-exception-handler-41` |
| `refactor` | `refactor/payment-service-extract-44` |
| `add` | `add/user-profile-fields-45` |

---

## 작업 시작 방법

```bash
# 이슈 브랜치 체크아웃 (순서대로)
git checkout dev && git pull origin dev
git checkout -b feat/battle-join-42
```

작업 완료 후:
```
/pr-merge-dev  →  dev 브랜치로 PR 생성 및 머지
```

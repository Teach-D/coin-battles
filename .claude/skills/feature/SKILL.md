---
name: feature
description: |
  기획부터 개발·PR까지 전체 파이프라인을 자동 실행하는 오케스트레이터.
  "feature 만들어줘", "기능 구현해줘", "전체 파이프라인 실행해줘", "/feature" 등
  새 기능을 처음부터 끝까지 개발하는 요청이 오면 반드시 이 스킬을 사용하세요.
  기능 설명만 있어도, 설계 문서가 없어도 바로 실행할 수 있습니다.
---

# Feature Pipeline — 기획부터 PR까지

기능 설명 하나로 설계 → 이슈 생성 → 백엔드·프론트엔드 병렬 개발 → 코드 리뷰 → PR까지 전체 파이프라인을 자동 실행하는 오케스트레이터입니다.

---

## 파이프라인 구조

```
Stage 1 (설계)    requirement → domain → api-spec    순차 실행
                                    │
                          [Gate A] API 명세 확정
                                    │
Stage 2 (이슈)         github-issue-branch 스킬
                                    │
                    ┌───────────────┴───────────────┐
              Stage 3A                        Stage 3B
         frontend-dev-agent              tdd-test-writer
          백그라운드 실행                  포그라운드 실행
                                                │
                                    [Gate B] 테스트 케이스 확인
                                                │
                                       backend-dev-agent
                                        백그라운드 실행
                    └───────────────┬───────────────┘
                              양쪽 완료 대기
                                    │
Stage 4 (검증)    code-reviewer + secure-reviewer    병렬 실행
                                    │
Stage 5 (머지)              pr-merge-dev
```

---

## 실행 전 확인

기능 설명이 충분히 구체적인지 판단합니다.

**즉시 시작 가능한 수준**: 도메인(auth/battle/order 등)과 핵심 행위가 명확한 경우.
- "유저 프로필 조회 API"
- "배틀 참가 기능"

**먼저 질문이 필요한 수준**: 도메인이나 행위가 불명확한 경우. 최대 3개 질문으로 명확화 후 시작.
- "뭔가 배틀 관련 기능" → 배틀 생성인지 참가인지 조회인지 확인
- "프로필 수정" → 수정 가능한 필드 범위 확인

질문은 선택지와 함께 제시하고, 답변을 받은 즉시 Stage 1로 진행합니다.

---

## Stage 1 — 설계 (순차)

세 스킬을 순서대로 실행합니다. 각 스킬 완료 후 다음 스킬로 자동 진행합니다.

### 1-1. requirement 스킬

`requirement` 스킬을 실행합니다. 기능 설명을 입력으로 전달합니다.

완료 후: 요구사항 문서를 `docs/requirements-{기능명}.md`에 저장합니다.

### 1-2. domain 스킬

`domain` 스킬을 실행합니다. 1-1의 요구사항 문서를 기반으로 도메인 모델을 설계합니다.

domain 스킬은 내부적으로 설계 질문(최대 3개)을 할 수 있습니다. 답변 후 자동으로 `docs/domain-model-{기능명}.md`를 생성합니다.

### 1-3. api-spec 스킬

`api-spec` 스킬을 실행합니다. 도메인 모델을 기반으로 API 명세를 작성합니다.

완료 후: api-spec 스킬의 출력을 `docs/api-spec-{기능명}.md`로 저장합니다.
(api-spec 스킬은 채팅창에만 출력하므로, 출력 내용을 파일로 직접 기록합니다.)

---

## Gate A — API 명세 확정

Stage 1 완료 후 사용자에게 확인합니다.

```
─────────────────────────────────────────
[Gate A] API 명세 확정

설계 문서:
  - docs/requirements-{기능명}.md
  - docs/domain-model-{기능명}.md
  - docs/api-spec-{기능명}.md

수정할 내용이 있으면 지금 말씀해주세요.
없으면 이슈 생성과 개발을 시작합니다.
─────────────────────────────────────────
```

수정 요청이 있으면 해당 문서를 수정한 뒤 다시 확인합니다.
확정되면 Stage 2로 진행합니다.

---

## Stage 2 — 이슈 & 브랜치 생성

`github-issue-branch` 스킬을 실행합니다.

- GitHub 이슈 생성 (제목, 작업 내용, 브랜치명 포함)
- `feat/{기능명}-{이슈번호}` 브랜치 자동 생성 및 로컬 체크아웃

완료 후 이슈 번호와 브랜치명을 기억해 이후 단계에서 활용합니다.

---

## Stage 3 — 병렬 개발

백엔드와 프론트엔드를 동시에 시작합니다.

### 3A. 프론트엔드 (백그라운드)

`frontend-dev-agent`를 `run_in_background: true`로 즉시 실행합니다.

전달 컨텍스트:
- `docs/api-spec-{기능명}.md` 경로
- `docs/domain-model-{기능명}.md` 경로
- 이슈 번호

### 3B. 백엔드 TDD (포그라운드 → 백그라운드)

`tdd-test-writer` 에이전트를 포그라운드로 실행합니다.

tdd-test-writer는 내부적으로 테스트 케이스 목록을 도출한 뒤 **Gate B**에서 사용자 확인을 기다립니다. 이 게이트는 유지합니다.

**Gate B 완료 후:**
`backend-dev-agent`를 `run_in_background: true`로 실행합니다.

전달 컨텍스트:
- tdd-test-writer가 작성한 테스트 파일 경로
- `docs/api-spec-{기능명}.md` 경로
- 이슈 번호

### 양쪽 완료 대기

frontend-dev-agent와 backend-dev-agent 모두 완료될 때까지 대기합니다.

**한쪽이 실패한 경우:**
- 실패 원인을 분석해 원인 유형을 분류합니다.
  - 컴파일 오류 → 시그니처 수정 후 해당 에이전트 재실행
  - 테스트 실패 → 실패 케이스만 수정 후 재실행
  - 5회 이상 동일 유형 실패 → 사용자에게 보고하고 중단
- 나머지 한쪽은 계속 진행합니다.

---

## Stage 4 — 검증 (병렬)

두 에이전트를 동시에 실행합니다.

- `code-reviewer` 에이전트 (`run_in_background: true`)
- `secure-reviewer` 에이전트 (`run_in_background: true`)

양쪽 완료 후 리뷰 결과를 요약해서 보여줍니다.

**Critical 이슈가 있는 경우:**
수정이 필요한 파일과 내용을 사용자에게 보고하고, 수정 후 Stage 4를 재실행할지 묻습니다.

**Critical 이슈가 없는 경우:**
Stage 5로 자동 진행합니다.

---

## Stage 5 — PR 생성

`pr-merge-dev` 스킬을 실행합니다.

- PR 제목: `feat: {기능명} #{이슈번호}`
- PR 본문: 각 Stage의 결과물을 기반으로 자동 작성
- base: `dev`

---

## 완료 메시지

모든 Stage 완료 후 아래 형식으로 결과를 출력합니다.

```
─────────────────────────────────────────
Feature Pipeline 완료

기능: {기능명}
이슈: #{이슈번호}
브랜치: feat/{기능명}-{이슈번호}

설계 문서:
  ✓ docs/requirements-{기능명}.md
  ✓ docs/domain-model-{기능명}.md
  ✓ docs/api-spec-{기능명}.md

개발:
  ✓ 백엔드 ({N}개 테스트 통과)
  ✓ 프론트엔드

검증:
  ✓ 코드 리뷰 (Critical {N}개)
  ✓ 보안 리뷰 (Critical {N}개)

PR: #{PR번호} → dev
─────────────────────────────────────────
```

---

## 중단 조건

아래 상황에서는 파이프라인을 중단하고 사용자에게 보고합니다.

- domain 스킬의 설계 질문에 답변이 없어 설계가 불완전한 경우
- Gate A에서 명세가 확정되지 않은 경우
- backend-dev-agent가 5회 이상 동일 오류로 실패한 경우
- code-reviewer 또는 secure-reviewer에서 Critical 이슈 발생 후 사용자가 수정을 거절한 경우

중단 시 현재까지 생성된 파일과 브랜치는 유지합니다.

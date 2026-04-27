---
name: pr-merge-dev
description: |
  현재 브랜치에서 dev로 PR을 생성하고 머지합니다.
  "PR 올려줘", "PR 만들어줘", "dev로 PR", "머지해줘", "pr merge", "pull request 생성", "dev에 머지" 등
  현재 브랜치를 dev에 합치는 요청이 오면 반드시 이 스킬을 사용하세요.
  브랜치명 끝 숫자를 이슈 번호로 자동 인식해 PR 본문에 연결합니다.
---

# PR 생성 → dev 머지 자동화 스킬

현재 브랜치 작업이 끝난 후 dev 브랜치로 PR을 만들고 머지까지 한 번에 처리합니다.

## 레포지토리 정보

- **owner**: Teach-D
- **repo**: coin-battle
- **base branch**: dev

---

## 실행 순서

### 1단계: 현재 브랜치 및 이슈 번호 파악

`Bash` 도구로 현재 브랜치명을 가져옵니다:

```bash
git branch --show-current
```

브랜치명 끝의 숫자를 이슈 번호로 추출합니다.

예시:
| 브랜치명 | 이슈 번호 |
|---------|---------|
| `feat/upbit-websocket-price-12` | `12` |
| `feat/jwt-auth-middleware-7` | `7` |
| `fix/order-concurrency-bug-34` | `34` |

숫자가 없으면 이슈 연결 없이 PR을 생성합니다.

### 2단계: 커밋 이력으로 변경 내용 파악

```bash
git log origin/dev..HEAD --oneline
git diff origin/dev...HEAD --stat
```

커밋 목록과 변경 파일을 보고 PR 제목과 본문을 작성할 내용을 파악합니다.

### 3단계: 이슈 정보 조회 (이슈 번호가 있는 경우)

`mcp__github__issue_read` 도구로 해당 이슈 제목과 작업 내용을 확인합니다:
- **owner**: Teach-D
- **repo**: coin-battle
- **issue_number**: 추출한 이슈 번호

이슈 제목을 PR 제목 작성에 활용합니다.

### 4단계: PR 제목 및 본문 작성

**PR 제목 형식**:
```
[feat|fix|...]: <이슈 제목 또는 변경 핵심 요약> #<이슈번호>
```

이슈 번호가 없으면 `#<번호>` 생략.

**PR 본문 형식**:

```markdown
## 개요

[변경 목적을 2~3문장으로 설명]

## 변경 내용

- [주요 변경 사항 1]
- [주요 변경 사항 2]
- [주요 변경 사항 3]

## 관련 이슈

Close #<이슈번호>
```

이슈 번호가 없으면 `## 관련 이슈` 섹션 생략.

### 5단계: PR 생성

`mcp__github__create_pull_request` 도구로 PR을 생성합니다:
- **owner**: Teach-D
- **repo**: coin-battle
- **title**: 위에서 작성한 제목
- **body**: 위에서 작성한 본문
- **head**: 현재 브랜치명
- **base**: `dev`

### 6단계: 사용자 확인

PR 생성 결과(URL 포함)를 보여주고 머지 여부를 확인합니다:

```
PR이 생성되었습니다:
#[번호] [PR 제목]
→ https://github.com/Teach-D/coin-battle/pull/[번호]

dev 브랜치로 머지할까요? (y/n)
```

사용자가 거절하면 머지 없이 종료합니다.

### 7단계: PR 머지

`mcp__github__merge_pull_request` 도구로 머지합니다:
- **owner**: Teach-D
- **repo**: coin-battle
- **pull_number**: 생성된 PR 번호
- **merge_method**: `squash`

### 8단계: 로컬 dev 동기화

머지 완료 후 로컬을 최신 상태로 업데이트합니다:

```bash
git checkout dev && git pull origin dev
```

---

## 완료 메시지 형식

```
✅ 머지 완료

- PR: #[번호] [PR 제목]
  → https://github.com/Teach-D/coin-battle/pull/[번호]
- 머지: [현재브랜치] → dev (squash)
- 로컬: dev 브랜치로 전환 및 pull 완료
```

---

## 주의사항

- PR 생성 전 현재 브랜치가 원격에 push되어 있는지 확인하세요. 없으면 먼저 push를 제안하세요:
  ```bash
  git push --set-upstream origin <브랜치명>
  ```
- CI 체크가 실패 중이어도 머지를 강제하지 않습니다. 사용자에게 알리고 판단을 맡깁니다.
- 이미 같은 head/base 조합의 PR이 열려 있으면 중복 생성하지 말고 기존 PR URL을 안내합니다.
- merge_method는 항상 `squash`를 사용합니다 (커밋 히스토리 정리).

## 2026-05-11

### Backend — 배틀 종료 결과 처리

- [x] `BattleResult.kt` 엔티티 설계 + `BattleResultRepository.kt` (수익률, 승자, 참가자별 최종 평가금액 저장)
- [x] `BattleEndService.kt` 구현 — 배틀 종료 시 수익률 계산, 승자 결정, DB 저장, 랭킹 갱신 트리거
- [x] `GET /api/battles/{battleId}/result` 엔드포인트 추가 (`BattleController.kt`)

### Frontend — 배틀 결과 카드 UI

- [x] `BattleResultCard.tsx` 컴포넌트 — 수익률, 승패 표시, 참가자 순위 목록
- [x] `BattleRoom.tsx` 업데이트 — 배틀 종료 WebSocket 메시지 수신 시 결과 카드 전환
- [x] 결과 카드 공유 버튼 — Web Share API 활용 (`navigator.share`) + 이미지 다운로드 fallback

### Backend — 배틀 실시간 랭킹 브로드캐스트

- [x] `BattleRankingScheduler.kt` 생성 — 5초 간격으로 IN_PROGRESS 배틀 목록 조회
- [x] 참가자별 평가금액 계산 — `user.balance + Σ(openPositions.evaluatedValue(currentPrice))`
- [x] STOMP `/topic/battle/{battleId}` 에 `RANK_UPDATE` 메시지 브로드캐스트

### Frontend — 배틀 실시간 랭킹 확인

- [x] `BattleRoom.tsx` RANK_UPDATE 수신 동작 검증 — 기존 구독 로직 정상 작동 확인 및 필요 시 수정

### Backend — 바이낸스 WebSocket 시세 통합

- [x] `BinanceWebSocketClient.kt` 신규 생성 — 바이낸스 800개+ 글로벌 코인 실시간 수신
- [x] 기존 Pub/Sub 파이프라인 재사용 — `TickerPubSubPublisher` → Redis Pub/Sub → STOMP 브로드캐스트
- [x] 바이낸스 마켓 코드 변환 — `BTCUSDT` → `USDT-BTC` 별도 네임스페이스 처리

### Frontend — 바이낸스 시세 UI 대응

- [x] `MarketListPage.tsx` — USDT 마켓 코인 가격 표시 처리 (`$` 단위 포맷 분기)
- [x] 거래소 구분 배지 또는 마켓 필터 탭 추가 (업비트 / 바이낸스)

### Backend — 펀딩비 정산 구현

- [x] `FundingRateScheduler.kt` 생성 — `@Scheduled(cron = "0 0 0,8,16 * * *")` + `@Async` 8시간 주기
- [x] 오픈 LONG/SHORT 포지션에 펀딩비 부과 로직 구현 (바이낸스 펀딩비 기준 적용)
- [x] 정산 실패 시 로깅 후 계속 진행 (부분 실패 허용)

### Backend — 슬리피지 시뮬레이션

- [ ] `OrderService.kt` 주문 금액별 슬리피지 보정 로직 구현 (100만 이하: 없음 / 100만~500만: ±0.05% / 500만~전액: ±0.1~0.3% 랜덤)
- [ ] `OrderResponse.kt`에 `executedPrice` 필드 추가 — 슬리피지 적용 체결가 반환

### Backend — 강제청산 WebSocket 알림

- [ ] `LiquidationScheduler.kt` 청산 실행 직후 STOMP `/user/{userId}/queue/notification` 에 청산 알림 메시지 발송 추가
- [ ] `LiquidationNotificationMessage.kt` DTO 생성 — `ticker`, `liquidatedAt`, `lossAmount` 필드

### Frontend — 포트폴리오 페이지

- [ ] `PortfolioPage.tsx` 생성 — 잔고, 오픈 포지션 목록, 평가금액, 거래 내역 표시 (`usePortfolio` 훅 활용)
- [ ] `App.tsx` `/portfolio` 라우트에 `PortfolioPage` 연결 (현재 빈 `<div>` 대체)

### Frontend — 배틀 결과 공유 페이지

- [ ] `BattleResultPage.tsx` 생성 — `/result/:battleId` 라우트, `BattleResultCard.tsx` 재사용 + `GET /api/battles/{battleId}/result` 호출
- [ ] `App.tsx` `/result/:battleId` 라우트에 `BattleResultPage` 연결 (현재 빈 `<div>` 대체)

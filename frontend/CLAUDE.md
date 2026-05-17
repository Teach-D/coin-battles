# CoinBattle Frontend — CLAUDE.md

> 전체 프로젝트 개요, 아키텍처 흐름, 설계 결정은 루트 `../CLAUDE.md` 참조.

## 명령어

```bash
npm run dev        # 개발 서버 (Vite HMR)
npm run build      # tsc + Vite 프로덕션 빌드
npm run lint       # ESLint 검사
npm run preview    # 빌드 결과 미리보기
```

## 환경변수 (.env)

```
VITE_API_URL=http://localhost:8080
VITE_WS_URL=ws://localhost:8080/ws
```

## 폴더 구조

```
src/
├── lib/
│   ├── api.ts          # axios 인스턴스 (JWT 인터셉터, 토큰 자동 갱신)
│   └── stomp.ts        # STOMP 싱글턴 클라이언트 (connectStomp / disconnectStomp)
├── store/
│   ├── authStore.ts    # Zustand: accessToken / refreshToken (localStorage 동기화)
│   ├── orderStore.ts   # Zustand: 주문 폼 상태
│   ├── tickerStore.ts  # Zustand: 실시간 시세 맵
│   └── useBattleStore.ts  # Zustand: 배틀 진행 상태
├── hooks/
│   ├── useMarketTickers.ts      # TanStack Query: 전체 시세 목록
│   ├── useTickerSubscription.ts # STOMP /topic/coin/{ticker} 구독
│   ├── useOrder.ts              # 매수/매도 mutation
│   ├── usePortfolio.ts          # TanStack Query: 포트폴리오 조회
│   └── useRanking.ts            # TanStack Query: 랭킹 조회
├── components/
│   ├── AuthGuard.tsx       # 미인증 시 /login 리다이렉트
│   ├── CandleChart.tsx     # lightweight-charts 캔들 차트
│   ├── OrderPanel.tsx      # 매수/매도 패널
│   └── PortfolioWidget.tsx # 잔고/포지션 위젯
├── pages/
│   ├── LoginPage.tsx       # Google OAuth2 로그인
│   ├── OAuth2Callback.tsx  # OAuth2 콜백 처리, 토큰 저장
│   ├── MarketListPage.tsx  # 코인 시세 목록 (/)
│   ├── CoinDetailPage.tsx  # 코인 상세 + 주문 (/coin/:ticker)
│   ├── BattlePage.tsx      # 배틀 목록 + 생성/매칭 (/battles)
│   ├── BattleRoom.tsx      # 배틀 진행 화면 (/battles/:battleId)
│   └── RankingPage.tsx     # 랭킹 (/ranking)
└── types/
    └── index.ts            # 공유 타입 정의 (API 요청/응답, 도메인 타입)
```

## 핵심 패턴

### API 호출 — TanStack Query + axios
```typescript
const { data } = useQuery({
  queryKey: ['portfolio'],
  queryFn: () => api.get<ApiResponse<PortfolioResponse>>('/api/orders/portfolio').then(r => r.data.data),
});
```
- 모든 응답은 `ApiResponse<T>` 래퍼: `{ data: T, message: string }`
- axios 인스턴스(`lib/api.ts`)가 JWT 자동 주입 + 401 시 토큰 갱신 처리

### 실시간 시세 구독 — STOMP
```typescript
// lib/stomp.ts의 싱글턴 클라이언트 사용
await connectStomp();
client.subscribe(`/topic/coin/${ticker}`, (msg) => {
  const ticker = JSON.parse(msg.body);
  useTickerStore.getState().updateTicker(ticker);
});
```
- `connectStomp()` 중복 호출 안전: 이미 연결된 경우 즉시 resolve
- Authorization 헤더는 `connectHeaders`에서 동적으로 읽음 (getter 패턴)

### 인증 — Zustand authStore
```typescript
const { setTokens, clearAuth, isAuthenticated } = useAuthStore();
```
- `localStorage`와 Zustand 상태를 동기화 (초기값도 localStorage에서 로드)
- `isAuthenticated()` → `!!accessToken`

### 주문 멱등성 키 — uuid
```typescript
import { v4 as uuidv4 } from 'uuid';
const idempotencyKey = uuidv4();  // 요청 전 생성, 재시도 시 동일 키 재사용
```

## 라우팅

```
/                   MarketListPage    (AuthGuard)
/coin/:ticker       CoinDetailPage    (AuthGuard)
/battles            BattlePage        (AuthGuard)
/battles/:battleId  BattleRoom        (AuthGuard)
/ranking            RankingPage       (AuthGuard)
/login              LoginPage
/oauth2/callback    OAuth2Callback
```

## 코딩 규칙

- 주석 없음 — 코드로 의도 표현
- 컴포넌트 파일은 named export (`export function` / `export const`)
- 훅은 `use` 접두사, 파일명도 `useFoo.ts`
- Zustand 스토어: `useXxxStore` (훅 형태)
- 타입 정의: 도메인 타입은 `types/index.ts`, 컴포넌트 내부 타입은 파일 내 인라인
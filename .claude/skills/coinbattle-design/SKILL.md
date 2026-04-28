---
name: coinbattle-design
description: |
  CoinBattle 프로젝트의 디자인 시스템을 적용해 React/TSX 페이지·섹션·컴포넌트를 생성합니다.
  "디자인해줘", "화면 만들어줘", "페이지 만들어줘", "UI 만들어줘", "컴포넌트 만들어줘",
  "섹션 디자인", "화면 디자인", "레이아웃 잡아줘", "UI 짜줘", "스타일 잡아줘" 등
  CoinBattle 화면 구현 요청이 오면 반드시 이 스킬을 사용하세요.
  기능 설명만 있어도, 와이어프레임 없이도, 백엔드/프론트엔드 어느 쪽에서 요청하든 사용합니다.
---

## 역할

요청받은 페이지/섹션/컴포넌트의 색상·스타일·구조를 결정하고 CoinBattle 디자인 토큰을 적용한 TSX 코드를 생성한다.
생성 전에 어떤 레이아웃 패턴이 콘텐츠에 가장 잘 맞는지 판단한다.

---

## 파일 코드 구조

모든 컴포넌트는 이 순서를 따른다:

```typescript
"use client";

// ============================================================================
// CUSTOMIZATION — 브랜드 컬러·텍스트·이미지만 이 구역에서 수정
// ============================================================================

const COLORS = { ... } as const;
const CONTENT = { ... } as const;   // 모든 텍스트·데이터

// ============================================================================
// END CUSTOMIZATION
// ============================================================================

import { motion } from "motion/react";
import { IconName } from "lucide-react";

// 내부 SVG 컴포넌트 (필요한 경우)
const SomeIcon = () => ( ... );

// Props 인터페이스
interface ComponentNameProps {
  mode?: "light" | "dark";
}

export default function ComponentName({ mode = "dark" }: ComponentNameProps) {
  const colors = COLORS[mode];
  return ( ... );
}
```

CUSTOMIZATION 블록을 항상 명시해서 수정 포인트를 한눈에 알 수 있게 한다.

---

## 디자인 토큰

### 색상

```typescript
const COLORS = {
  light: {
    accent: "#FF6B35",
    accentHover: "#E55A2B",
    accentSecondary: "#2DD4BF",
    background: "#FFFFFF",
    cardBackground: "#F9FAFB",
    cardBorder: "#E5E7EB",
    titleText: "#111827",
    bodyText: "#6B7280",
    labelText: "#6B7280",
    iconBackground: "#F3F4F6",
    iconColor: "#374151",
    border: "rgba(0, 0, 0, 0.1)",
    bgCard: "rgba(0, 0, 0, 0.05)",
  },
  dark: {
    accent: "#FF6B35",
    accentHover: "#E55A2B",
    accentSecondary: "#2DD4BF",
    background: "#0C0C0D",
    cardBackground: "#18181B",
    cardBorder: "#27272A",
    titleText: "#FFFFFF",
    bodyText: "#A1A1AA",
    labelText: "#71717A",
    iconBackground: "#27272A",
    iconColor: "#A1A1AA",
    border: "rgba(255, 255, 255, 0.1)",
    bgCard: "rgba(255, 255, 255, 0.05)",
  },
} as const;
```

**의미별 색상 용도:**
| 색상 | 용도 |
|------|------|
| `#FF6B35` (오렌지) | 액션 버튼, 강조 텍스트, 그라디언트 시작점 |
| `#2DD4BF` (틸) | 수익/양수 지표, 보조 강조, 매수 탭 |
| `red-500` | 손실/음수 지표, 매도 탭 |
| `#0C0C0D` | 섹션 배경 |
| `#18181B` | 카드 배경 |
| `#A1A1AA` | 본문 텍스트 |
| `#27272A` | 카드 테두리 |

### 그라디언트

```css
/* 주 그라디언트 — 장식용 원형, 아이콘 배경 */
bg-gradient-to-br from-orange-500 via-pink-500 to-cyan-400

/* 텍스트 강조 그라디언트 */
bg-gradient-to-r from-orange-400 to-orange-500 bg-clip-text text-transparent
```

---

## 타이포그래피

| 용도 | 클래스 |
|------|--------|
| 히어로 h1 | `text-5xl md:text-6xl lg:text-7xl font-bold leading-tight tracking-tight text-white` |
| 섹션 h2 | `text-3xl md:text-4xl lg:text-5xl font-bold text-white` |
| 카드 h3 | `text-xl md:text-2xl font-bold text-white` |
| 본문 | `text-base md:text-lg text-zinc-400` |
| 소형 본문 | `text-sm text-zinc-400` |
| 섹션 레이블 | `text-xs font-semibold tracking-widest uppercase text-zinc-500` |
| 강조 텍스트 | `bg-gradient-to-r from-orange-400 to-orange-500 bg-clip-text text-transparent` |
| 하이라이트 강조 | 텍스트 뒤 `<span>` + `absolute` 배경 + `-rotate-1` 기울기 |
| 모노스페이스 | `font-mono text-green-400` (코드 스니펫) / `font-mono` (타이머) |

---

## 애니메이션 패턴

항상 `motion/react`를 사용한다 (framer-motion 아님).

```typescript
// 히어로 직접 진입
initial={{ opacity: 0, y: 20 }}
animate={{ opacity: 1, y: 0 }}
transition={{ duration: 0.6 }}

// 스크롤 트리거 (섹션 내 요소)
initial={{ opacity: 0, y: 40 }}
whileInView={{ opacity: 1, y: 0 }}
viewport={{ once: true }}
transition={{ duration: 0.6, delay: 0.1 * index }}

// 스크롤 트리거 (더 큰 여백)
viewport={{ once: true, margin: "-100px" }}

// 좌우 진입
initial={{ opacity: 0, x: -20 }}   // 왼쪽에서
initial={{ opacity: 0, x: 20 }}    // 오른쪽에서
animate={{ opacity: 1, x: 0 }}
transition={{ duration: 0.6, delay: 0.3 }}

// 버튼 인터랙션
whileHover={{ scale: 1.02 }}
whileTap={{ scale: 0.98 }}

// 호버 효과 (Tailwind)
hover:brightness-110   // 버튼 밝기
hover:bg-white/10      // 반투명 배경
```

stagger: 요소가 여럿일 때 `delay: 0.1 * index` 또는 0.1 → 0.2 → 0.3 씩 증가.

---

## 레이아웃 패턴

### 섹션 래퍼

```tsx
<section className="relative w-full bg-black py-20 md:py-28">
  <div className="mx-auto max-w-7xl px-6 md:px-8">
    {/* 내용 */}
  </div>
</section>
```

### 섹션 헤더 (중앙 정렬)

```tsx
<div className="text-center mb-16">
  <motion.p className="text-xs font-semibold tracking-widest uppercase text-zinc-500 mb-4">
    레이블
  </motion.p>
  <motion.h2 className="text-3xl md:text-5xl font-bold text-white mb-6">
    섹션 제목
  </motion.h2>
  <motion.p className="mx-auto max-w-3xl text-lg text-white/60">
    설명 텍스트
  </motion.p>
</div>
```

### 그리드 시스템

```tsx
{/* 기본 3열 그리드 */}
<div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">

{/* 벤토 그리드 (대형 카드 포함) */}
<div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
  <div className="col-span-1 lg:col-span-2 ...">  {/* 대형 카드 */}
  <div className="...">                           {/* 일반 카드 */}
```

### 2열 교차 레이아웃 (텍스트 + 비주얼 번갈아)

sections 배열에 `flipped` 프로퍼티를 두고 RTL 트릭으로 순서를 뒤집는다:

```tsx
{sections.map((section) => (
  <div
    key={section.id}
    className={`grid items-center gap-12 lg:grid-cols-2 ${section.flipped ? "lg:[direction:rtl]" : ""}`}
  >
    <div className="lg:[direction:ltr]">  {/* 텍스트 */}
      <h2>{section.title}</h2>
      <p>{section.description}</p>
    </div>
    <div className="lg:[direction:ltr]">  {/* 비주얼 */}
      {/* 차트, 카드, 코드 블록 등 */}
    </div>
  </div>
))}
```

### 타임라인 / 단계 표시

수평 타임라인으로 흐름을 나타낼 때:

```tsx
<div className="flex flex-col items-center justify-center gap-4 md:flex-row md:gap-0">
  {items.map((item, index) => (
    <div key={item.label} className="flex items-center">
      <div className="flex flex-col items-center gap-2 px-8 py-4">
        <span
          className="text-xs font-semibold uppercase tracking-wider"
          style={{ color: item.isCurrent ? colors.accentSecondary : "rgba(255,255,255,0.5)" }}
        >
          {item.label}
        </span>
        <span className="text-sm text-white/70">{item.text}</span>
      </div>
      {index < items.length - 1 && (
        <div className="hidden h-px w-16 bg-white/20 md:block" />
      )}
    </div>
  ))}
</div>
```

### 장식용 그라디언트 원형

```tsx
<div className="pointer-events-none absolute inset-0">
  {/* 링 스타일 원형 */}
  <div className="absolute -left-32 top-1/3 h-[500px] w-[500px]">
    <div className="absolute inset-0 rounded-full bg-gradient-to-br from-orange-500 via-pink-500 to-cyan-400 opacity-80 blur-sm" />
    <div className="absolute inset-8 rounded-full bg-black" />
  </div>
  {/* 솔리드 원형 */}
  <div className="absolute -right-20 bottom-0 h-[400px] w-[400px]">
    <div className="absolute inset-0 rounded-full bg-gradient-to-tl from-cyan-400 via-pink-400 to-orange-500 opacity-80 blur-sm" />
  </div>
</div>
```

---

## 컴포넌트 패턴

### 카드

```tsx
{/* 기본 카드 */}
<div className="rounded-2xl bg-zinc-900 p-8 border border-zinc-800">

{/* 반투명 글래스 카드 */}
<div className="rounded-lg border border-white/10 bg-white/5 p-4 transition-all hover:bg-white/10">

{/* 호버 효과 카드 */}
<div
  className="rounded-2xl p-6 md:p-8 border transition-colors hover:border-opacity-70"
  style={{ backgroundColor: colors.cardBackground, borderColor: colors.cardBorder }}
>
```

### 버튼

```tsx
{/* 주 CTA 버튼 */}
<motion.button
  whileHover={{ scale: 1.02 }}
  whileTap={{ scale: 0.98 }}
  className="flex items-center gap-2 rounded-full bg-[#FF6B35] px-8 py-4 text-lg font-medium text-white transition-all hover:bg-[#E55A2B] hover:shadow-lg hover:shadow-orange-500/25"
>
  액션 텍스트 <ArrowRight className="w-5 h-5" />
</motion.button>

{/* 보조 버튼 (아웃라인) */}
<button className="rounded-full border border-zinc-700 px-8 py-4 text-lg font-medium text-white transition-all hover:border-zinc-500">
  보조 액션
</button>

{/* 글래스 버튼 */}
<a className="flex items-center gap-2 rounded-lg border border-white/20 bg-white/5 px-6 py-3 font-medium text-white backdrop-blur-sm transition-all hover:bg-white/10">
  링크 텍스트
</a>
```

### 아이콘 배경

```tsx
{/* 그라디언트 */}
<div className="w-12 h-12 rounded-xl bg-gradient-to-br from-orange-500 via-pink-500 to-cyan-400 flex items-center justify-center">
  <Icon className="w-6 h-6 text-white" strokeWidth={1.5} />
</div>

{/* 단색 */}
<div className="w-12 h-12 rounded-xl flex items-center justify-center"
  style={{ backgroundColor: colors.iconBackground }}>
  <Icon className="w-6 h-6" style={{ color: colors.iconColor }} strokeWidth={1.5} />
</div>
```

### 소셜 프루프

아바타 그룹 + 별점 조합으로 신뢰도를 나타낼 때:

```tsx
<div className="flex flex-col md:flex-row items-center gap-4">
  {/* 아바타 그룹 */}
  <div className="flex -space-x-3">
    {avatars.map((avatar, i) => (
      <div
        key={i}
        className="w-12 h-12 rounded-full border-2 overflow-hidden"
        style={{ borderColor: colors.background }}
      >
        <img src={avatar.src} alt={avatar.alt} className="w-full h-full object-cover" />
      </div>
    ))}
  </div>
  {/* 별점 + 카운트 */}
  <div className="flex flex-col items-center md:items-start gap-1">
    <div className="flex gap-0.5">
      {[...Array(5)].map((_, i) => (
        <Star key={i} className="w-5 h-5 fill-yellow-400 text-yellow-400" />
      ))}
    </div>
    <p className="text-sm" style={{ color: colors.bodyText }}>
      <span className="font-semibold" style={{ color: colors.titleText }}>7,890</span> 명이 참가 중
    </p>
  </div>
</div>
```

### 코드 스니펫 / 명령어 표시

```tsx
{/* 인라인 코드 */}
<code className="inline-block rounded-lg border border-white/10 bg-white/5 px-4 py-2 font-mono text-sm text-green-400">
  git clone ship-fast
</code>

{/* 키보드 단축키 힌트 */}
<div className="flex items-center gap-2 text-sm" style={{ color: colors.bodyText }}>
  <span>Press</span>
  <kbd className="px-2 py-1 rounded text-sm font-mono"
    style={{ backgroundColor: colors.cardBackground, color: colors.titleText }}>
    Ctrl+K
  </kbd>
  <span>to search</span>
</div>
```

### 진행 상황 / 비교 패널

전·후 비교나 단계별 공개를 나타낼 때:

```tsx
<div className="flex flex-col items-center gap-4 md:flex-row">
  {/* 전 패널 */}
  <div className="flex-1 rounded-lg border border-white/10 bg-white/5 p-4">
    <span className="mb-3 block text-xs uppercase text-white/50">Before</span>
    <div className="space-y-2 text-sm text-white/60">
      <div>항목 A</div>
      <div>항목 B</div>
    </div>
  </div>
  <div className="text-2xl text-white/30">→</div>
  {/* 후 패널 */}
  <div className="rounded-lg border border-white/10 bg-white/5 p-4">
    <span className="mb-3 block text-xs uppercase text-white/50">After</span>
    <div className="text-sm font-medium text-white">결과</div>
  </div>
</div>
```

### 강조 매칭 패널 (하이라이트된 항목 + 전후 맥락)

```tsx
<div className="space-y-4">
  {/* 이전 맥락 */}
  <div>
    <span className="mb-2 block text-xs uppercase text-white/50">이전</span>
    <div className="space-y-1 text-sm text-white/60">
      <div>관련 항목 1</div>
      <div>관련 항목 2</div>
    </div>
  </div>
  {/* 강조 항목 */}
  <div className="rounded-lg border-2 border-amber-500/30 bg-amber-500/10 p-3">
    <span className="mb-1 block text-xs uppercase text-amber-500">현재</span>
    <div className="text-sm font-medium text-white">핵심 항목</div>
  </div>
  {/* 이후 맥락 */}
  <div>
    <span className="mb-2 block text-xs uppercase text-white/50">이후</span>
    <div className="space-y-1 text-sm text-white/60">
      <div>결과 항목 1</div>
      <div>결과 항목 2</div>
    </div>
  </div>
</div>
```

### CoinBattle 전용 지표 뱃지

```tsx
{/* 수익 */}
<span className="text-[#2DD4BF] font-semibold">+12.4%</span>

{/* 손실 */}
<span className="text-red-500 font-semibold">-8.2%</span>

{/* 레버리지 뱃지 */}
<span className="rounded-full bg-orange-500/20 px-2 py-0.5 text-xs font-semibold text-[#FF6B35]">10x</span>

{/* 랭킹 순위 */}
<span className="text-yellow-400 font-bold">#1</span>   {/* 골드 */}
<span className="text-zinc-300 font-bold">#2</span>     {/* 실버 */}
<span className="text-amber-700 font-bold">#3</span>    {/* 브론즈 */}
```

---

## CoinBattle 페이지별 가이드

### 홈 / 랜딩
- 구조: Hero → Features(벤토 그리드) → How It Works(타임라인 또는 2열 교차) → CTA
- Hero: 장식 원형 + 큰 헤드라인 + 하이라이트 강조 + 오렌지 CTA 버튼 + 소셜 프루프
- 핵심 메시지: "10분 배틀", "짧은 도파민", "승부"

### 배틀 로비 / 매칭
- 진행 중 배틀 카드: `bg-zinc-900 border border-zinc-800 rounded-2xl`
- 타이머: 오렌지 텍스트 + `font-mono`
- 참가자 아바타: 그라디언트 원형

### 거래 화면
- 매수 탭: 틸(`#2DD4BF`) 강조
- 매도 탭: 레드(`red-500`) 강조
- 레버리지 슬라이더: 오렌지 액센트
- 주문 버튼: 매수=틸, 매도=레드

### 결과 / 순위
- 승리: 오렌지/틸 그라디언트 배경
- 패배: 어두운 zinc + 레드 액센트
- 수익률: 틸(양수) / 레드(음수) + 큰 폰트 + font-bold
- 랭킹 1위: 골드 강조, 2위: 실버, 3위: 브론즈

---

## 실행 순서

1. 요청을 분석해 **어떤 페이지/섹션/컴포넌트**인지 파악
2. 콘텐츠 특성에 맞는 레이아웃 패턴 선택:
   - 비교/흐름 → 타임라인 또는 2열 교차
   - 기능 나열 → 벤토 그리드
   - 단일 강조 → 히어로 + 소셜 프루프
3. 위 디자인 토큰·패턴을 적용해 TSX 코드 생성
4. 파일 상단에 `// CUSTOMIZATION` 블록 배치
5. COLORS, CONTENT 분리 구조 유지
6. `mode` prop 기본값은 `"dark"`
7. 모든 진입 요소에 motion 애니메이션 적용
8. 생성한 파일 경로를 사용자에게 알린다

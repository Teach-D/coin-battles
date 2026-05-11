// ============================================================================
// CUSTOMIZATION — 브랜드 컬러·텍스트만 이 구역에서 수정
// ============================================================================

const COLORS = {
  background: '#0C0C0D',
  cardBg: '#18181B',
  cardBorder: '#27272A',
  accent: '#FF6B35',
  profit: '#2DD4BF',
  loss: '#f87171',
} as const;

// ============================================================================
// END CUSTOMIZATION
// ============================================================================

import { motion } from 'motion/react';
import { usePortfolio } from '../hooks/usePortfolio';
import type { Position, RecentOrder } from '../types';

function formatKRW(value: number): string {
  return value.toLocaleString('ko-KR');
}

function formatRate(rate: number): string {
  const sign = rate > 0 ? '+' : '';
  return `${sign}${rate.toFixed(2)}%`;
}

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function SkeletonCard() {
  return (
    <div
      className="rounded-2xl p-4 border animate-pulse"
      style={{ backgroundColor: COLORS.cardBg, borderColor: COLORS.cardBorder }}
    >
      <div className="flex justify-between mb-3">
        <div className="w-16 h-4 bg-zinc-800 rounded" />
        <div className="w-12 h-4 bg-zinc-800 rounded" />
      </div>
      <div className="w-24 h-6 bg-zinc-800 rounded mb-2" />
      <div className="w-32 h-3 bg-zinc-800 rounded" />
    </div>
  );
}

function SummarySection({
  balance,
  totalAsset,
  totalPnl,
  totalPnlRate,
}: {
  balance: number;
  totalAsset: number;
  totalPnl: number;
  totalPnlRate: number;
}) {
  const isPnlPositive = totalPnl >= 0;

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35 }}
      className="mx-4 mt-4 rounded-2xl border p-5"
      style={{ backgroundColor: COLORS.cardBg, borderColor: COLORS.cardBorder }}
    >
      <p className="text-xs font-semibold tracking-widest uppercase text-zinc-500 mb-4">
        자산 요약
      </p>

      <div className="mb-4">
        <p className="text-xs text-zinc-500 mb-1">총 평가금액</p>
        <p className="text-2xl font-bold text-white font-mono">
          {formatKRW(totalAsset)}
          <span className="text-sm text-zinc-500 ml-1">원</span>
        </p>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div
          className="rounded-xl p-3"
          style={{ backgroundColor: 'rgba(255,255,255,0.03)' }}
        >
          <p className="text-xs text-zinc-500 mb-1">가용 잔고</p>
          <p className="text-sm font-semibold text-white font-mono">
            {formatKRW(balance)}원
          </p>
        </div>

        <div
          className="rounded-xl p-3"
          style={{
            backgroundColor: isPnlPositive
              ? 'rgba(45, 212, 191, 0.06)'
              : 'rgba(248, 113, 113, 0.06)',
          }}
        >
          <p className="text-xs text-zinc-500 mb-1">미실현 손익</p>
          <p
            className="text-sm font-semibold font-mono"
            style={{ color: isPnlPositive ? COLORS.profit : COLORS.loss }}
          >
            {isPnlPositive ? '+' : ''}
            {formatKRW(totalPnl)}원
          </p>
          <p
            className="text-xs font-mono mt-0.5"
            style={{ color: isPnlPositive ? COLORS.profit : COLORS.loss }}
          >
            {formatRate(totalPnlRate)}
          </p>
        </div>
      </div>
    </motion.div>
  );
}

function PositionCard({ position, index }: { position: Position; index: number }) {
  const isLong = position.direction === 'LONG';
  const isPnlPositive = position.unrealizedPnl >= 0;

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25, delay: 0.05 + index * 0.06 }}
      className="rounded-2xl border p-4"
      style={{ backgroundColor: COLORS.cardBg, borderColor: COLORS.cardBorder }}
    >
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <span className="text-sm font-bold text-white">{position.ticker}</span>
          <span
            className="rounded-full px-2 py-0.5 text-xs font-semibold"
            style={{
              backgroundColor: isLong
                ? 'rgba(45, 212, 191, 0.15)'
                : 'rgba(248, 113, 113, 0.15)',
              color: isLong ? COLORS.profit : COLORS.loss,
            }}
          >
            {isLong ? 'LONG' : 'SHORT'}
          </span>
          <span
            className="rounded-full px-2 py-0.5 text-xs font-semibold"
            style={{
              backgroundColor: 'rgba(255, 107, 53, 0.15)',
              color: COLORS.accent,
            }}
          >
            {position.leverage}x
          </span>
        </div>

        <div className="text-right">
          <p
            className="text-sm font-bold font-mono"
            style={{ color: isPnlPositive ? COLORS.profit : COLORS.loss }}
          >
            {formatRate(position.unrealizedPnlRate)}
          </p>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-2">
        <div>
          <p className="text-xs text-zinc-500 mb-0.5">진입가</p>
          <p className="text-xs font-mono text-zinc-300">{formatKRW(position.averagePrice)}</p>
        </div>
        <div>
          <p className="text-xs text-zinc-500 mb-0.5">현재가</p>
          <p className="text-xs font-mono text-zinc-300">{formatKRW(position.currentPrice)}</p>
        </div>
        <div className="text-right">
          <p className="text-xs text-zinc-500 mb-0.5">평가금액</p>
          <p className="text-xs font-mono text-white">{formatKRW(position.evaluatedValue)}원</p>
        </div>
      </div>
    </motion.div>
  );
}

function OrderRow({ order, index }: { order: RecentOrder; index: number }) {
  const isBuy = order.side === 'BUY';

  return (
    <motion.div
      initial={{ opacity: 0, x: -8 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ duration: 0.2, delay: 0.05 + index * 0.04 }}
      className="flex items-center px-4 py-3 border-b border-zinc-800/60 hover:bg-zinc-800/30 transition-colors"
    >
      <span className="text-sm text-white w-20 shrink-0 font-mono">{order.ticker}</span>

      <span
        className="rounded-full px-2 py-0.5 text-xs font-semibold w-12 text-center shrink-0"
        style={{
          backgroundColor: isBuy
            ? 'rgba(45, 212, 191, 0.15)'
            : 'rgba(248, 113, 113, 0.15)',
          color: isBuy ? COLORS.profit : COLORS.loss,
        }}
      >
        {isBuy ? '매수' : '매도'}
      </span>

      <div className="flex-1 min-w-0 mx-3 text-right">
        <p className="text-xs text-zinc-300 font-mono">{formatKRW(order.executedPrice)}원</p>
        <p className="text-xs text-zinc-500 font-mono">{formatKRW(order.amount)}원</p>
      </div>

      <span className="text-xs text-zinc-600 shrink-0 w-24 text-right">
        {formatDateTime(order.createdAt)}
      </span>
    </motion.div>
  );
}

function EmptyState({ message }: { message: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-12 gap-2">
      <span className="text-2xl">📭</span>
      <p className="text-sm text-zinc-600">{message}</p>
    </div>
  );
}

export function PortfolioPage() {
  const { data, isLoading, isError, refetch } = usePortfolio(true);

  const portfolio = data?.portfolio;
  const positions = data?.positions ?? [];
  const recentOrders = data?.recentOrders ?? [];
  const openPositions = positions.filter((p) => p.status === 'OPEN');

  return (
    <div className="min-h-screen text-white flex flex-col" style={{ backgroundColor: COLORS.background }}>
      <header
        className="sticky top-0 z-10 backdrop-blur border-b px-4 py-3"
        style={{
          backgroundColor: `${COLORS.background}f5`,
          borderColor: COLORS.cardBorder,
        }}
      >
        <div className="max-w-2xl mx-auto">
          <h1
            className="text-lg font-extrabold bg-clip-text text-transparent"
            style={{ backgroundImage: `linear-gradient(to right, ${COLORS.accent}, #facc15)` }}
          >
            내 포트폴리오
          </h1>
        </div>
      </header>

      {isError && (
        <div className="flex flex-col items-center justify-center flex-1 gap-4 py-20">
          <p className="text-zinc-500 text-sm">포트폴리오를 불러올 수 없습니다</p>
          <button
            onClick={() => refetch()}
            className="px-5 py-2 text-sm font-semibold text-white rounded-xl transition-colors"
            style={{ backgroundColor: COLORS.accent }}
          >
            다시 시도
          </button>
        </div>
      )}

      {!isError && (
        <main className="max-w-2xl mx-auto w-full flex-1 pb-10">
          {isLoading || !portfolio ? (
            <div className="mx-4 mt-4 space-y-4">
              <div
                className="rounded-2xl p-5 border animate-pulse"
                style={{ backgroundColor: COLORS.cardBg, borderColor: COLORS.cardBorder }}
              >
                <div className="w-16 h-3 bg-zinc-800 rounded mb-4" />
                <div className="w-40 h-7 bg-zinc-800 rounded mb-4" />
                <div className="grid grid-cols-2 gap-3">
                  <div className="rounded-xl p-3 bg-zinc-800/50">
                    <div className="w-12 h-3 bg-zinc-700 rounded mb-2" />
                    <div className="w-24 h-4 bg-zinc-700 rounded" />
                  </div>
                  <div className="rounded-xl p-3 bg-zinc-800/50">
                    <div className="w-12 h-3 bg-zinc-700 rounded mb-2" />
                    <div className="w-20 h-4 bg-zinc-700 rounded" />
                  </div>
                </div>
              </div>
              <SkeletonCard />
              <SkeletonCard />
            </div>
          ) : (
            <>
              <SummarySection
                balance={portfolio.balance}
                totalAsset={portfolio.totalAsset}
                totalPnl={portfolio.totalPnl}
                totalPnlRate={portfolio.totalPnlRate}
              />

              <section className="mt-6 mx-4">
                <h2 className="text-sm font-semibold text-zinc-400 mb-3">
                  오픈 포지션
                  <span className="ml-2 text-xs text-zinc-600">
                    {openPositions.length}개
                  </span>
                </h2>

                {openPositions.length === 0 ? (
                  <EmptyState message="보유 중인 포지션이 없습니다" />
                ) : (
                  <div className="space-y-3">
                    {openPositions.map((pos, i) => (
                      <PositionCard key={pos.positionId} position={pos} index={i} />
                    ))}
                  </div>
                )}
              </section>

              <section className="mt-6">
                <div className="mx-4 mb-3">
                  <h2 className="text-sm font-semibold text-zinc-400">최근 거래 내역</h2>
                </div>

                {recentOrders.length === 0 ? (
                  <EmptyState message="거래 내역이 없습니다" />
                ) : (
                  <div
                    className="rounded-2xl border mx-4 overflow-hidden"
                    style={{ backgroundColor: '#0f0f10', borderColor: COLORS.cardBorder }}
                  >
                    <div
                      className="flex items-center px-4 py-2 text-xs text-zinc-600 border-b"
                      style={{ borderColor: COLORS.cardBorder }}
                    >
                      <span className="w-20 shrink-0">코인</span>
                      <span className="w-12 shrink-0">구분</span>
                      <span className="flex-1 text-right">체결가 / 금액</span>
                      <span className="w-24 text-right shrink-0">시각</span>
                    </div>
                    {recentOrders.map((order, i) => (
                      <OrderRow key={order.orderId} order={order} index={i} />
                    ))}
                  </div>
                )}
              </section>
            </>
          )}
        </main>
      )}
    </div>
  );
}

// ============================================================================
// CUSTOMIZATION
// ============================================================================

const COLORS = {
  longBadge: 'bg-teal-500/20 text-[#2DD4BF]',
  shortBadge: 'bg-red-500/20 text-red-400',
  leverageBadge: 'bg-orange-500/20 text-[#FF6B35]',
  profit: 'text-[#2DD4BF]',
  loss: 'text-red-400',
  card: 'bg-zinc-900 border border-zinc-800',
  closeButton: 'bg-red-600/80 hover:bg-red-600 text-white',
} as const;

// ============================================================================
// END CUSTOMIZATION
// ============================================================================

import { motion } from 'motion/react';
import { Loader2 } from 'lucide-react';
import { useOrderStore } from '../store/orderStore';
import { useSellOrder } from '../hooks/useOrder';
import { usePortfolio } from '../hooks/usePortfolio';
import type { Position } from '../types';

const formatKRW = (n: number) => new Intl.NumberFormat('ko-KR').format(Math.round(n));
const pnlColor = (pnl: number) => (pnl >= 0 ? COLORS.profit : COLORS.loss);
const pnlSign = (pnl: number) => (pnl >= 0 ? '+' : '');

interface PositionRowProps {
  position: Position;
  onClose: (positionId: number) => void;
  isClosing: boolean;
}

function PositionRow({ position, onClose, isClosing }: PositionRowProps) {
  const isLong = position.direction === 'LONG';
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className="rounded-xl border border-zinc-700 bg-zinc-800/50 p-3 space-y-2"
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="font-bold text-white text-sm">{position.ticker}</span>
          <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${isLong ? COLORS.longBadge : COLORS.shortBadge}`}>
            {isLong ? 'LONG' : 'SHORT'}
          </span>
          <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${COLORS.leverageBadge}`}>
            {position.leverage}x
          </span>
        </div>
        <motion.button
          whileHover={{ scale: 1.04 }}
          whileTap={{ scale: 0.96 }}
          onClick={() => onClose(position.positionId)}
          disabled={isClosing}
          className={`text-xs font-semibold px-3 py-1.5 rounded-lg transition-colors flex items-center gap-1 disabled:opacity-50 disabled:cursor-not-allowed ${COLORS.closeButton}`}
        >
          {isClosing ? <Loader2 className="w-3 h-3 animate-spin" /> : null}
          전량 청산
        </motion.button>
      </div>

      <div className="grid grid-cols-3 gap-2 text-xs">
        <div>
          <p className="text-zinc-500 mb-0.5">수량</p>
          <p className="text-zinc-200 font-medium">{position.quantity.toFixed(6)}</p>
        </div>
        <div>
          <p className="text-zinc-500 mb-0.5">평균단가</p>
          <p className="text-zinc-200 font-medium">{formatKRW(position.averagePrice)}</p>
        </div>
        <div>
          <p className="text-zinc-500 mb-0.5">현재가</p>
          <p className="text-zinc-200 font-medium">{formatKRW(position.currentPrice)}</p>
        </div>
      </div>

      <div className="flex items-center justify-between pt-1 border-t border-zinc-700">
        <div>
          <p className="text-zinc-500 text-xs mb-0.5">미실현 손익</p>
          <p className={`text-sm font-bold ${pnlColor(position.unrealizedPnl)}`}>
            {pnlSign(position.unrealizedPnl)}{formatKRW(position.unrealizedPnl)} KRW
            <span className="text-xs font-semibold ml-1">
              ({pnlSign(position.unrealizedPnlRate)}{position.unrealizedPnlRate.toFixed(2)}%)
            </span>
          </p>
        </div>
        <div className="text-right">
          <p className="text-zinc-500 text-xs mb-0.5">강제청산가</p>
          <p className="text-red-400 text-xs font-semibold">{formatKRW(position.liquidationPrice)} KRW</p>
        </div>
      </div>
    </motion.div>
  );
}

export function PortfolioWidget() {
  usePortfolio();

  const balance = useOrderStore((s) => s.balance);
  const totalAsset = useOrderStore((s) => s.totalAsset);
  const totalPnl = useOrderStore((s) => s.totalPnl);
  const totalPnlRate = useOrderStore((s) => s.totalPnlRate);
  const positions = useOrderStore((s) => s.positions);

  const sellOrder = useSellOrder();
  const openPositions = positions.filter((p) => p.status === 'OPEN');

  const handleClose = (positionId: number) => {
    sellOrder.mutate({ positionId, closeRatio: 1.0 });
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay: 0.1 }}
      className={`${COLORS.card} rounded-2xl p-4 space-y-4`}
    >
      <div className="grid grid-cols-3 gap-3">
        <div className="rounded-xl bg-zinc-800/60 border border-zinc-700 p-3">
          <p className="text-zinc-500 text-xs mb-1">총 자산</p>
          <p className="text-white font-bold text-sm">{formatKRW(totalAsset)}</p>
          <p className="text-zinc-500 text-xs">KRW</p>
        </div>
        <div className="rounded-xl bg-zinc-800/60 border border-zinc-700 p-3">
          <p className="text-zinc-500 text-xs mb-1">사용 가능</p>
          <p className="text-white font-bold text-sm">{formatKRW(balance)}</p>
          <p className="text-zinc-500 text-xs">KRW</p>
        </div>
        <div className="rounded-xl bg-zinc-800/60 border border-zinc-700 p-3">
          <p className="text-zinc-500 text-xs mb-1">미실현 손익</p>
          <p className={`font-bold text-sm ${pnlColor(totalPnl)}`}>
            {pnlSign(totalPnl)}{formatKRW(totalPnl)}
          </p>
          <p className={`text-xs font-semibold ${pnlColor(totalPnlRate)}`}>
            {pnlSign(totalPnlRate)}{totalPnlRate.toFixed(2)}%
          </p>
        </div>
      </div>

      <div>
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm font-bold text-white">보유 포지션</h3>
          <span className="text-xs text-zinc-500">{openPositions.length}개 오픈</span>
        </div>

        {openPositions.length === 0 ? (
          <div className="rounded-xl border border-dashed border-zinc-700 py-8 flex flex-col items-center justify-center gap-2">
            <p className="text-zinc-500 text-sm">오픈 포지션 없음</p>
            <p className="text-zinc-600 text-xs">주문 패널에서 매수/매도를 시작하세요</p>
          </div>
        ) : (
          <div className="space-y-2">
            {openPositions.map((position, index) => (
              <motion.div
                key={position.positionId}
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.3, delay: 0.05 * index }}
              >
                <PositionRow
                  position={position}
                  onClose={handleClose}
                  isClosing={sellOrder.isPending}
                />
              </motion.div>
            ))}
          </div>
        )}
      </div>
    </motion.div>
  );
}

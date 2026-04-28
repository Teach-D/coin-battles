import { useState, useRef, useEffect } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { useMarketTickers } from '../hooks/useMarketTickers';
import { useTickerSubscription } from '../hooks/useTickerSubscription';
import { useTickerStore, type Ticker } from '../store/tickerStore';

function formatPrice(price: number): string {
  return price.toLocaleString('ko-KR');
}

function formatRate(rate: number): string {
  const sign = rate > 0 ? '+' : '';
  return `${sign}${(rate * 100).toFixed(2)}%`;
}

function formatVolume(volume: number): string {
  if (volume >= 1_000_000_000_000) return `${(volume / 1_000_000_000_000).toFixed(0)}조`;
  if (volume >= 100_000_000) return `${(volume / 100_000_000).toFixed(0)}억`;
  if (volume >= 10_000) return `${(volume / 10_000).toFixed(0)}만`;
  return volume.toLocaleString('ko-KR');
}

function getCoinSymbol(market: string): string {
  return market.replace('KRW-', '');
}

function TickerRow({ ticker }: { ticker: Ticker }) {
  const prevPriceRef = useRef<number>(ticker.tradePrice);
  const [flash, setFlash] = useState<'up' | 'down' | null>(null);

  useEffect(() => {
    if (ticker.tradePrice !== prevPriceRef.current) {
      setFlash(ticker.tradePrice > prevPriceRef.current ? 'up' : 'down');
      prevPriceRef.current = ticker.tradePrice;
      const id = setTimeout(() => setFlash(null), 500);
      return () => clearTimeout(id);
    }
  }, [ticker.tradePrice]);

  const changeColor =
    ticker.change === 'RISE'
      ? 'text-[#2DD4BF]'
      : ticker.change === 'FALL'
      ? 'text-red-500'
      : 'text-zinc-400';

  const flashBg =
    flash === 'up'
      ? 'bg-[#2DD4BF]/10'
      : flash === 'down'
      ? 'bg-red-500/10'
      : '';

  return (
    <motion.div
      className={`flex items-center px-4 py-3 border-b border-zinc-800 transition-colors duration-300 ${flashBg} hover:bg-zinc-800/50 cursor-pointer`}
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.15 }}
    >
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="font-bold text-white text-sm">{getCoinSymbol(ticker.market)}</span>
          <span className="text-xs text-zinc-600">{ticker.market}</span>
        </div>
      </div>
      <div className={`text-right w-36 font-mono font-semibold text-sm ${changeColor}`}>
        ₩{formatPrice(ticker.tradePrice)}
      </div>
      <div className={`text-right w-24 font-mono text-sm ${changeColor}`}>
        {formatRate(ticker.changeRate)}
      </div>
      <div className="text-right w-28 text-zinc-500 font-mono text-xs hidden sm:block">
        {formatVolume(ticker.accTradePrice24h)}
      </div>
    </motion.div>
  );
}

function SkeletonRow() {
  return (
    <div className="flex items-center px-4 py-3 border-b border-zinc-800 animate-pulse">
      <div className="flex-1 space-y-1">
        <div className="h-4 bg-zinc-800 rounded w-16" />
        <div className="h-3 bg-zinc-900 rounded w-24" />
      </div>
      <div className="w-36 h-4 bg-zinc-800 rounded" />
      <div className="w-24 h-4 bg-zinc-800 rounded ml-4" />
      <div className="w-28 h-4 bg-zinc-800 rounded ml-4 hidden sm:block" />
    </div>
  );
}

export function MarketListPage() {
  const [search, setSearch] = useState('');
  const { isLoading, isError, refetch } = useMarketTickers();
  const tickers = useTickerStore((s) => s.tickers);
  const markets = Array.from(tickers.keys());

  useTickerSubscription(markets);

  const filtered = Array.from(tickers.values()).filter((t) =>
    getCoinSymbol(t.market).toLowerCase().includes(search.toLowerCase())
  );
  const sorted = [...filtered].sort((a, b) => b.accTradePrice24h - a.accTradePrice24h);

  return (
    <div className="min-h-screen bg-[#0C0C0D] text-white">
      <header className="sticky top-0 z-10 bg-[#0C0C0D]/95 backdrop-blur border-b border-zinc-800 px-4 py-3">
        <div className="max-w-2xl mx-auto flex items-center gap-3">
          <h1 className="text-lg font-extrabold bg-gradient-to-r from-orange-400 to-yellow-400 bg-clip-text text-transparent">
            CoinBattle
          </h1>
          <span className="text-xs bg-[#2DD4BF]/20 text-[#2DD4BF] px-2 py-0.5 rounded-full font-medium">
            실시간
          </span>
        </div>
      </header>

      <main className="max-w-2xl mx-auto">
        <div className="px-4 py-3">
          <input
            type="text"
            placeholder="코인 검색..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full bg-zinc-900 border border-zinc-800 rounded-xl px-4 py-2 text-sm text-white placeholder-zinc-600 focus:outline-none focus:border-zinc-600 transition-colors"
          />
        </div>

        <div className="flex items-center px-4 py-2 text-xs text-zinc-600 border-b border-zinc-800">
          <div className="flex-1">코인</div>
          <div className="w-36 text-right">현재가</div>
          <div className="w-24 text-right">등락률</div>
          <div className="w-28 text-right hidden sm:block">거래대금</div>
        </div>

        {isError && (
          <div className="flex flex-col items-center justify-center py-20 gap-4">
            <p className="text-zinc-500 text-sm">시세를 불러올 수 없습니다</p>
            <button
              onClick={() => refetch()}
              className="px-5 py-2 bg-orange-500 hover:bg-orange-400 active:bg-orange-600 text-white text-sm font-semibold rounded-xl transition-colors"
            >
              다시 시도
            </button>
          </div>
        )}

        {isLoading && (
          <div>
            {Array.from({ length: 20 }).map((_, i) => (
              <SkeletonRow key={i} />
            ))}
          </div>
        )}

        {!isLoading && !isError && (
          <AnimatePresence>
            {sorted.map((ticker) => (
              <TickerRow key={ticker.market} ticker={ticker} />
            ))}
          </AnimatePresence>
        )}

        {!isLoading && !isError && sorted.length === 0 && search && (
          <p className="text-center py-20 text-zinc-600 text-sm">
            "{search}"에 대한 결과가 없습니다
          </p>
        )}
      </main>
    </div>
  );
}

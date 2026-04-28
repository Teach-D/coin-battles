import { create } from 'zustand';

export interface Ticker {
  market: string;
  tradePrice: number;
  changeRate: number;
  changePrice: number;
  change: 'RISE' | 'FALL' | 'EVEN';
  accTradeVolume24h: number;
  accTradePrice24h: number;
  highPrice?: number;
  lowPrice?: number;
  timestamp?: number;
}

interface TickerState {
  tickers: Map<string, Ticker>;
  setTicker: (ticker: Ticker) => void;
  setTickers: (tickers: Ticker[]) => void;
  clearTickers: () => void;
}

export const useTickerStore = create<TickerState>((set) => ({
  tickers: new Map(),
  setTicker: (ticker) =>
    set((state) => {
      const next = new Map(state.tickers);
      next.set(ticker.market, ticker);
      return { tickers: next };
    }),
  setTickers: (tickers) =>
    set(() => {
      const map = new Map<string, Ticker>();
      tickers.forEach((t) => map.set(t.market, t));
      return { tickers: map };
    }),
  clearTickers: () => set({ tickers: new Map() }),
}));
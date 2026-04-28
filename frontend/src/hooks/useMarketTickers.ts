import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api';
import { useTickerStore, type Ticker } from '../store/tickerStore';

interface TickerListApiResponse {
  data: {
    tickers: Ticker[];
  };
}

export function useMarketTickers() {
  const setTickers = useTickerStore((s) => s.setTickers);

  return useQuery({
    queryKey: ['market', 'tickers'],
    queryFn: async () => {
      const response = await api.get<TickerListApiResponse>('/api/market/tickers');
      const tickers = response.data.data.tickers;
      setTickers(tickers);
      return tickers;
    },
    staleTime: 0,
    gcTime: 30_000,
  });
}
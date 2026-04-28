import { useEffect, useRef } from 'react';
import type { StompSubscription } from '@stomp/stompjs';
import { connectStomp, getStompClient } from '../lib/stomp';
import { useTickerStore, type Ticker } from '../store/tickerStore';

export function useTickerSubscription(markets: string[]) {
  const setTicker = useTickerStore((s) => s.setTicker);
  const subscriptionsRef = useRef<StompSubscription[]>([]);
  const marketsKey = markets.join(',');

  useEffect(() => {
    if (markets.length === 0) return;
    let mounted = true;

    connectStomp().then(() => {
      if (!mounted) return;
      const client = getStompClient();
      subscriptionsRef.current = markets.map((market) =>
        client.subscribe(`/topic/ticker/${market}`, (message) => {
          try {
            const ticker: Ticker = JSON.parse(message.body);
            setTicker(ticker);
          } catch {
            // ignore malformed frames
          }
        })
      );
    });

    return () => {
      mounted = false;
      subscriptionsRef.current.forEach((sub) => sub.unsubscribe());
      subscriptionsRef.current = [];
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [marketsKey]);
}

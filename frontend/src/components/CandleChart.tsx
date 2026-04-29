// ============================================================================
// CUSTOMIZATION — 차트 색상·테마만 이 구역에서 수정
// ============================================================================

const CHART_COLORS = {
  background: '#0C0C0D',
  textColor: '#71717A',
  gridLines: '#27272A',
  crosshairLine: '#52525B',
  crosshairLabel: '#3F3F46',
  candleRise: '#2DD4BF',
  candleFall: '#f87171',
} as const;

// ============================================================================
// END CUSTOMIZATION
// ============================================================================

import { useEffect, useRef } from 'react';
import {
  createChart,
  CandlestickSeries,
  type IChartApi,
  type ISeriesApi,
  type UTCTimestamp,
} from 'lightweight-charts';
import type { CandleData } from '../types';

interface CandleChartProps {
  candles: CandleData[];
  height?: number;
}

export function CandleChart({ candles, height = 360 }: CandleChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const seriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    const chart = createChart(containerRef.current, {
      width: containerRef.current.clientWidth,
      height,
      layout: {
        background: { color: CHART_COLORS.background },
        textColor: CHART_COLORS.textColor,
      },
      grid: {
        vertLines: { color: CHART_COLORS.gridLines },
        horzLines: { color: CHART_COLORS.gridLines },
      },
      crosshair: {
        vertLine: {
          color: CHART_COLORS.crosshairLine,
          labelBackgroundColor: CHART_COLORS.crosshairLabel,
        },
        horzLine: {
          color: CHART_COLORS.crosshairLine,
          labelBackgroundColor: CHART_COLORS.crosshairLabel,
        },
      },
      rightPriceScale: {
        borderColor: CHART_COLORS.gridLines,
      },
      timeScale: {
        borderColor: CHART_COLORS.gridLines,
        timeVisible: true,
        secondsVisible: false,
      },
    });

    const series = chart.addSeries(CandlestickSeries, {
      upColor: CHART_COLORS.candleRise,
      downColor: CHART_COLORS.candleFall,
      borderUpColor: CHART_COLORS.candleRise,
      borderDownColor: CHART_COLORS.candleFall,
      wickUpColor: CHART_COLORS.candleRise,
      wickDownColor: CHART_COLORS.candleFall,
    });

    chartRef.current = chart;
    seriesRef.current = series;

    const resizeObserver = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (entry && chartRef.current) {
        chartRef.current.applyOptions({ width: entry.contentRect.width });
      }
    });
    resizeObserver.observe(containerRef.current);

    return () => {
      resizeObserver.disconnect();
      chart.remove();
      chartRef.current = null;
      seriesRef.current = null;
    };
  }, [height]);

  useEffect(() => {
    if (!seriesRef.current || candles.length === 0) return;

    const sorted = [...candles]
      .sort((a, b) => a.time - b.time)
      .map((c) => ({
        time: c.time as UTCTimestamp,
        open: c.open,
        high: c.high,
        low: c.low,
        close: c.close,
      }));

    seriesRef.current.setData(sorted);
    chartRef.current?.timeScale().fitContent();
  }, [candles]);

  return <div ref={containerRef} style={{ height }} className="w-full" />;
}

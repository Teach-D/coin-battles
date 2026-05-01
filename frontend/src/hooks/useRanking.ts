import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api';
import type { ApiResponse, RankingEntry, MyRanking } from '../types';

export function useRankingSeason() {
  return useQuery({
    queryKey: ['ranking', 'season'],
    queryFn: async () => {
      const response = await api.get<ApiResponse<RankingEntry[]>>('/api/ranking/season');
      return response.data.data;
    },
    staleTime: 30_000,
    gcTime: 60_000,
    refetchInterval: 60_000,
  });
}

export function useRankingDaily() {
  return useQuery({
    queryKey: ['ranking', 'daily'],
    queryFn: async () => {
      const response = await api.get<ApiResponse<RankingEntry[]>>('/api/ranking/daily');
      return response.data.data;
    },
    staleTime: 30_000,
    gcTime: 60_000,
    refetchInterval: 60_000,
  });
}

export function useMyRanking(enabled: boolean) {
  return useQuery({
    queryKey: ['ranking', 'me'],
    queryFn: async () => {
      const response = await api.get<ApiResponse<MyRanking>>('/api/ranking/me');
      return response.data.data;
    },
    enabled,
    staleTime: 30_000,
    gcTime: 60_000,
    refetchInterval: 60_000,
  });
}

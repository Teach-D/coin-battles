import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api';
import type { ApiResponse, UserStatsResponse } from '../types';

export function useUserStats() {
  return useQuery({
    queryKey: ['user', 'stats'],
    queryFn: async () => {
      const res = await api.get<ApiResponse<UserStatsResponse>>('/api/users/me/stats');
      return res.data.data;
    },
    staleTime: 30_000,
    gcTime: 60_000,
  });
}

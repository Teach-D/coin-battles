import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api';
import type { ApiResponse, UserProfileResponse } from '../types';

export function useUserProfile() {
  return useQuery({
    queryKey: ['user', 'profile'],
    queryFn: async () => {
      const res = await api.get<ApiResponse<UserProfileResponse>>('/api/users/me');
      return res.data.data;
    },
    staleTime: 60_000,
    gcTime: 300_000,
  });
}

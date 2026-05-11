import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api';
import type { ApiResponse, BattleResultResponse } from '../types';

export function useBattleResult(battleId: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: ['battle-result', battleId],
    queryFn: () =>
      api
        .get<ApiResponse<BattleResultResponse>>(`/api/battles/${battleId}/result`)
        .then((r) => r.data.data),
    enabled: !!battleId && enabled,
    staleTime: Infinity,
  });
}

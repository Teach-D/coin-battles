// ============================================================================
// CUSTOMIZATION
// ============================================================================
const ERROR_MESSAGES: Record<string, string> = {
  INVITE_CODE_NOT_FOUND: '초대 링크가 만료되었거나 존재하지 않습니다',
  BATTLE_ALREADY_FINISHED: '이미 종료된 배틀입니다',
  ALREADY_JOINED_BATTLE: '이미 이 배틀에 참가 중입니다',
  BATTLE_LOCK_TIMEOUT: '배틀 처리 중 일시적 오류가 발생했습니다. 다시 시도해 주세요',
};

const FALLBACK_ERROR_MESSAGE = '참가 처리 중 오류가 발생했습니다';
// ============================================================================
// END CUSTOMIZATION
// ============================================================================

import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { motion } from 'motion/react';
import { api } from '../lib/api';
import type { ApiResponse, JoinByInviteResponse } from '../types';

type JoinState =
  | { phase: 'loading' }
  | { phase: 'error'; message: string };

function resolveErrorMessage(error: unknown): string {
  if (
    error &&
    typeof error === 'object' &&
    'response' in error
  ) {
    const axiosError = error as { response?: { data?: { message?: string } } };
    const serverMessage = axiosError.response?.data?.message ?? '';
    return ERROR_MESSAGES[serverMessage] ?? FALLBACK_ERROR_MESSAGE;
  }
  return FALLBACK_ERROR_MESSAGE;
}

export function JoinByInvitePage() {
  const { inviteCode } = useParams<{ inviteCode: string }>();
  const navigate = useNavigate();
  const [state, setState] = useState<JoinState>({ phase: 'loading' });

  useEffect(() => {
    if (!inviteCode) {
      setState({ phase: 'error', message: FALLBACK_ERROR_MESSAGE });
      return;
    }

    api
      .post<ApiResponse<JoinByInviteResponse>>(`/api/battles/join/${inviteCode}`)
      .then(({ data }) => {
        navigate(`/battles/${data.data.battleId}`, { replace: true });
      })
      .catch((error) => {
        setState({ phase: 'error', message: resolveErrorMessage(error) });
      });
  }, [inviteCode, navigate]);

  return (
    <div className="min-h-screen bg-[#0C0C0D] flex flex-col items-center justify-center px-6">
      {state.phase === 'loading' && <LoadingView />}
      {state.phase === 'error' && <ErrorView message={state.message} />}
    </div>
  );
}

function LoadingView() {
  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className="flex flex-col items-center gap-6"
    >
      <div className="relative w-14 h-14">
        <div className="absolute inset-0 rounded-full border-2 border-zinc-800" />
        <div className="absolute inset-0 rounded-full border-2 border-transparent border-t-[#2DD4BF] animate-spin" />
      </div>
      <div className="text-center">
        <p className="text-base font-semibold text-white">배틀 참가 중...</p>
        <p className="text-sm text-zinc-500 mt-1">잠시만 기다려 주세요</p>
      </div>
    </motion.div>
  );
}

function ErrorView({ message }: { message: string }) {
  const navigate = useNavigate();

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className="flex flex-col items-center gap-6 text-center max-w-xs"
    >
      <div className="w-14 h-14 rounded-full bg-red-500/10 border border-red-500/30 flex items-center justify-center">
        <span className="text-red-400 text-2xl font-bold">!</span>
      </div>
      <div>
        <p className="text-base font-semibold text-white">참가 실패</p>
        <p className="text-sm text-zinc-400 mt-2 leading-relaxed">{message}</p>
      </div>
      <motion.button
        whileTap={{ scale: 0.97 }}
        onClick={() => navigate('/battles', { replace: true })}
        className="px-8 py-3 rounded-2xl bg-orange-500 hover:bg-orange-400 text-white text-sm font-semibold transition-colors"
      >
        배틀 목록으로
      </motion.button>
    </motion.div>
  );
}

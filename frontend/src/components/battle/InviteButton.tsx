// ============================================================================
// CUSTOMIZATION
// ============================================================================
const INVITE_COLORS = {
  buttonIdle: "border border-[#2DD4BF]/40 text-[#2DD4BF] hover:bg-[#2DD4BF]/10 hover:border-[#2DD4BF]/70",
  buttonLoading: "border border-zinc-700 text-zinc-500 cursor-not-allowed",
  toastSuccess: "bg-[#2DD4BF] text-[#0C0C0D]",
  toastError: "bg-red-500 text-white",
} as const;

const INVITE_CONTENT = {
  buttonIdle: "친구 초대",
  buttonLoading: "링크 생성 중...",
  toastCopied: "링크 복사됨",
  toastShared: "공유 완료",
  toastError: "링크 생성 실패",
} as const;
// ============================================================================
// END CUSTOMIZATION
// ============================================================================

import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { api } from '../../lib/api';
import type { ApiResponse, InviteCodeResponse } from '../../types';

interface InviteButtonProps {
  battleId: string;
}

type ToastState = {
  message: string;
  variant: 'success' | 'error';
} | null;

export function InviteButton({ battleId }: InviteButtonProps) {
  const [isLoading, setIsLoading] = useState(false);
  const [toast, setToast] = useState<ToastState>(null);

  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), 2000);
    return () => clearTimeout(timer);
  }, [toast]);

  const handleInvite = async () => {
    if (isLoading) return;
    setIsLoading(true);
    try {
      const { data } = await api.post<ApiResponse<InviteCodeResponse>>(
        `/api/battles/${battleId}/invite`
      );
      const { inviteUrl } = data.data;

      if (navigator.share) {
        await navigator.share({
          title: 'CoinBattle 친구 초대',
          text: '10분 트레이딩 배틀에서 나를 이겨봐!',
          url: inviteUrl,
        });
        setToast({ message: INVITE_CONTENT.toastShared, variant: 'success' });
      } else {
        await navigator.clipboard.writeText(inviteUrl);
        setToast({ message: INVITE_CONTENT.toastCopied, variant: 'success' });
      }
    } catch {
      setToast({ message: INVITE_CONTENT.toastError, variant: 'error' });
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <>
      <motion.button
        whileTap={{ scale: 0.97 }}
        onClick={handleInvite}
        disabled={isLoading}
        className={`w-full rounded-2xl py-3.5 text-sm font-semibold flex items-center justify-center gap-2 transition-colors ${
          isLoading ? INVITE_COLORS.buttonLoading : INVITE_COLORS.buttonIdle
        }`}
      >
        {isLoading ? (
          <>
            <div className="w-4 h-4 rounded-full border-2 border-zinc-600 border-t-zinc-400 animate-spin" />
            {INVITE_CONTENT.buttonLoading}
          </>
        ) : (
          <>
            <LinkIcon />
            {INVITE_CONTENT.buttonIdle}
          </>
        )}
      </motion.button>

      <AnimatePresence>
        {toast && (
          <motion.div
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 8 }}
            transition={{ duration: 0.2 }}
            className={`fixed bottom-6 left-1/2 -translate-x-1/2 z-50 px-5 py-2.5 rounded-full text-sm font-semibold shadow-lg whitespace-nowrap ${
              toast.variant === 'success' ? INVITE_COLORS.toastSuccess : INVITE_COLORS.toastError
            }`}
          >
            {toast.message}
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}

function LinkIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
      <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
    </svg>
  );
}

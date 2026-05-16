import { create } from 'zustand';

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  nickname: string | null;
  setTokens: (accessToken: string, refreshToken: string) => void;
  setNickname: (nickname: string) => void;
  clearAuth: () => void;
  isAuthenticated: () => boolean;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: localStorage.getItem('accessToken'),
  refreshToken: localStorage.getItem('refreshToken'),
  nickname: localStorage.getItem('nickname'),
  setTokens: (accessToken, refreshToken) => {
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);
    set({ accessToken, refreshToken });
  },
  setNickname: (nickname) => {
    localStorage.setItem('nickname', nickname);
    set({ nickname });
  },
  clearAuth: () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('nickname');
    set({ accessToken: null, refreshToken: null, nickname: null });
  },
  isAuthenticated: () => !!get().accessToken,
}));

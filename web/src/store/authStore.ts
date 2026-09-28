import { create } from 'zustand';
import type { UserPublicDTO } from '@shared/types';
import { api, ApiError } from '../lib/apiClient';

type AuthStatus = 'loading' | 'setup-required' | 'unauthenticated' | 'authenticated';

interface AuthState {
  status: AuthStatus;
  user: UserPublicDTO | null;
  error: string | null;
  init: () => Promise<void>;
  login: (username: string, password: string) => Promise<void>;
  setup: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  clearError: () => void;
}

function errorMessage(err: unknown, fallback: string): string {
  return err instanceof ApiError ? err.message : fallback;
}

export const useAuthStore = create<AuthState>((set) => ({
  status: 'loading',
  user: null,
  error: null,

  init: async () => {
    try {
      const { setupRequired } = await api.get<{ setupRequired: boolean }>('/auth/setup-required');
      if (setupRequired) {
        set({ status: 'setup-required' });
        return;
      }
      const { user } = await api.get<{ user: UserPublicDTO }>('/auth/me');
      set({ status: 'authenticated', user });
    } catch {
      set({ status: 'unauthenticated' });
    }
  },

  login: async (username, password) => {
    set({ error: null });
    try {
      const { user } = await api.post<{ user: UserPublicDTO }>('/auth/login', { username, password });
      set({ status: 'authenticated', user });
    } catch (err) {
      set({ error: errorMessage(err, 'Login failed') });
      throw err;
    }
  },

  setup: async (username, password) => {
    set({ error: null });
    try {
      const { user } = await api.post<{ user: UserPublicDTO }>('/auth/setup', { username, password });
      set({ status: 'authenticated', user });
    } catch (err) {
      set({ error: errorMessage(err, 'Setup failed') });
      throw err;
    }
  },

  logout: async () => {
    await api.post('/auth/logout');
    set({ status: 'unauthenticated', user: null });
  },

  clearError: () => set({ error: null }),
}));

import { create } from 'zustand';
import type { SettingsDTO } from '@shared/types';
import { api } from '../lib/apiClient';

interface SettingsState {
  settings: SettingsDTO | null;
  fetchSettings: () => Promise<void>;
  updateSettings: (patch: Partial<SettingsDTO>) => Promise<void>;
}

export const useSettingsStore = create<SettingsState>((set) => ({
  settings: null,

  fetchSettings: async () => {
    const { settings } = await api.get<{ settings: SettingsDTO }>('/settings');
    set({ settings });
  },

  updateSettings: async (patch) => {
    const { settings } = await api.patch<{ settings: SettingsDTO }>('/settings', patch);
    set({ settings });
  },
}));

export function useResolvedTheme(): 'light' | 'dark' {
  const settings = useSettingsStore((s) => s.settings);
  const theme = settings?.theme ?? 'system';
  if (theme !== 'system') return theme;
  if (typeof window === 'undefined') return 'light';
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

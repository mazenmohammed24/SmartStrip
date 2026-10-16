import { create } from 'zustand';
import type { RuleDTO, RuleType } from '@shared/types';
import { api } from '../lib/apiClient';

export interface RuleInput {
  type: RuleType;
  stripId: string;
  relayIndex: number | null;
  name: string;
  enabled: boolean;
  params: Record<string, unknown>;
}

interface RulesState {
  rules: RuleDTO[];
  loaded: boolean;
  fetchRules: () => Promise<void>;
  saveRule: (input: RuleInput, id?: string) => Promise<void>;
  setEnabled: (id: string, enabled: boolean) => Promise<void>;
  removeRule: (id: string) => Promise<void>;
}

export const useRulesStore = create<RulesState>((set, get) => ({
  rules: [],
  loaded: false,
  fetchRules: async () => {
    const { rules } = await api.get<{ rules: RuleDTO[] }>('/rules');
    set({ rules, loaded: true });
  },
  saveRule: async (input, id) => {
    if (id) await api.put(`/rules/${id}`, input);
    else await api.post('/rules', input);
    await get().fetchRules();
  },
  setEnabled: async (id, enabled) => {
    set({ rules: get().rules.map((r) => (r.id === id ? { ...r, enabled } : r)) });
    try {
      await api.patch(`/rules/${id}`, { enabled });
    } finally {
      await get().fetchRules();
    }
  },
  removeRule: async (id) => {
    set({ rules: get().rules.filter((r) => r.id !== id) });
    await api.delete(`/rules/${id}`);
  },
}));

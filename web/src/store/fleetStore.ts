import { create } from 'zustand';
import type { ApplianceProfileName, ConnectivityState, DiscoveredStripDTO, RelayStateValue, RelayTimerDTO, RelayTimerMode, StripDTO } from '@shared/types';
import { api } from '../lib/apiClient';

export interface RelayTimerState {
  mode: RelayTimerMode;
  state: RelayStateValue;
  restoreState: RelayStateValue | null;
  restoreAt: string;
}

interface AdoptInput {
  driverStripId: string;
  model: string;
  relayCount: number;
  ipAddress?: string | null;
  suggestedName: string;
  name?: string;
  location?: string | null;
}

interface FleetState {
  strips: StripDTO[];
  relayTimers: Record<string, RelayTimerState>;
  loading: boolean;
  loaded: boolean;
  fetchStrips: () => Promise<void>;
  adoptStrip: (input: AdoptInput) => Promise<StripDTO>;
  removeStrip: (stripId: string) => Promise<void>;
  renameStrip: (stripId: string, name: string) => Promise<void>;
  setStripIcon: (stripId: string, icon: ApplianceProfileName) => Promise<void>;
  renameRelay: (stripId: string, relayIndex: number, name: string) => Promise<void>;
  setRelayIcon: (stripId: string, relayIndex: number, icon: ApplianceProfileName) => Promise<void>;
  setRelay: (stripId: string, relayIndex: number, state: RelayStateValue) => Promise<void>;
  startRelayTimer: (stripId: string, relayIndex: number, state: RelayStateValue, durationMinutes: number, mode: RelayTimerMode) => Promise<void>;
  cancelRelayTimer: (stripId: string, relayIndex: number) => Promise<void>;
  applyRelayTimer: (stripId: string, relayIndex: number, timer: RelayTimerState | null) => void;
  setAllRelays: (stripId: string, state: RelayStateValue) => Promise<void>;
  applyRelayState: (stripId: string, relayIndex: number, state: RelayStateValue, changedAt: string) => void;
  applyPowerTick: (stripId: string, relays: Array<{ relayIndex: number; watts: number }>, totalWatts: number) => void;
  applyConnectivity: (stripId: string, connectivity: ConnectivityState) => void;
  applyStripUpdated: (strip: StripDTO) => void;
  applyStripAdded: (strip: StripDTO) => void;
  applyStripRemoved: (stripId: string) => void;
}

function updateStrip(strips: StripDTO[], stripId: string, updater: (strip: StripDTO) => StripDTO): StripDTO[] {
  return strips.map((s) => (s.id === stripId ? updater(s) : s));
}

function timerMap(timers: RelayTimerDTO[]): Record<string, RelayTimerState> {
  return Object.fromEntries(timers.map((timer) => [`${timer.stripId}:${timer.relayIndex}`, timer]));
}

export const useFleetStore = create<FleetState>((set, get) => ({
  strips: [],
  relayTimers: {},
  loading: false,
  loaded: false,

  fetchStrips: async () => {
    set({ loading: true });
    try {
      const [{ strips }, { timers }] = await Promise.all([
        api.get<{ strips: StripDTO[] }>('/strips'),
        api.get<{ timers: RelayTimerDTO[] }>('/strips/timers/active'),
      ]);
      set({ strips, relayTimers: timerMap(timers), loading: false, loaded: true });
    } catch (err) {
      set({ loading: false });
      throw err;
    }
  },

  adoptStrip: async (input) => {
    const { strip } = await api.post<{ strip: StripDTO }>('/strips', input);
    set((state) => (state.strips.some((s) => s.id === strip.id) ? state : { strips: [...state.strips, strip] }));
    return strip;
  },

  removeStrip: async (stripId) => {
    await api.delete(`/strips/${stripId}`);
    set({ strips: get().strips.filter((s) => s.id !== stripId) });
  },

  renameStrip: async (stripId, name) => {
    const { strip } = await api.patch<{ strip: StripDTO }>(`/strips/${stripId}`, { name });
    set({ strips: updateStrip(get().strips, stripId, () => strip) });
  },

  setStripIcon: async (stripId, icon) => {
    const { strip } = await api.patch<{ strip: StripDTO }>(`/strips/${stripId}`, { icon });
    set({ strips: updateStrip(get().strips, stripId, () => strip) });
  },

  renameRelay: async (stripId, relayIndex, name) => {
    const { strip } = await api.patch<{ strip: StripDTO }>(`/strips/${stripId}/relays/${relayIndex}`, { name });
    set({ strips: updateStrip(get().strips, stripId, () => strip) });
  },

  setRelayIcon: async (stripId, relayIndex, icon) => {
    const { strip } = await api.patch<{ strip: StripDTO }>(`/strips/${stripId}/relays/${relayIndex}`, { icon });
    set({ strips: updateStrip(get().strips, stripId, () => strip) });
  },

  setRelay: async (stripId, relayIndex, state) => {
    const { strip } = await api.patch<{ strip: StripDTO }>(`/strips/${stripId}/relays/${relayIndex}`, { state });
    set({ strips: updateStrip(get().strips, stripId, () => strip) });
  },

  startRelayTimer: async (stripId, relayIndex, state, durationMinutes, mode) => {
    const { strip } = await api.post<{ strip: StripDTO | null; timer: RelayTimerDTO }>(`/strips/${stripId}/relays/${relayIndex}/timer`, { state, durationMinutes, mode });
    if (strip) set({ strips: updateStrip(get().strips, stripId, () => strip) });
    get().applyRelayTimer(stripId, relayIndex, { mode, state, restoreState: mode === 'for' ? (state === 'on' ? 'off' : 'on') : null, restoreAt: new Date(Date.now() + durationMinutes * 60 * 1000).toISOString() });
  },

  cancelRelayTimer: async (stripId, relayIndex) => {
    await api.delete(`/strips/${stripId}/relays/${relayIndex}/timer`);
    get().applyRelayTimer(stripId, relayIndex, null);
  },

  applyRelayTimer: (stripId, relayIndex, timer) => {
    const key = `${stripId}:${relayIndex}`;
    const next = { ...get().relayTimers };
    if (timer) next[key] = timer;
    else delete next[key];
    set({ relayTimers: next });
  },

  setAllRelays: async (stripId, state) => {
    const path = state === 'on' ? 'all-on' : 'all-off';
    const { strip } = await api.post<{ strip: StripDTO }>(`/strips/${stripId}/relays/${path}`);
    set({ strips: updateStrip(get().strips, stripId, () => strip) });
  },

  applyRelayState: (stripId, relayIndex, state, changedAt) => {
    set({
      strips: updateStrip(get().strips, stripId, (strip) => ({
        ...strip,
        relays: strip.relays.map((r) => (r.index === relayIndex ? { ...r, state, lastChangedAt: changedAt } : r)),
      })),
    });
  },

  applyPowerTick: (stripId, relays, totalWatts) => {
    set({
      strips: updateStrip(get().strips, stripId, (strip) => ({
        ...strip,
        totalWatts,
        relays: strip.relays.map((r) => {
          const match = relays.find((x) => x.relayIndex === r.index);
          return match ? { ...r, watts: match.watts } : r;
        }),
      })),
    });
  },

  applyConnectivity: (stripId, connectivity) => {
    set({ strips: updateStrip(get().strips, stripId, (strip) => ({ ...strip, connectivity })) });
  },

  applyStripUpdated: (strip: StripDTO) => {
    set({ strips: updateStrip(get().strips, strip.id, () => strip) });
  },

  applyStripAdded: (strip: StripDTO) => {
    if (get().strips.some((s) => s.id === strip.id)) return;
    set({ strips: [...get().strips, strip] });
  },

  applyStripRemoved: (stripId) => {
    set({ strips: get().strips.filter((s) => s.id !== stripId) });
  },
}));

export type { DiscoveredStripDTO };

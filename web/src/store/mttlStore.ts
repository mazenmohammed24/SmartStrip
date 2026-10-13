import { create } from 'zustand';
import type { MttlProvisionResponse, MttlStatusResponse } from '@shared/types';
import { api, ApiError } from '../lib/apiClient';

/**
 * Diagnostics and provisioning for the direct MTTL-W01 driver.
 *
 * These endpoints only answer while `DRIVER=mttl` is active. `unsupported` holds
 * the 409 the server returns for any other driver so callers can show an honest
 * "not using the MTTL driver" state instead of an endless spinner.
 */
interface MttlState {
  status: MttlStatusResponse | null;
  unsupported: boolean;
  error: string | null;
  provisioning: boolean;
  provisionMessage: string | null;
  fetchStatus: () => Promise<void>;
  provision: (input: { serverIp: string; ssid: string; password: string }) => Promise<void>;
}

export const useMttlStore = create<MttlState>((set) => ({
  status: null,
  unsupported: false,
  error: null,
  provisioning: false,
  provisionMessage: null,

  fetchStatus: async () => {
    set({ error: null });
    try {
      const status = await api.get<MttlStatusResponse>('/mttl/status');
      set({ status, unsupported: false });
    } catch (err) {
      const unsupported = err instanceof ApiError && err.status === 409;
      set({
        unsupported,
        status: null,
        error: unsupported ? null : err instanceof Error ? err.message : 'Failed to read driver status',
      });
    }
  },

  provision: async (input) => {
    set({ provisioning: true, error: null, provisionMessage: null });
    try {
      const result = await api.post<MttlProvisionResponse>('/mttl/provision', input);
      set({ provisioning: false, provisionMessage: result.message });
    } catch (err) {
      set({ provisioning: false, error: err instanceof Error ? err.message : 'Provisioning failed' });
      throw err;
    }
  },
}));

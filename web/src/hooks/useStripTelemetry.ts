import { useEffect, useState } from 'react';
import type { MttlStripTelemetry, MttlStripTelemetryResponse } from '@shared/types';
import { api } from '../lib/apiClient';

interface TelemetryState {
  /** Which strip the readings below belong to. */
  driverStripId: string;
  telemetry: MttlStripTelemetry | null;
}

/**
 * Polls one strip's hardware telemetry. The readings only exist for the direct
 * MTTL-W01 driver, so the server answers 409 for any other driver and this simply
 * stays null, which is how the telemetry card knows to hide itself.
 *
 * Polling pauses while the tab is hidden: the server already keeps its own poll
 * loop running, so a hidden tab has nothing to add.
 *
 * Pass an empty id to disable. Readings are tagged with the id they belong to, so
 * navigating between strips can never show one strip's numbers under another's name.
 */
export function useStripTelemetry(driverStripId: string, intervalMs = 5000): MttlStripTelemetry | null {
  const [state, setState] = useState<TelemetryState | null>(null);

  useEffect(() => {
    if (!driverStripId) return;
    let cancelled = false;

    const load = async () => {
      if (document.hidden) return;
      try {
        const body = await api.get<MttlStripTelemetryResponse>(`/mttl/strips/${encodeURIComponent(driverStripId)}`);
        if (!cancelled) setState({ driverStripId, telemetry: body.telemetry });
      } catch {
        // A failed poll leaves the last known readings on screen rather than blanking them.
      }
    };

    void load();
    const handle = setInterval(() => void load(), intervalMs);
    return () => {
      cancelled = true;
      clearInterval(handle);
    };
  }, [driverStripId, intervalMs]);

  if (!driverStripId || state?.driverStripId !== driverStripId) return null;
  return state.telemetry;
}

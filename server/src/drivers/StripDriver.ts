import type { ConnectivityState, RelayStateValue } from '../../../shared/types.js';

export interface DiscoveredStrip {
  driverStripId: string;
  model: string;
  suggestedName: string;
  ipAddress?: string;
  relayCount: number;
  signalStrength?: number;
}

export interface RelayCommandResult {
  relayIndex: number;
  success: boolean;
  newState: RelayStateValue;
  error?: string;
}

export interface StripStatusSnapshot {
  driverStripId: string;
  connectivity: ConnectivityState;
  relays: Array<{ relayIndex: number; state: RelayStateValue; watts: number }>;
  totalWatts: number;
  timestamp: string;
  relayNames?: Record<number, string>;
}

export type StripDriverEvent =
  | { type: 'status'; snapshot: StripStatusSnapshot }
  | { type: 'connectivity'; driverStripId: string; connectivity: ConnectivityState }
  | {
    type: 'relay-changed';
    driverStripId: string;
    relayIndex: number;
    state: RelayStateValue;
    cause: 'command' | 'schedule' | 'external' | 'overload';
  }
  | { type: 'overload'; driverStripId: string; relayIndex: number; watts: number };

export interface ScanOptions {
  timeoutMs?: number;
  onProgress?: (progress: { percent: number; found: number }) => void;
}

/**
 * The hardware abstraction seam. Every strip driver (mock or real) implements
 * this interface. Nothing outside `drivers/` should talk to hardware directly -
 * routes and services only ever go through a `StripDriver`. Swapping `DRIVER`
 * env var to a real implementation requires no other code changes.
 */
export interface StripDriver {
  readonly name: string;
  init(): Promise<void>;
  shutdown(): Promise<void>;
  scanForStrips(options?: ScanOptions): Promise<DiscoveredStrip[]>;
  adoptStrip(driverStripId: string): Promise<void>;
  releaseStrip(driverStripId: string): Promise<void>;
  setRelay(driverStripId: string, relayIndex: number, state: RelayStateValue): Promise<RelayCommandResult>;
  setAllRelays(driverStripId: string, state: RelayStateValue): Promise<RelayCommandResult[]>;
  getStatus(driverStripId: string): Promise<StripStatusSnapshot>;
  onEvent(listener: (event: StripDriverEvent) => void): void;
  offEvent(listener: (event: StripDriverEvent) => void): void;
}

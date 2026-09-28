import { EventEmitter } from 'node:events';
import type { RelayStateValue } from '../../../../shared/types.js';
import { defaultSettings } from '../../db/schema.js';
import { createLogger } from '../../utils/logger.js';
import type {
  DiscoveredStrip,
  RelayCommandResult,
  ScanOptions,
  StripDriver,
  StripDriverEvent,
  StripStatusSnapshot,
} from '../StripDriver.js';
import { computeWatts } from './applianceProfiles.js';
import { createMockStripPool, type MockStripState } from './mockState.js';

const TICK_MS = 5000;
const OFFLINE_CHANCE_PER_TICK = 0.005;
const OVERLOAD_WATTS = defaultSettings.overloadThresholdWatts;

const log = createLogger('MockDriver');

export class MockDriver implements StripDriver {
  readonly name = 'mock';

  private pool = createMockStripPool();
  private emitter = new EventEmitter();
  private tickHandle: ReturnType<typeof setInterval> | null = null;
  private tickCounter = 0;

  async init(): Promise<void> {
    this.tickHandle = setInterval(() => this.tick(), TICK_MS);
    log.info(`mock driver started, ticking every ${TICK_MS}ms`);
  }

  async shutdown(): Promise<void> {
    if (this.tickHandle) clearInterval(this.tickHandle);
    this.emitter.removeAllListeners();
  }

  async scanForStrips(options?: ScanOptions): Promise<DiscoveredStrip[]> {
    const candidates = [...this.pool.values()].filter((s) => !s.adopted);
    const timeoutMs = options?.timeoutMs ?? 4000;
    const found: DiscoveredStrip[] = [];
    const steps = Math.max(candidates.length, 1);
    const stepDelay = timeoutMs / (steps + 1);

    for (let i = 0; i < candidates.length; i++) {
      await delay(stepDelay);
      found.push(toDiscovered(candidates[i]));
      options?.onProgress?.({ percent: Math.round(((i + 1) / steps) * 100), found: found.length });
    }
    await delay(stepDelay);
    options?.onProgress?.({ percent: 100, found: found.length });
    return found;
  }

  async adoptStrip(driverStripId: string): Promise<void> {
    const strip = this.requireStrip(driverStripId);
    strip.adopted = true;
  }

  async releaseStrip(driverStripId: string): Promise<void> {
    const strip = this.requireStrip(driverStripId);
    strip.adopted = false;
    for (const relay of strip.relays) {
      relay.state = 'off';
      relay.currentWatts = 0;
    }
  }

  async setRelay(driverStripId: string, relayIndex: number, state: RelayStateValue): Promise<RelayCommandResult> {
    const strip = this.requireStrip(driverStripId);
    const relay = strip.relays.find((r) => r.relayIndex === relayIndex);
    if (!relay) {
      return { relayIndex, success: false, newState: 'off', error: 'relay not found' };
    }
    await delay(100 + Math.random() * 300);
    relay.state = state;
    relay.currentWatts = computeWatts(relay.profile, state === 'on', this.tickCounter);
    this.emit({ type: 'relay-changed', driverStripId, relayIndex, state, cause: 'command' });
    return { relayIndex, success: true, newState: state };
  }

  async setAllRelays(driverStripId: string, state: RelayStateValue): Promise<RelayCommandResult[]> {
    const strip = this.requireStrip(driverStripId);
    await delay(150 + Math.random() * 300);
    const results: RelayCommandResult[] = [];
    for (const relay of strip.relays) {
      relay.state = state;
      relay.currentWatts = computeWatts(relay.profile, state === 'on', this.tickCounter);
      this.emit({ type: 'relay-changed', driverStripId, relayIndex: relay.relayIndex, state, cause: 'command' });
      results.push({ relayIndex: relay.relayIndex, success: true, newState: state });
    }
    return results;
  }

  async getStatus(driverStripId: string): Promise<StripStatusSnapshot> {
    const strip = this.requireStrip(driverStripId);
    return snapshotOf(strip);
  }

  onEvent(listener: (event: StripDriverEvent) => void): void {
    this.emitter.on('event', listener);
  }

  offEvent(listener: (event: StripDriverEvent) => void): void {
    this.emitter.off('event', listener);
  }

  private tick(): void {
    this.tickCounter++;
    for (const strip of this.pool.values()) {
      if (!strip.adopted) continue;

      if (strip.connectivity === 'online' && Math.random() < OFFLINE_CHANCE_PER_TICK) {
        strip.connectivity = 'offline';
        strip.offlineTicksRemaining = 1 + Math.floor(Math.random() * 3);
        this.emit({ type: 'connectivity', driverStripId: strip.driverStripId, connectivity: 'offline' });
        continue;
      }
      if (strip.connectivity === 'offline') {
        strip.offlineTicksRemaining -= 1;
        if (strip.offlineTicksRemaining <= 0) {
          strip.connectivity = 'online';
          this.emit({ type: 'connectivity', driverStripId: strip.driverStripId, connectivity: 'online' });
        } else {
          continue;
        }
      }

      for (const relay of strip.relays) {
        relay.currentWatts = computeWatts(relay.profile, relay.state === 'on', this.tickCounter);
        if (relay.state === 'on' && relay.currentWatts > OVERLOAD_WATTS) {
          relay.state = 'off';
          relay.currentWatts = 0;
          this.emit({ type: 'overload', driverStripId: strip.driverStripId, relayIndex: relay.relayIndex, watts: relay.currentWatts });
          this.emit({
            type: 'relay-changed',
            driverStripId: strip.driverStripId,
            relayIndex: relay.relayIndex,
            state: 'off',
            cause: 'overload',
          });
        }
      }

      this.emit({ type: 'status', snapshot: snapshotOf(strip) });
    }
  }

  private requireStrip(driverStripId: string): MockStripState {
    const strip = this.pool.get(driverStripId);
    if (!strip) throw new Error(`Unknown mock strip: ${driverStripId}`);
    return strip;
  }

  private emit(event: StripDriverEvent): void {
    this.emitter.emit('event', event);
  }
}

function toDiscovered(strip: MockStripState): DiscoveredStrip {
  return {
    driverStripId: strip.driverStripId,
    model: strip.model,
    suggestedName: strip.suggestedName,
    ipAddress: strip.ipAddress,
    relayCount: strip.relays.length,
    signalStrength: strip.signalStrength,
  };
}

function snapshotOf(strip: MockStripState): StripStatusSnapshot {
  const relays = strip.relays.map((r) => ({ relayIndex: r.relayIndex, state: r.state, watts: r.currentWatts }));
  return {
    driverStripId: strip.driverStripId,
    connectivity: strip.connectivity,
    relays,
    totalWatts: round2(relays.reduce((sum, r) => sum + r.watts, 0)),
    timestamp: new Date().toISOString(),
  };
}

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

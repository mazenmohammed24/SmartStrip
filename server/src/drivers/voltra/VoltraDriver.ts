import { EventEmitter } from 'node:events';
import type { ConnectivityState, RelayStateValue } from '../../../../shared/types.js';
import { createLogger } from '../../utils/logger.js';
import { voltraTokenManager } from './tokenManager.js';
import type {
  DiscoveredStrip,
  RelayCommandResult,
  ScanOptions,
  StripDriver,
  StripDriverEvent,
  StripStatusSnapshot,
} from '../StripDriver.js';

const log = createLogger('VoltraDriver');
const STREAM_BACKOFF_MIN_MS = 5_000;
const STREAM_BACKOFF_MAX_MS = 60_000;

interface VoltraOutlet {
  state?: unknown;
  watts?: unknown;
  power?: unknown;
}

interface VoltraDevice {
  id?: unknown;
  mac?: unknown;
  name?: unknown;
  model?: unknown;
  online?: unknown;
  outlets?: unknown;
  outletNames?: unknown;
}

interface VoltraStateResponse {
  devices?: unknown;
}

interface VoltraEnergyBucket {
  day?: unknown;
  kwh?: unknown;
  byOutlet?: unknown;
}

export interface VoltraEnergyResponse {
  buckets: Array<{ day: string; kwh: number; byOutlet: Record<string, number> }>;
  totalKwh: number;
  prevTotalKwh: number;
}

export interface VoltraDriverOptions {
  accessToken: string;
  baseUrl: string;
}

/** Controls registered TONLY strips through the official Voltra cloud API. */
export class VoltraDriver implements StripDriver {
  readonly name = 'voltra';

  private readonly baseUrl: string;
  private readonly accessToken: string;
  private readonly emitter = new EventEmitter();
  private readonly devices = new Map<string, VoltraDevice>();
  private pollHandle: ReturnType<typeof setInterval> | null = null;
  private streamAbort: AbortController | null = null;
  private streamRunning = false;
  private streamBackoffMs = STREAM_BACKOFF_MIN_MS;
  private authFailed = false;
  private lastSuccessAt: string | null = null;
  private lastError: string | null = null;

  constructor(options: VoltraDriverOptions) {
    this.baseUrl = options.baseUrl.replace(/\/$/, '');
    this.accessToken = options.accessToken;
  }

  async init(): Promise<void> {
    // A bad or expired token must not take the whole server down: the portal still needs to
    // start so the operator can see the problem and replace the token.
    try {
      await this.refreshState(false);
    } catch (err) {
      log.error('initial Voltra state fetch failed; continuing and retrying in the background', err);
    }
    this.startStream();
    this.pollHandle = setInterval(() => {
      void this.refreshState(true).catch(() => undefined);
    }, 30_000);
  }

  async shutdown(): Promise<void> {
    if (this.pollHandle) clearInterval(this.pollHandle);
    this.streamAbort?.abort();
    this.emitter.removeAllListeners();
  }

  async scanForStrips(options?: ScanOptions): Promise<DiscoveredStrip[]> {
    await this.refreshState(false);
    const devices = [...this.devices.values()];
    options?.onProgress?.({ percent: 100, found: devices.length });
    return devices.map((device) => this.toDiscoveredStrip(device));
  }

  async adoptStrip(driverStripId: string): Promise<void> {
    await this.getDevice(driverStripId);
  }

  async releaseStrip(_driverStripId: string): Promise<void> {
    // The strip remains registered to the user's Voltra account; this only removes it locally.
  }

  async setRelay(driverStripId: string, relayIndex: number, state: RelayStateValue): Promise<RelayCommandResult> {
    const device = await this.getDevice(driverStripId);
    const outlet = String(relayIndex + 1);
    const path = `/api/device/${encodeURIComponent(this.deviceId(device))}/outlet/${outlet}/${state}`;

    for (let attempt = 0; attempt < 3; attempt++) {
      await this.request(path, 'POST');
      if (await this.waitForRelayState(driverStripId, relayIndex, state)) {
        return { relayIndex, success: true, newState: state };
      }
    }

    return { relayIndex, success: false, newState: this.currentRelayState(driverStripId, relayIndex), error: 'Voltra accepted the command but the strip did not confirm the relay change' };
  }

  async setAllRelays(driverStripId: string, state: RelayStateValue): Promise<RelayCommandResult[]> {
    const device = await this.getDevice(driverStripId);
    const outletCount = Object.keys(this.outlets(device)).length || 4;
    const results: RelayCommandResult[] = [];
    for (let relayIndex = 0; relayIndex < outletCount; relayIndex++) {
      results.push(await this.setRelay(driverStripId, relayIndex, state));
    }
    return results;
  }

  async getStatus(driverStripId: string): Promise<StripStatusSnapshot> {
    const device = await this.getDevice(driverStripId);
    return this.toSnapshot(device);
  }

  /**
   * Reports whether the Voltra session is still usable. An access token lives 24 hours, so a
   * silent 401 would otherwise leave the portal showing stale state with no explanation.
   */
  health(): { name: string; authFailed: boolean; lastSuccessAt: string | null; lastError: string | null } {
    return { name: this.name, authFailed: this.authFailed, lastSuccessAt: this.lastSuccessAt, lastError: this.lastError };
  }

  async getEnergy(driverStripId: string, days: number): Promise<VoltraEnergyResponse> {
    const device = await this.getDevice(driverStripId);
    const mac = device.mac;
    if (!mac) throw new Error('Voltra device is missing its MAC');
    const payload = await this.request(`/api/energy?mac=${encodeURIComponent(String(mac))}&days=${days}`, 'GET') as { buckets?: unknown; totalKwh?: unknown; prevTotalKwh?: unknown };
    const buckets = Array.isArray(payload.buckets) ? payload.buckets : [];
    return {
      buckets: buckets.map((bucket) => this.energyBucket(bucket as VoltraEnergyBucket)).filter((bucket) => bucket.day),
      totalKwh: typeof payload.totalKwh === 'number' ? payload.totalKwh : 0,
      prevTotalKwh: typeof payload.prevTotalKwh === 'number' ? payload.prevTotalKwh : 0,
    };
  }

  onEvent(listener: (event: StripDriverEvent) => void): void {
    this.emitter.on('event', listener);
  }

  offEvent(listener: (event: StripDriverEvent) => void): void {
    this.emitter.off('event', listener);
  }

  private async refreshState(emitChanges: boolean): Promise<void> {
    const payload = await this.request('/api/state', 'GET') as VoltraStateResponse;
    if (!Array.isArray(payload.devices)) throw new Error('Voltra returned no device list');
    this.applyStatePayload(payload, emitChanges);
  }

  private applyStatePayload(payload: VoltraStateResponse, emitChanges: boolean): void {
    if (!Array.isArray(payload.devices)) return;

    for (const candidate of payload.devices) {
      if (!candidate || typeof candidate !== 'object') continue;
      const device = candidate as VoltraDevice;
      const driverStripId = this.deviceId(device);
      const previous = this.devices.get(driverStripId);
      this.devices.set(driverStripId, device);

      if (!emitChanges) continue;
      const connectivity = this.connectivity(device);
      if (previous && this.connectivity(previous) !== connectivity) {
        this.emit({ type: 'connectivity', driverStripId, connectivity });
      }
      for (const relay of this.toSnapshot(device).relays) {
        const oldState = this.outlets(previous)?.[String(relay.relayIndex + 1)]?.state;
        if (previous && oldState !== relay.state) {
          this.emit({ type: 'relay-changed', driverStripId, relayIndex: relay.relayIndex, state: relay.state, cause: 'external' });
        }
      }
      this.emit({ type: 'status', snapshot: this.toSnapshot(device) });
    }
  }

  private startStream(): void {
    if (this.streamRunning) return;
    this.streamAbort = new AbortController();
    this.streamRunning = true;
    void this.consumeStream(this.streamAbort.signal)
      .then(() => {
        this.streamBackoffMs = STREAM_BACKOFF_MIN_MS;
      })
      .catch((err) => {
        if (this.streamAbort?.signal.aborted) return;
        log.warn(`Voltra stream error (${err instanceof Error ? err.message : String(err)}); retrying in ${this.streamBackoffMs}ms`);
        this.streamBackoffMs = Math.min(this.streamBackoffMs * 2, STREAM_BACKOFF_MAX_MS);
      })
      .finally(() => {
        this.streamRunning = false;
        if (!this.streamAbort?.signal.aborted) {
          setTimeout(() => this.startStream(), this.streamBackoffMs).unref();
        }
      });
  }

  private async consumeStream(signal: AbortSignal): Promise<void> {
    const token = voltraTokenManager.getToken();
    const response = await fetch(`${this.baseUrl}/api/state/stream`, {
      headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
      signal,
    });
    if (!response.ok || !response.body) throw new Error(`Voltra stream failed (${response.status})`);

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let dataLines: string[] = [];

    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop() ?? '';

      for (const line of lines) {
        if (!line) {
          if (dataLines.length > 0) {
            const chunk = dataLines.join('\n');
            dataLines = [];
            try {
              this.applyStatePayload(JSON.parse(chunk) as VoltraStateResponse, true);
            } catch {
              // Ignore one malformed stream frame; the next valid frame will resync state.
            }
          }
          continue;
        }
        if (line.startsWith(':')) continue;
        if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart());
      }
    }
  }

  private async getDevice(driverStripId: string): Promise<VoltraDevice> {
    let device = this.devices.get(driverStripId);
    if (!device) {
      await this.refreshState(false);
      device = this.devices.get(driverStripId);
    }
    if (!device) throw new Error(`Voltra strip ${driverStripId} is not visible to this account`);
    return device;
  }

  private async waitForRelayState(driverStripId: string, relayIndex: number, state: RelayStateValue): Promise<boolean> {
    const deadline = Date.now() + 15_000;
    while (Date.now() < deadline) {
      await delay(1000);
      await this.refreshState(false);
      if (this.currentRelayState(driverStripId, relayIndex) === state) return true;
    }
    return false;
  }

  private currentRelayState(driverStripId: string, relayIndex: number): RelayStateValue {
    const device = this.devices.get(driverStripId);
    const outlet = this.outlets(device)[String(relayIndex + 1)];
    return outlet?.state === 'on' ? 'on' : 'off';
  }

  private async request(path: string, method: 'GET' | 'POST'): Promise<unknown> {
    const token = voltraTokenManager.getToken();
    const response = await fetch(`${this.baseUrl}${path}`, {
      method,
      headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
      signal: AbortSignal.timeout(30_000),
    });
    if (response.status === 401 || response.status === 403) {
      this.authFailed = true;
      this.lastError = `Voltra rejected the access token (${response.status}). It likely expired.`;
      // Trigger token refresh on 401/403
      await voltraTokenManager.onTokenRejected().catch(() => undefined);
      throw new Error(`Voltra API ${method} ${path} failed (${response.status})`);
    }
    if (!response.ok) {
      this.lastError = `Voltra API ${method} ${path} failed (${response.status})`;
      throw new Error(this.lastError);
    }
    if (this.authFailed) {
      log.info('Voltra access token accepted again');
    }
    this.authFailed = false;
    this.lastError = null;
    this.lastSuccessAt = new Date().toISOString();
    if (response.status === 204) return undefined;
    return response.json();
  }

  private deviceId(device: VoltraDevice): string {
    if (!device.id) throw new Error('Voltra device is missing its id');
    return String(device.id);
  }

  private toDiscoveredStrip(device: VoltraDevice): DiscoveredStrip {
    return {
      driverStripId: this.deviceId(device),
      model: typeof device.model === 'string' ? device.model : 'TONLY MTTL-W01',
      suggestedName: typeof device.name === 'string' && device.name.trim() ? device.name : this.deviceId(device),
      relayCount: Object.keys(this.outlets(device)).length || 4,
    };
  }

  private toSnapshot(device: VoltraDevice): StripStatusSnapshot {
    const outlets = this.outlets(device);
    const relays = Object.entries(outlets).map(([outlet, value]) => ({
      relayIndex: Number(outlet) - 1,
      state: value.state === 'on' ? 'on' : 'off' as RelayStateValue,
      watts: this.watts(value),
    })).filter((relay) => Number.isInteger(relay.relayIndex) && relay.relayIndex >= 0);
    return {
      driverStripId: this.deviceId(device),
      connectivity: this.connectivity(device),
      relays,
      totalWatts: round2(relays.reduce((total, relay) => total + relay.watts, 0)),
      timestamp: new Date().toISOString(),
      relayNames: this.relayNames(device),
    };
  }

  private outlets(device: VoltraDevice | undefined): Record<string, VoltraOutlet> {
    return device?.outlets && typeof device.outlets === 'object' && !Array.isArray(device.outlets)
      ? device.outlets as Record<string, VoltraOutlet>
      : {};
  }

  private connectivity(device: VoltraDevice): ConnectivityState {
    return device.online ? 'online' : 'offline';
  }

  private relayNames(device: VoltraDevice): Record<number, string> {
    if (!device.outletNames || typeof device.outletNames !== 'object' || Array.isArray(device.outletNames)) return {};
    const names: Record<number, string> = {};
    for (const [outlet, name] of Object.entries(device.outletNames as Record<string, unknown>)) {
      const relayIndex = Number(outlet) - 1;
      if (Number.isInteger(relayIndex) && relayIndex >= 0 && typeof name === 'string' && name.trim()) {
        names[relayIndex] = name.trim();
      }
    }
    return names;
  }

  private energyBucket(bucket: VoltraEnergyBucket): { day: string; kwh: number; byOutlet: Record<string, number> } {
    const byOutlet: Record<string, number> = {};
    if (bucket.byOutlet && typeof bucket.byOutlet === 'object' && !Array.isArray(bucket.byOutlet)) {
      for (const [outlet, value] of Object.entries(bucket.byOutlet as Record<string, unknown>)) {
        if (typeof value === 'number') byOutlet[outlet] = value;
      }
    }
    return {
      day: typeof bucket.day === 'string' ? bucket.day : '',
      kwh: typeof bucket.kwh === 'number' ? bucket.kwh : 0,
      byOutlet,
    };
  }

  private watts(outlet: VoltraOutlet): number {
    const value = outlet.watts ?? outlet.power;
    return typeof value === 'number' && Number.isFinite(value) ? value : 0;
  }

  private emit(event: StripDriverEvent): void {
    this.emitter.emit('event', event);
  }
}

function round2(value: number): number {
  return Math.round(value * 100) / 100;
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
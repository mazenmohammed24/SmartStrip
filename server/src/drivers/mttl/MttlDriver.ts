import { EventEmitter } from 'node:events';
import net, { type Server, type Socket } from 'node:net';
import type { MttlStripTelemetry, RelayStateValue } from '../../../../shared/types.js';
import { getMainDb } from '../../db/mainDb.js';
import { createLogger } from '../../utils/logger.js';
import type {
  DiscoveredStrip,
  RelayCommandResult,
  ScanOptions,
  StripDriver,
  StripDriverEvent,
  StripStatusSnapshot,
} from '../StripDriver.js';
import {
  MTTL_DEVICE_PORT,
  MTTL_RELAY_COUNT,
  buildGetInfoCommand,
  buildOnOffCommand,
  buildRssiCommand,
  buildVoltageCommand,
  normalizeFrame,
  parseBootInfo,
  parseEvent,
  parseGetInfo,
  parsePowerReport,
  parseQuery,
  relayIndexToChannel,
  type MttlOutletReading,
} from './protocol.js';

const log = createLogger('MttlDriver');

const DEFAULT_POLL_MS = 5000;
const REFRESH_TIMEOUT_MS = 6000;
const COMMAND_TIMEOUT_MS = 8000;
const DIAG_EVERY_POLLS = 3;
const DIAG_REPLY_TIMEOUT_MS = 2000;
const MAX_LINE_BYTES = 8192;
const SETTLE_MS = 300;
/** How long an unidentified connection may stay open before it is dropped. */
const IDENTIFIED_SOCKET_TIMEOUT_MS = 30_000;
/** Upper bound on concurrent sockets on the unauthenticated device port. */
const MAX_DEVICE_CONNECTIONS = 32;
/** Upper bound on tracked strips, so a forged bootinfo cannot grow memory without limit. */
const MAX_TRACKED_DEVICES = 64;
/**
 * How long after one of our own commands a relay change on that outlet is treated as
 * its echo rather than an external change. Must exceed the round-trip but stay well
 * under the poll interval, so a button press right after a command is not swallowed.
 */
const OWN_COMMAND_WINDOW_MS = 1500;

export interface MttlDriverOptions {
  /** TCP port the strip dials. Fixed at 10086 by the strip's firmware. */
  port?: number;
  pollIntervalMs?: number;
}

interface OutletState extends MttlOutletReading {
  /** Timestamp of the last onoff we sent for this outlet, used to tell our own commands apart from button presses. */
  lastCommandAt: number;
}

interface MttlDevice {
  mac: string;
  model: string;
  fw: string;
  ip: string;
  online: boolean;
  /** True once the current session has been reported online. Reset on disconnect. */
  announced: boolean;
  adopted: boolean;
  outlets: OutletState[];
  voltageV: number | null;
  rssi: number | null;
  /** When we last heard anything from this strip. Surfaced for staleness checks. */
  lastSeenAt: number;
}

/**
 * Driver for real LG U+ / Jinheung MTTL-W01 strips, speaking the strip's own
 * `up:` line protocol.
 *
 * The hardware model is inverted compared to a normal network device: the strip
 * stores one server IP in its flash and dials *us* on TCP 10086. So this driver
 * runs the device-facing TCP server, and "scanning" means "report the strips that
 * have dialled in", not a subnet sweep. That is why no home-router port forward
 * is ever needed, and why the same code works unchanged on a LAN host or a VPS.
 */
export class MttlDriver implements StripDriver {
  readonly name = 'mttl';

  private readonly port: number;
  private readonly pollIntervalMs: number;
  private readonly devices = new Map<string, MttlDevice>();
  /** Driver ids already in the fleet, so a restart does not orphan them. */
  private readonly adoptedIds = new Set<string>();
  /** socket per device MAC, so a re-dial replaces the previous session cleanly */
  private readonly sockets = new Map<string, Socket>();
  private readonly buffers = new Map<string, string>();
  /** serialized command chain per device: only one transaction in flight at a time */
  private readonly queues = new Map<string, Promise<unknown>>();
  private readonly getInfoWaiters = new Map<string, Array<(ok: boolean) => void>>();
  private readonly replyWaiters = new Map<string, () => void>();
  private readonly emitter = new EventEmitter();
  private server: Server | null = null;
  private pollHandle: ReturnType<typeof setInterval> | null = null;
  private pollCount = 0;
  private polling = false;

  constructor(options: MttlDriverOptions = {}) {
    this.port = options.port ?? MTTL_DEVICE_PORT;
    this.pollIntervalMs = options.pollIntervalMs ?? DEFAULT_POLL_MS;
    this.emitter.setMaxListeners(50);
  }

  async init(): Promise<void> {
    // Strips dial in *after* the fleet resync has already run, so the driver's own
    // in-memory adoption state is empty at this point. Seed it from the database, or
    // every previously-adopted strip would come back unadopted, never be polled, and
    // reappear in scans as if it were a new device.
    this.loadAdoptedIds();
    await this.listen();
    this.pollHandle = setInterval(() => void this.poll(), this.pollIntervalMs);
    log.info(`listening for MTTL-W01 strips on TCP ${this.port}, polling every ${this.pollIntervalMs}ms`);
  }

  /**
   * Records which driver ids are already in the fleet. Ids belonging to a different
   * driver are ignored: those rows describe devices this driver cannot address.
   */
  private loadAdoptedIds(): void {
    try {
      const db = getMainDb();
      for (const strip of db.data.strips) {
        if (strip.driverName === this.name) this.adoptedIds.add(normalizeMac(strip.driverStripId));
      }
      if (this.adoptedIds.size > 0) {
        log.info(`restoring ${this.adoptedIds.size} adopted strip(s) from the fleet database`);
      }
    } catch (err) {
      // The database is initialised before the driver, so this only fires if that
      // ordering ever changes. Proceed with an empty set rather than refusing to start.
      log.warn('could not read adopted strips from the database', err);
    }
  }

  async shutdown(): Promise<void> {
    if (this.pollHandle) clearInterval(this.pollHandle);
    this.pollHandle = null;
    for (const socket of this.sockets.values()) socket.destroy();
    this.sockets.clear();
    this.buffers.clear();
    this.getInfoWaiters.clear();
    await new Promise<void>((resolve) => {
      if (!this.server) return resolve();
      this.server.close(() => resolve());
    });
    this.server = null;
    this.emitter.removeAllListeners();
  }

  /**
   * There is nothing to sweep: a strip shows up here only after it has opened a
   * session with this server. Anything already adopted is filtered out, matching
   * the "discover devices you do not have yet" contract the scan page expects.
   */
  async scanForStrips(options?: ScanOptions): Promise<DiscoveredStrip[]> {
    const candidates = [...this.devices.values()].filter((d) => !d.adopted);
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
    const mac = normalizeMac(driverStripId);
    this.adoptedIds.add(mac);
    const device = this.devices.get(mac);
    if (!device) {
      // Not dialled in yet. The id is remembered, so the strip is adopted the moment
      // it connects rather than needing another call from the caller.
      return;
    }
    device.adopted = true;
    // Refresh opportunistically; a strip that is not dialled in right now must not
    // fail adoption, its stored state is still adopted.
    await this.withSession(device, (session) => session.refresh()).catch(() => undefined);
  }

  async releaseStrip(driverStripId: string): Promise<void> {
    const mac = normalizeMac(driverStripId);
    this.adoptedIds.delete(mac);
    const device = this.devices.get(mac);
    if (!device) return;
    device.adopted = false;
  }

  async setRelay(driverStripId: string, relayIndex: number, state: RelayStateValue): Promise<RelayCommandResult> {
    const device = this.requireDevice(driverStripId);
    if (relayIndex < 0 || relayIndex >= MTTL_RELAY_COUNT) {
      return { relayIndex, success: false, newState: 'off', error: 'relay not found' };
    }
    const session = this.sessionFor(device);
    if (!session) {
      return { relayIndex, success: false, newState: this.outletState(device, relayIndex), error: 'strip offline' };
    }

    const confirmed = await this.withSession(device, (s) => s.setOutlets([relayIndex], state === 'on')).catch((err) => {
      log.warn(`onoff ${relayIndex} -> ${state} failed`, err);
      return false;
    });
    // Report what the strip actually reads back, never what we asked for: a strip
    // that refused or missed the command must not be recorded as having applied it.
    const newState = this.outletState(device, relayIndex);
    if (!confirmed || newState !== state) {
      return {
        relayIndex,
        success: false,
        newState,
        error: confirmed ? `strip stayed ${newState} after being told ${state}` : 'strip did not confirm the command',
      };
    }
    this.emit({ type: 'relay-changed', driverStripId: device.mac, relayIndex, state: newState, cause: 'command' });
    return { relayIndex, success: true, newState };
  }

  async setAllRelays(driverStripId: string, state: RelayStateValue): Promise<RelayCommandResult[]> {
    const device = this.requireDevice(driverStripId);
    const indices = Array.from({ length: MTTL_RELAY_COUNT }, (_, i) => i);
    const session = this.sessionFor(device);
    if (!session) {
      return indices.map((relayIndex) => ({
        relayIndex,
        success: false,
        newState: this.outletState(device, relayIndex),
        error: 'strip offline',
      }));
    }

    const confirmed = await this.withSession(device, (s) => s.setOutlets(indices, state === 'on')).catch((err) => {
      log.warn(`all relays -> ${state} failed`, err);
      return false;
    });

    return indices.map((relayIndex) => {
      const newState = this.outletState(device, relayIndex);
      if (!confirmed || newState !== state) {
        return {
          relayIndex,
          success: false,
          newState,
          error: confirmed ? `strip stayed ${newState} after being told ${state}` : 'strip did not confirm the command',
        };
      }
      this.emit({ type: 'relay-changed', driverStripId: device.mac, relayIndex, state: newState, cause: 'command' });
      return { relayIndex, success: true, newState };
    });
  }

  /**
   * Never throws for a strip that is not currently dialled in: callers use this at
   * startup to resync the fleet, and a strip that is asleep must be reported as
   * offline rather than break the whole resync. No live round-trip happens here
   * either - the poll loop already keeps this state fresh, and blocking on the wire
   * would make a fleet-wide resync cost seconds per strip.
   */
  async getStatus(driverStripId: string): Promise<StripStatusSnapshot> {
    const mac = normalizeMac(driverStripId);
    const device = this.devices.get(mac);
    if (!device) return offlineSnapshot(mac);
    return snapshotOf(device);
  }

  /** Live diagnostics for a strip: line voltage, Wi-Fi RSSI and per-outlet energy/temperature. */
  getTelemetry(driverStripId: string): MttlTelemetry | null {
    const device = this.devices.get(normalizeMac(driverStripId));
    if (!device) return null;
    return telemetryOf(device);
  }

  /** The port actually bound, which differs from the requested one when 0 was asked for. */
  listeningPort(): number {
    const address = this.server?.address();
    return address && typeof address === 'object' ? address.port : this.port;
  }

  /** Shape consumed by the health route: what this server is listening for and what has dialled in. */
  health(): Record<string, unknown> {
    const devices = [...this.devices.values()];
    return {
      name: this.name,
      listeningPort: this.listeningPort(),
      pollIntervalMs: this.pollIntervalMs,
      knownStrips: devices.length,
      onlineStrips: devices.filter((d) => d.online).length,
      lastSeenAt: devices.length
        ? new Date(Math.max(...devices.map((d) => d.lastSeenAt))).toISOString()
        : null,
    };
  }

  /** Every strip this server has ever seen, online or not. Used by the health and mttl routes. */
  listKnownDevices(): MttlTelemetry[] {
    return [...this.devices.values()].map((d) => telemetryOf(d));
  }

  onEvent(listener: (event: StripDriverEvent) => void): void {
    this.emitter.on('event', listener);
  }

  offEvent(listener: (event: StripDriverEvent) => void): void {
    this.emitter.off('event', listener);
  }

  // ---- device-facing TCP server ----

  private listen(): Promise<void> {
    return new Promise((resolve, reject) => {
      const server = net.createServer((socket) => this.handleConnection(socket));
      // Two listeners, deliberately. The one-shot rejects a failed bind so startup
      // fails loudly. The persistent one must never be removed: once this port is
      // internet-facing, a later server-level 'error' (EMFILE under a connection
      // flood, for example) with no listener is an unhandled EventEmitter event and
      // would take the HTTP server and every WebSocket client down with it.
      server.once('error', reject);
      server.on('error', (err) => log.error('device server error', err));
      server.maxConnections = MAX_DEVICE_CONNECTIONS;
      server.listen(this.port, '0.0.0.0', () => {
        server.off('error', reject);
        this.server = server;
        resolve();
      });
    });
  }

  private handleConnection(socket: Socket): void {
    const ip = socket.remoteAddress ?? '?';
    log.info(`incoming strip connection from ${ip}`);
    socket.setNoDelay(true);
    socket.setEncoding('utf8');
    // An unidentified connection that never sends a bootinfo would otherwise sit
    // here forever holding a buffer entry and keeping shutdown() waiting on it.
    socket.setTimeout(IDENTIFIED_SOCKET_TIMEOUT_MS, () => {
      log.info(`dropping unidentified connection from ${ip} after ${IDENTIFIED_SOCKET_TIMEOUT_MS}ms`);
      socket.destroy();
    });
    this.buffers.set(socketKey(socket), '');

    socket.on('data', (chunk: string | Buffer) => this.handleData(socket, chunk.toString()));
    socket.on('error', (err) => log.warn(`socket error from ${ip}: ${err.message}`));
    socket.on('timeout', () => socket.destroy());
    socket.on('close', () => this.handleDisconnect(socket, ip));
  }

  private handleData(socket: Socket, chunk: string): void {
    const key = socketKey(socket);
    const buffer = (this.buffers.get(key) ?? '') + chunk;
    const parts = buffer.split(/\r?\n/);
    // The trailing element is an incomplete line; hold it until the rest arrives.
    this.buffers.set(key, parts.pop() ?? '');
    if (this.buffers.get(key)!.length > MAX_LINE_BYTES) {
      log.warn(`line from ${socket.remoteAddress} exceeded ${MAX_LINE_BYTES} bytes, dropping session`);
      socket.destroy();
      return;
    }
    for (const part of parts) {
      const line = normalizeFrame(part);
      if (line) this.handleLine(socket, line);
    }
  }

  private handleLine(socket: Socket, line: string): void {
    const boot = parseBootInfo(line);
    if (boot) {
      this.registerDevice(socket, boot.mac, boot.model, boot.fw);
      const device = this.devices.get(boot.mac)!;
      // `announced` is false for a strip that has never been seen and set again on
      // disconnect, so a reconnect (not just the very first dial) reports online.
      const wasOffline = !device.online || !device.announced;
      device.online = true;
      device.announced = true;
      device.online = true;
      device.ip = socket.remoteAddress ?? device.ip;
      device.lastSeenAt = Date.now();
      this.attachSocket(device, socket);
      // Identified, so it no longer needs the bootinfo deadline.
      socket.setTimeout(0);
      log.info(`${deviceName(device)} connected - model=${device.model} fw=${device.fw} from ${device.ip}`);
      if (wasOffline) this.emit({ type: 'connectivity', driverStripId: device.mac, connectivity: 'online' });
      // A fresh session may have stale relay state in the DB, so pull it immediately.
      void this.withSession(device, (s) => s.refresh()).catch(() => undefined);
      return;
    }

    const device = this.deviceForSocket(socket);
    if (!device) return;
    device.lastSeenAt = Date.now();

    if (line.startsWith('up:getinfo:')) {
      this.applyGetInfo(device, line);
      return;
    }

    const power = parsePowerReport(line);
    if (power) {
      device.voltageV = power.volts;
      this.notifyReply(device.mac);
      return;
    }

    const rssi = parseQuery(line);
    if (rssi !== null) {
      device.rssi = rssi;
      this.notifyReply(device.mac);
      return;
    }

    const event = parseEvent(line);
    if (event) {
      const outlet = this.outlet(device, event.relayIndex);
      if (outlet) outlet.on = event.on;
      // A physical button press is authoritative: publish it as external so the
      // app reflects the strip instead of only echoing our own commands.
      this.emit({
        type: 'relay-changed',
        driverStripId: device.mac,
        relayIndex: event.relayIndex,
        state: event.on ? 'on' : 'off',
        cause: 'external',
      });
    }
    // Anything else, including our own `up:onoff:` acknowledgements, needs no action.
  }

  private applyGetInfo(device: MttlDevice, line: string): void {
    const readings = parseGetInfo(line);
    // Only accept a snapshot that covers every outlet; a partial frame would
    // otherwise blank the outlets it did not mention.
    if (readings.length !== MTTL_RELAY_COUNT) {
      log.debug(`ignoring partial getinfo for ${deviceName(device)}: ${readings.length}/${MTTL_RELAY_COUNT} outlets`);
      return;
    }

    const overloads: number[] = [];
    for (const reading of readings) {
      const previous = this.outlet(device, reading.relayIndex);
      if (previous && previous.on !== reading.on) {
        // Suppress the echo of a command we just sent, but nothing else: a button
        // press or a protection trip must still reach the app.
        const isOurEcho = previous.lastCommandAt > 0 && Date.now() - previous.lastCommandAt < OWN_COMMAND_WINDOW_MS;
        if (!isOurEcho) {
          this.emit({
            type: 'relay-changed',
            driverStripId: device.mac,
            relayIndex: reading.relayIndex,
            state: reading.on ? 'on' : 'off',
            cause: 'external',
          });
        }
      }
      // Edge-triggered: overload and overheat are levels that stay set while the
      // condition persists. Emitting on every poll would fire a critical push
      // notification every few seconds for as long as the strip is overloaded.
      const faulted = reading.overload || reading.overheat;
      if (faulted && !previous?.overload && !previous?.overheat) overloads.push(reading.relayIndex);
      device.outlets[reading.relayIndex] = { ...reading, lastCommandAt: previous?.lastCommandAt ?? 0 };
    }

    for (const relayIndex of overloads) {
      this.emit({ type: 'overload', driverStripId: device.mac, relayIndex, watts: device.outlets[relayIndex]?.watts ?? 0 });
    }

    this.notifyGetInfo(device.mac);
    if (device.adopted) this.emit({ type: 'status', snapshot: snapshotOf(device) });
  }

  private registerDevice(socket: Socket, mac: string, model: string, fw: string): void {
    const existing = this.devices.get(mac);
    if (existing) {
      existing.model = model || existing.model;
      existing.fw = fw || existing.fw;
      existing.adopted = this.adoptedIds.has(mac);
      return;
    }
    this.evictIfFull(mac);
    this.devices.set(mac, {
      mac,
      model,
      fw,
      ip: socket.remoteAddress ?? '',
      // announced stays false: the caller decides when to publish the online event.
      online: true,
      announced: false,
      adopted: this.adoptedIds.has(mac),
      outlets: emptyOutlets(),
      voltageV: null,
      rssi: null,
      lastSeenAt: Date.now(),
    });
  }

  /**
   * The device port is unauthenticated by hardware design, so a remote host could
   * otherwise register unbounded fake MACs and grow this map (and the per-device
   * queue and waiter state) until the process runs out of memory. Evicting the
   * least-recently-seen *offline* entries keeps the bound without ever discarding a
   * strip that is actually connected, and an adopted strip is never a candidate.
   */
  private evictIfFull(incomingMac: string): void {
    if (this.devices.size < MAX_TRACKED_DEVICES) return;
    let oldestMac: string | null = null;
    let oldestSeen = Number.POSITIVE_INFINITY;
    for (const [mac, device] of this.devices) {
      if (device.online || device.adopted) continue;
      if (device.lastSeenAt < oldestSeen) {
        oldestSeen = device.lastSeenAt;
        oldestMac = mac;
      }
    }
    if (oldestMac === null) {
      log.warn(`device table is full (${this.devices.size}) and every entry is connected or adopted; not tracking ${incomingMac}`);
      return;
    }
    log.warn(`device table full (${MAX_TRACKED_DEVICES}); evicting least recently seen offline strip ${oldestMac}`);
    this.devices.delete(oldestMac);
    this.queues.delete(oldestMac);
    this.buffers.delete(oldestMac);
    this.getInfoWaiters.delete(oldestMac);
  }

  /** Binds a socket to its device and evicts any previous session for the same MAC. */
  private attachSocket(device: MttlDevice, socket: Socket): void {
    const previous = this.sockets.get(device.mac);
    if (previous && previous !== socket) {
      log.debug(`replacing stale session for ${deviceName(device)}`);
      previous.destroy();
    }
    this.sockets.set(device.mac, socket);
  }

  private handleDisconnect(socket: Socket, ip: string): void {
    const device = this.deviceForSocket(socket);
    this.buffers.delete(socketKey(socket));
    if (!device) {
      log.info(`unidentified connection from ${ip} closed`);
      return;
    }
    if (this.sockets.get(device.mac) === socket) {
      this.sockets.delete(device.mac);
      this.releaseWaiters(device.mac);
      if (device.online) {
        device.online = false;
        device.announced = false;
        log.info(`${deviceName(device)} disconnected from ${ip}`);
        this.emit({ type: 'connectivity', driverStripId: device.mac, connectivity: 'offline' });
      }
    }
  }

  // ---- command transactions ----

  private sessionFor(device: MttlDevice): Socket | null {
    const socket = this.sockets.get(device.mac);
    if (!socket || socket.destroyed) return null;
    return socket;
  }

  private requireDevice(driverStripId: string): MttlDevice {
    const device = this.devices.get(normalizeMac(driverStripId));
    if (!device) {
      throw new Error(`Unknown MTTL strip: ${driverStripId}. It must dial this server on TCP ${this.port} first.`);
    }
    return device;
  }

  private outlet(device: MttlDevice, relayIndex: number): OutletState | undefined {
    return device.outlets[relayIndex];
  }

  /**
   * The state the strip actually reports, not the one we asked for. Defaults to
   * "off" for an outlet the strip has never reported on.
   */
  private outletState(device: MttlDevice, relayIndex: number): RelayStateValue {
    return this.outlet(device, relayIndex)?.on ? 'on' : 'off';
  }

  private deviceForSocket(socket: Socket): MttlDevice | undefined {
    for (const [mac, candidate] of this.sockets) {
      if (candidate === socket) return this.devices.get(mac);
    }
    return undefined;
  }

  /**
   * Runs one command transaction with exclusive ownership of the socket. Commands
   * are chained rather than locked so a failed command cannot wedge the queue.
   */
  private withSession<T>(device: MttlDevice, fn: (session: MttlSession) => Promise<T>): Promise<T> {
    const previous = this.queues.get(device.mac) ?? Promise.resolve();
    const next = previous.then(async () => {
      const socket = this.sessionFor(device);
      if (!socket) throw new Error(`${deviceName(device)} is offline`);
      return fn(new MttlSession(socket, device, this));
    });
    this.queues.set(device.mac, next.catch(() => undefined));
    return next;
  }

  // Called by MttlSession, which runs inside the per-device command queue.

  writeLine(socket: Socket, command: string): void {
    socket.write(`${command}\r\n`);
  }

  /**
   * Resolves true once a getinfo frame for this strip has landed, false on timeout
   * or on disconnect. The waiter carries the outcome so a dropped connection can
   * never be mistaken for a confirmed command.
   */
  waitForGetInfo(device: MttlDevice, timeoutMs: number): Promise<boolean> {
    return new Promise((resolve) => {
      const waiters = this.getInfoWaiters.get(device.mac) ?? [];
      const done = (ok: boolean) => {
        clearTimeout(timer);
        resolve(ok);
      };
      const timer = setTimeout(() => {
        const list = this.getInfoWaiters.get(device.mac);
        if (list) this.getInfoWaiters.set(device.mac, list.filter((w) => w !== done));
        resolve(false);
      }, timeoutMs);
      timer.unref?.();
      this.getInfoWaiters.set(device.mac, [...waiters, done]);
    });
  }

  private notifyGetInfo(mac: string): void {
    const waiters = this.getInfoWaiters.get(mac);
    if (!waiters?.length) return;
    this.getInfoWaiters.set(mac, []);
    for (const waiter of waiters) waiter(true);
  }

  private releaseWaiters(mac: string): void {
    const waiters = this.getInfoWaiters.get(mac);
    this.getInfoWaiters.set(mac, []);
    // Resolve as failure, not success: the strip is gone, so a command in flight was
    // never confirmed. Resolving true here would report unapplied commands as applied.
    for (const waiter of waiters ?? []) waiter(false);
  }

  waitForReplies(device: MttlDevice, count: number, timeoutMs: number): Promise<void> {
    return new Promise((resolve) => {
      let seen = 0;
      const finish = () => {
        clearTimeout(timer);
        this.replyWaiters.delete(device.mac);
        resolve();
      };
      const timer = setTimeout(finish, timeoutMs);
      timer.unref?.();
      this.replyWaiters.set(device.mac, () => {
        if (++seen >= count) finish();
      });
    });
  }

  private notifyReply(mac: string): void {
    this.replyWaiters.get(mac)?.();
  }

  markCommand(device: MttlDevice, relayIndices: number[]): void {
    const now = Date.now();
    for (const relayIndex of relayIndices) {
      const outlet = device.outlets[relayIndex];
      if (outlet) outlet.lastCommandAt = now;
    }
  }

  private async poll(): Promise<void> {
    // Devices are awaited one after another, so a tick costs N x per-strip latency.
    // Without this guard, ticks overlap once that exceeds the interval and each
    // overlap enqueues another refresh onto the same per-strip chain.
  if (this.polling) {
    log.debug('previous poll still running, skipping this tick');
    return;
  }
    this.polling = true;
    this.pollCount++;
    try {
      for (const device of [...this.devices.values()]) {
        if (!device.online || !device.adopted) continue;
        if (!this.sessionFor(device)) continue;
        const sendDiagnostics = this.pollCount % DIAG_EVERY_POLLS === 0;
        try {
          const ok = await this.withSession(device, (session) => session.refresh());
          if (!ok) {
            log.debug(`poll timeout for ${deviceName(device)}`);
            continue;
          }
          if (sendDiagnostics) {
            await this.withSession(device, (session) => session.diagnostics()).catch(() => undefined);
          }
        } catch (err) {
          log.debug(`poll error for ${deviceName(device)}: ${(err as Error).message}`);
        }
      }
    } finally {
      this.polling = false;
    }
  }

  private emit(event: StripDriverEvent): void {
    this.emitter.emit('event', event);
  }
}

/** A single command transaction against one strip: at most one in flight per socket. */
class MttlSession {
  constructor(
    private readonly socket: Socket,
    private readonly device: MttlDevice,
    private readonly driver: MttlDriver,
  ) {}

  send(command: string): void {
    this.driver.writeLine(this.socket, command);
  }

  async refresh(timeoutMs = REFRESH_TIMEOUT_MS): Promise<boolean> {
    this.send(buildGetInfoCommand());
    return this.driver.waitForGetInfo(this.device, timeoutMs);
  }

  /**
   * Sends one onoff per relay (the firmware has no "all" command), waits for the
   * relays to settle, then re-reads the authoritative state. Returning the read
   * back rather than the requested value means a strip that refused the command
   * is reported honestly instead of optimistically.
   */
  async setOutlets(relayIndices: number[], on: boolean): Promise<boolean> {
    this.driver.markCommand(this.device, relayIndices);
    for (const relayIndex of relayIndices) {
      this.send(buildOnOffCommand(relayIndex, on));
    }
    await delay(SETTLE_MS);
    return this.refresh(COMMAND_TIMEOUT_MS);
  }

  /**
   * Asks for line voltage and Wi-Fi RSSI. Resolves once the strip has answered both
   * questions or the wait elapses, so a caller that immediately reads the values
   * gets them instead of a null.
   */
  async diagnostics(timeoutMs = DIAG_REPLY_TIMEOUT_MS): Promise<void> {
    const answered = this.driver.waitForReplies(this.device, 2, timeoutMs);
    this.send(buildVoltageCommand());
    this.send(buildRssiCommand());
    await answered;
  }
}

/** The telemetry shape is owned by `shared/types.ts`; re-declaring it here would let the two drift. */
export type MttlTelemetry = MttlStripTelemetry;

function emptyOutlets(): OutletState[] {
  return Array.from({ length: MTTL_RELAY_COUNT }, (_, relayIndex) => ({
    relayIndex,
    channel: relayIndexToChannel(relayIndex),
    on: false,
    watts: 0,
    energyKwh: 0,
    temperatureC: 0,
    overload: false,
    overheat: false,
    lastCommandAt: 0,
  }));
}

function toDiscovered(device: MttlDevice): DiscoveredStrip {
  return {
    driverStripId: device.mac,
    model: device.model,
    suggestedName: deviceName(device),
    ipAddress: device.ip,
    relayCount: MTTL_RELAY_COUNT,
    // RSSI is negative dBm on this hardware; the UI shows a 0-100 "signal %",
    // so map the usual -90..-30 dBm window onto that range.
    signalStrength: device.rssi === null ? undefined : rssiToPercent(device.rssi),
  };
}

export function rssiToPercent(rssi: number): number {
  return Math.max(0, Math.min(100, Math.round((rssi + 90) * (100 / 60))));
}

function snapshotOf(device: MttlDevice): StripStatusSnapshot {
  const relays = device.outlets.map((o) => ({
    relayIndex: o.relayIndex,
    state: (o.on ? 'on' : 'off') as RelayStateValue,
    watts: o.watts,
  }));
  return {
    driverStripId: device.mac,
    connectivity: device.online ? 'online' : 'offline',
    relays,
    totalWatts: round2(relays.reduce((sum, r) => sum + r.watts, 0)),
    timestamp: new Date().toISOString(),
  };
}

function telemetryOf(device: MttlDevice): MttlTelemetry {
  const totalWatts = device.outlets.reduce((sum, o) => sum + o.watts, 0);
  return {
    driverStripId: device.mac,
    model: device.model,
    fw: device.fw,
    ip: device.ip,
    connectivity: device.online ? 'online' : 'offline',
    voltageV: device.voltageV,
    currentA: device.voltageV ? round2(totalWatts / device.voltageV) : null,
    rssi: device.rssi,
    totalWatts: round2(totalWatts),
    totalEnergyKwh: round3(device.outlets.reduce((sum, o) => sum + o.energyKwh, 0)),
    outlets: device.outlets.map((o) => ({
      relayIndex: o.relayIndex,
      on: o.on,
      watts: o.watts,
      energyKwh: o.energyKwh,
      temperatureC: o.temperatureC,
    })),
  };
}

/**
 * A strip that has never dialled in. `getStatus` returns this instead of throwing so
 * a fleet-wide resync is not aborted by one absent device.
 */
function offlineSnapshot(driverStripId: string): StripStatusSnapshot {
  return {
    driverStripId,
    connectivity: 'offline',
    relays: Array.from({ length: MTTL_RELAY_COUNT }, (_, relayIndex) => ({ relayIndex, state: 'off' as RelayStateValue, watts: 0 })),
    totalWatts: 0,
    timestamp: new Date().toISOString(),
  };
}

function deviceName(device: MttlDevice): string {
  return `MTTL ${device.mac.slice(-7)}`;
}

function normalizeMac(mac: string): string {
  return mac.trim().toUpperCase();
}

/** Line-buffer key per socket, tracked in a WeakMap so buffers die with the socket. */
const socketKeys = new WeakMap<Socket, string>();
let socketCounter = 0;
function socketKey(socket: Socket): string {
  let key = socketKeys.get(socket);
  if (!key) {
    key = `s${++socketCounter}`;
    socketKeys.set(socket, key);
  }
  return key;
}

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

function round3(n: number): number {
  return Math.round(n * 1000) / 1000;
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

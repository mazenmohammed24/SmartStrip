import type { DiscoveredStripDTO, EventCause, RelayStateValue, StripDTO, StripTelemetryDTO } from '../../../shared/types.js';
import { getMainDb } from '../db/mainDb.js';
import type { StoredRelay, StoredStrip } from '../db/schema.js';
import type { DiscoveredStrip, ScanOptions, StripDriver, StripDriverEvent } from '../drivers/StripDriver.js';
import { newId } from '../utils/id.js';
import { createLogger } from '../utils/logger.js';
import { broadcast } from '../ws/broadcast.js';
import { deleteRulesForStrip } from './ruleService.js';
import { logEvent } from './eventService.js';
import { notify } from './notificationService.js';
import { recordTick } from './powerService.js';

const log = createLogger('fleetService');
const ICON_ROTATION = ['lamp', 'charger', 'fridge', 'heater'] as const;

// relayIndex -> watts, per DB strip id. Populated from driver 'status' ticks,
// never persisted directly (power history is derived via powerService rollups).
const liveWatts = new Map<string, Map<number, number>>();
const liveTelemetry = new Map<string, StripTelemetryDTO>();
const lastOverloadAt = new Map<string, number>();
const OVERLOAD_COOLDOWN_MS = 10 * 60_000;

export function getLiveTelemetry(stripId: string): StripTelemetryDTO | null {
  return liveTelemetry.get(stripId) ?? null;
}

export function getLiveWattsForStrip(stripId: string): { relays: Map<number, number>; total: number } {
  const relays = liveWatts.get(stripId) ?? new Map();
  let total = 0;
  for (const w of relays.values()) total += w;
  return { relays, total: round2(total) };
}

function toRelayDTO(relay: StoredRelay, stripId: string): StripDTO['relays'][number] {
  const watts = liveWatts.get(stripId)?.get(relay.index) ?? 0;
  return {
    id: relay.id,
    stripId,
    index: relay.index,
    name: relay.name,
    state: relay.state,
    watts,
    icon: relay.icon,
    lastChangedAt: relay.lastChangedAt,
  };
}

function toStripDTO(strip: StoredStrip, relays: StoredRelay[]): StripDTO {
  const relayDTOs = relays
    .filter((r) => r.stripId === strip.id)
    .sort((a, b) => a.index - b.index)
    .map((r) => toRelayDTO(r, strip.id));
  return {
    id: strip.id,
    name: strip.name,
    model: strip.model,
    location: strip.location,
    driverName: strip.driverName,
    driverStripId: strip.driverStripId,
    ipAddress: strip.ipAddress,
    connectivity: strip.connectivity,
    order: strip.order,
    addedAt: strip.addedAt,
    relays: relayDTOs,
    totalWatts: round2(relayDTOs.reduce((a, r) => a + r.watts, 0)),
    icon: strip.icon ?? 'power-strip',
    telemetry: liveTelemetry.get(strip.id) ?? null,
  };
}

export function listStrips(): StripDTO[] {
  const db = getMainDb();
  return [...db.data.strips].sort((a, b) => a.order - b.order).map((s) => toStripDTO(s, db.data.relays));
}

export function getStrip(stripId: string): StripDTO | null {
  const db = getMainDb();
  const strip = db.data.strips.find((s) => s.id === stripId);
  if (!strip) return null;
  return toStripDTO(strip, db.data.relays);
}

function findStripOrThrow(stripId: string): StoredStrip {
  const strip = getMainDb().data.strips.find((s) => s.id === stripId);
  if (!strip) throw new NotFoundError(`Strip ${stripId} not found`);
  return strip;
}

export class NotFoundError extends Error { }

export async function scanForStrips(driver: StripDriver, options?: ScanOptions): Promise<DiscoveredStripDTO[]> {
  const results = await driver.scanForStrips(options);
  return results.map(toDiscoveredDTO);
}

function toDiscoveredDTO(d: DiscoveredStrip): DiscoveredStripDTO {
  return {
    driverStripId: d.driverStripId,
    model: d.model,
    suggestedName: d.suggestedName,
    ipAddress: d.ipAddress,
    relayCount: d.relayCount,
    signalStrength: d.signalStrength,
  };
}

export interface AdoptInput {
  driverStripId: string;
  model: string;
  relayCount: number;
  ipAddress?: string | null;
  name?: string;
  suggestedName: string;
  location?: string | null;
}

export async function adoptStrip(driver: StripDriver, input: AdoptInput): Promise<StripDTO> {
  const db = getMainDb();
  if (db.data.strips.some((s) => s.driverStripId === input.driverStripId)) {
    throw new Error('Strip already in fleet');
  }
  await driver.adoptStrip(input.driverStripId);
  const status = await driver.getStatus(input.driverStripId);

  const now = new Date().toISOString();
  const strip: StoredStrip = {
    id: newId('strip'),
    name: input.name?.trim() || input.suggestedName,
    model: input.model,
    location: input.location ?? null,
    driverName: driver.name,
    driverStripId: input.driverStripId,
    ipAddress: input.ipAddress ?? null,
    connectivity: status.connectivity,
    order: db.data.strips.length,
    addedAt: now,
    icon: 'power-strip',
  };
  db.data.strips.push(strip);

  for (let i = 0; i < input.relayCount; i++) {
    const relay: StoredRelay = {
      id: newId('relay'),
      stripId: strip.id,
      index: i,
      name: status.relayNames?.[i] ?? `Outlet ${i + 1}`,
      state: status.relays.find((relay) => relay.relayIndex === i)?.state ?? 'off',
      icon: ICON_ROTATION[i % ICON_ROTATION.length],
      lastChangedAt: now,
    };
    db.data.relays.push(relay);
  }
  await db.write();

  const dto = toStripDTO(strip, db.data.relays);
  await logEvent({
    type: 'strip-added',
    stripId: strip.id,
    stripName: strip.name,
    driverStripId: strip.driverStripId,
    relayIndex: null,
    message: `${strip.name} added to fleet`,
    cause: 'user',
  });
  broadcast({ type: 'strip-added', strip: dto });
  return dto;
}

export async function removeStrip(driver: StripDriver, stripId: string): Promise<void> {
  const db = getMainDb();
  const strip = findStripOrThrow(stripId);
  await driver.releaseStrip(strip.driverStripId);
  db.data.strips = db.data.strips.filter((s) => s.id !== stripId);
  db.data.relays = db.data.relays.filter((r) => r.stripId !== stripId);
  await db.write();
  liveWatts.delete(stripId);
  await deleteRulesForStrip(stripId);
  await logEvent({
    type: 'strip-removed',
    stripId: null,
    stripName: strip.name,
    driverStripId: strip.driverStripId,
    relayIndex: null,
    message: `${strip.name} removed from fleet`,
    cause: 'user',
  });
  broadcast({ type: 'strip-removed', stripId });
}

export async function updateStrip(stripId: string, patch: { name?: string; location?: string | null; order?: number; icon?: StoredRelay['icon'] }): Promise<StripDTO> {
  const db = getMainDb();
  const strip = findStripOrThrow(stripId);
  if (patch.name !== undefined) strip.name = patch.name.trim();
  if (patch.location !== undefined) strip.location = patch.location;
  if (patch.order !== undefined) strip.order = patch.order;
  if (patch.icon !== undefined) strip.icon = patch.icon;
  await db.write();
  const dto = toStripDTO(strip, db.data.relays);
  broadcast({ type: 'strip-updated', strip: dto });
  return dto;
}

export async function renameRelay(stripId: string, relayIndex: number, name: string): Promise<StripDTO> {
  const db = getMainDb();
  const strip = findStripOrThrow(stripId);
  const relay = db.data.relays.find((r) => r.stripId === stripId && r.index === relayIndex);
  if (!relay) throw new NotFoundError('Relay not found');
  relay.name = name.trim();
  await db.write();
  return toStripDTO(strip, db.data.relays);
}

export async function updateRelayIcon(stripId: string, relayIndex: number, icon: StoredRelay['icon']): Promise<StripDTO> {
  const db = getMainDb();
  const strip = findStripOrThrow(stripId);
  const relay = db.data.relays.find((candidate) => candidate.stripId === stripId && candidate.index === relayIndex);
  if (!relay) throw new NotFoundError('Relay not found');
  relay.icon = icon;
  await db.write();
  return toStripDTO(strip, db.data.relays);
}

export async function setRelay(driver: StripDriver, stripId: string, relayIndex: number, state: RelayStateValue, cause: EventCause = 'user'): Promise<StripDTO> {
  const strip = findStripOrThrow(stripId);
  const db = getMainDb();
  const relay = db.data.relays.find((r) => r.stripId === stripId && r.index === relayIndex);
  if (!relay) throw new NotFoundError('Relay not found');

  const result = await driver.setRelay(strip.driverStripId, relayIndex, state);
  if (!result.success) throw new Error(result.error ?? 'Failed to set relay');

  relay.state = result.newState;
  relay.lastChangedAt = new Date().toISOString();
  await db.write();

  broadcast({ type: 'relay-state', stripId, relayIndex, state: relay.state, changedAt: relay.lastChangedAt });
  await logEvent({
    type: relay.state === 'on' ? 'relay-on' : 'relay-off',
    stripId,
    stripName: strip.name,
    driverStripId: strip.driverStripId,
    relayIndex,
    message: `${relay.name} turned ${relay.state}`,
    cause,
  });
  return toStripDTO(strip, db.data.relays);
}

export async function setAllRelays(driver: StripDriver, stripId: string, state: RelayStateValue, cause: EventCause = 'user'): Promise<StripDTO> {
  const strip = findStripOrThrow(stripId);
  const db = getMainDb();
  const results = await driver.setAllRelays(strip.driverStripId, state);
  const now = new Date().toISOString();
  for (const result of results) {
    if (!result.success) continue;
    const relay = db.data.relays.find((r) => r.stripId === stripId && r.index === result.relayIndex);
    if (!relay) continue;
    relay.state = result.newState;
    relay.lastChangedAt = now;
    broadcast({ type: 'relay-state', stripId, relayIndex: relay.index, state: relay.state, changedAt: now });
  }
  await db.write();
  await logEvent({
    type: state === 'on' ? 'relay-on' : 'relay-off',
    stripId,
    stripName: strip.name,
    driverStripId: strip.driverStripId,
    relayIndex: null,
    message: `All outlets on ${strip.name} turned ${state}`,
    cause,
  });
  return toStripDTO(strip, db.data.relays);
}

/** Wires the driver's autonomous events (ticks, connectivity, overload) into DB + WS + notifications. Call once at startup. */
export function wireDriverEvents(driver: StripDriver): void {
  driver.onEvent((event) => {
    void handleDriverEvent(event).catch((err) => log.error('driver event handling failed', err));
    if (event.type === 'connectivity' && event.connectivity === 'online') {
      void autoAdoptStrip(driver, event.driverStripId).catch((err) => log.warn('auto-registration failed', err));
    }
  });
}

const PRIVATE_IPV4 = /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/;

/** Registers a strip that dialled in from the LAN but isn't in the fleet yet (e.g. right after provisioning). */
async function autoAdoptStrip(driver: StripDriver, driverStripId: string): Promise<void> {
  const db = getMainDb();
  if (db.data.strips.some((s) => s.driverStripId === driverStripId)) return;
  const found = (await driver.scanForStrips({ timeoutMs: 100 })).find((d) => d.driverStripId === driverStripId);
  if (!found) return;
  if (!PRIVATE_IPV4.test((found.ipAddress ?? '').replace(/^::ffff:/, ''))) return;
  try {
    const strip = await adoptStrip(driver, {
      driverStripId: found.driverStripId,
      model: found.model,
      relayCount: found.relayCount,
      ipAddress: found.ipAddress,
      suggestedName: found.suggestedName,
    });
    log.info(`auto-registered ${strip.name} (${found.driverStripId})`);
  } catch (err) {
    if (!db.data.strips.some((s) => s.driverStripId === driverStripId)) throw err;
  }
}

/**
 * Re-establishes every already-adopted strip with the driver and synchronizes its current
 * reported state. Cloud-backed drivers must never replay a stale local state as a command
 * during startup, because doing so could change a physical outlet unexpectedly.
 * Call once at startup, after wireDriverEvents().
 */
export async function resyncDriverState(driver: StripDriver): Promise<void> {
  const db = getMainDb();
  for (const strip of db.data.strips) {
    try {
      await driver.adoptStrip(strip.driverStripId);
      const status = await driver.getStatus(strip.driverStripId);
      strip.connectivity = status.connectivity;
      for (const snapshotRelay of status.relays) {
        const relay = db.data.relays.find(
          (candidate) => candidate.stripId === strip.id && candidate.index === snapshotRelay.relayIndex,
        );
        if (!relay) continue;
        if (relay.state !== snapshotRelay.state) relay.lastChangedAt = status.timestamp;
        relay.state = snapshotRelay.state;
        const cloudName = status.relayNames?.[snapshotRelay.relayIndex];
        if (cloudName && isDefaultOutletName(relay.name, relay.index)) relay.name = cloudName;
      }
    } catch (err) {
      log.warn(`could not resync strip ${strip.name} (${strip.driverStripId}) with driver`, err);
    }
  }
  await db.write();
  log.info(`resynced ${db.data.strips.length} strip(s) with driver "${driver.name}"`);
}

async function handleDriverEvent(event: StripDriverEvent): Promise<void> {
  const db = getMainDb();

  if (event.type === 'status') {
    const strip = db.data.strips.find((s) => s.driverStripId === event.snapshot.driverStripId);
    if (!strip) return;
    const map = new Map<number, number>();
    const flipped: Array<{ relayIndex: number; state: RelayStateValue; changedAt: string }> = [];
    for (const r of event.snapshot.relays) {
      map.set(r.relayIndex, r.watts);
      const relay = db.data.relays.find((candidate) => candidate.stripId === strip.id && candidate.index === r.relayIndex);
      if (!relay) continue;
      if (relay.state !== r.state) {
        relay.lastChangedAt = event.snapshot.timestamp;
        flipped.push({ relayIndex: r.relayIndex, state: r.state, changedAt: relay.lastChangedAt });
      }
      relay.state = r.state;
      const cloudName = event.snapshot.relayNames?.[r.relayIndex];
      if (cloudName && isDefaultOutletName(relay.name, relay.index)) relay.name = cloudName;
    }
    strip.connectivity = event.snapshot.connectivity;
    await db.write();
    liveWatts.set(strip.id, map);
    if (event.snapshot.telemetry) liveTelemetry.set(strip.id, event.snapshot.telemetry);
    recordTick(event.snapshot.relays.map((r) => ({ stripId: strip.id, relayIndex: r.relayIndex, watts: r.watts })));
    for (const f of flipped) broadcast({ type: 'relay-state', stripId: strip.id, ...f });
    broadcast({
      type: 'power-tick',
      stripId: strip.id,
      relays: event.snapshot.relays,
      totalWatts: event.snapshot.totalWatts,
      timestamp: event.snapshot.timestamp,
      telemetry: event.snapshot.telemetry ?? null,
    });
    return;
  }

  if (event.type === 'connectivity') {
    const strip = db.data.strips.find((s) => s.driverStripId === event.driverStripId);
    if (!strip) return;
    strip.connectivity = event.connectivity;
    await db.write();
    broadcast({ type: 'strip-connectivity', stripId: strip.id, connectivity: event.connectivity });
    await logEvent({
      type: event.connectivity === 'online' ? 'online' : 'offline',
      stripId: strip.id,
      stripName: strip.name,
      driverStripId: event.driverStripId,
      relayIndex: null,
      message: `${strip.name} went ${event.connectivity}`,
      cause: 'driver',
    });
    if (event.connectivity === 'offline') {
      await notify({
        type: 'offline',
        title: `${strip.name} went offline`,
        body: 'Lost connection to the strip. It will reconnect automatically when back online.',
        severity: 'warning',
        relatedStripId: strip.id,
        relatedStripMac: event.driverStripId,
      });
    } else {
      await notify({
        type: 'online',
        title: `${strip.name} is back online`,
        body: 'Connection restored.',
        severity: 'info',
        relatedStripId: strip.id,
        relatedStripMac: event.driverStripId,
        push: false,
      });
    }
    return;
  }

  if (event.type === 'relay-changed' && event.cause !== 'command') {
    const strip = db.data.strips.find((s) => s.driverStripId === event.driverStripId);
    if (!strip) return;
    const relay = db.data.relays.find((r) => r.stripId === strip.id && r.index === event.relayIndex);
    if (!relay) return;
    relay.state = event.state;
    relay.lastChangedAt = new Date().toISOString();
    await db.write();
    broadcast({ type: 'relay-state', stripId: strip.id, relayIndex: relay.index, state: relay.state, changedAt: relay.lastChangedAt });
    if (event.cause !== 'overload') {
      await logEvent({
        type: relay.state === 'on' ? 'relay-on' : 'relay-off',
        stripId: strip.id,
        stripName: strip.name,
        driverStripId: event.driverStripId,
        relayIndex: relay.index,
        message: `${relay.name} turned ${relay.state}`,
        cause: 'driver',
      });
    }
    return;
  }

  if (event.type === 'overload') {
    const key = `${event.driverStripId}:${event.relayIndex}`;
    const lastAt = lastOverloadAt.get(key) ?? 0;
    if (Date.now() - lastAt < OVERLOAD_COOLDOWN_MS) return;
    lastOverloadAt.set(key, Date.now());
    const strip = db.data.strips.find((s) => s.driverStripId === event.driverStripId);
    if (!strip) return;
    const relay = db.data.relays.find((r) => r.stripId === strip.id && r.index === event.relayIndex);
    await logEvent({
      type: 'overload',
      stripId: strip.id,
      stripName: strip.name,
      driverStripId: event.driverStripId,
      relayIndex: event.relayIndex,
      message: `${relay?.name ?? `Outlet ${event.relayIndex + 1}`} exceeded safe load and was switched off`,
      cause: 'driver',
    });
    await notify({
      type: 'overload',
      title: `Overload protection triggered - ${strip.name}`,
      body: `${relay?.name ?? `Outlet ${event.relayIndex + 1}`} was switched off to prevent overload.`,
      severity: 'critical',
      relatedStripId: strip.id,
      relatedStripMac: event.driverStripId,
      relatedRelayIndex: event.relayIndex,
    });
  }
}

function isDefaultOutletName(name: string, relayIndex: number): boolean {
  return name === `Outlet ${relayIndex + 1}`;
}

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

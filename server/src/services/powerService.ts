import type { PowerHistoryResponse, PowerPointDTO, PowerRange, PowerSummaryResponse, StripDTO } from '../../../shared/types.js';
import { getMainDb } from '../db/mainDb.js';
import { getPowerDb } from '../db/powerDb.js';
import type { PowerResolution, StoredPowerSample } from '../db/schema.js';
import { newId } from '../utils/id.js';

interface TickSample {
  stripId: string;
  relayIndex: number;
  watts: number;
}

// In-memory buffer of raw 5s watt readings, flushed into 1-minute rollup rows.
const minuteBuffer = new Map<string, number[]>();

function bufferKey(stripId: string, relayIndex: number): string {
  return `${stripId}:${relayIndex}`;
}

export function recordTick(samples: TickSample[]): void {
  for (const s of samples) {
    const key = bufferKey(s.stripId, s.relayIndex);
    const arr = minuteBuffer.get(key) ?? [];
    arr.push(s.watts);
    minuteBuffer.set(key, arr);
  }
}

function startOfMinute(d: Date): string {
  const copy = new Date(d);
  copy.setSeconds(0, 0);
  return copy.toISOString();
}

function startOfHour(d: Date): string {
  const copy = new Date(d);
  copy.setMinutes(0, 0, 0);
  return copy.toISOString();
}

function startOfDay(d: Date): string {
  const copy = new Date(d);
  copy.setHours(0, 0, 0, 0);
  return copy.toISOString();
}

function kwhFromAvgWatts(avgW: number, hours: number): number {
  return (avgW / 1000) * hours;
}

export async function flushMinuteBucket(): Promise<void> {
  if (minuteBuffer.size === 0) return;
  const db = getPowerDb();
  const bucketStart = startOfMinute(new Date());
  for (const [key, samples] of minuteBuffer.entries()) {
    const [stripId, relayIndexStr] = key.split(':');
    const relayIndex = Number(relayIndexStr);
    const avgW = samples.reduce((a, b) => a + b, 0) / samples.length;
    const row: StoredPowerSample = {
      id: newId('pwr'),
      stripId,
      relayIndex,
      resolution: 'minute',
      bucketStart,
      avgW: round2(avgW),
      minW: round2(Math.min(...samples)),
      maxW: round2(Math.max(...samples)),
      kwh: kwhFromAvgWatts(avgW, 1 / 60),
    };
    db.data.samples.push(row);
  }
  minuteBuffer.clear();

  const cutoff = new Date(Date.now() - 48 * 60 * 60 * 1000).toISOString();
  db.data.samples = db.data.samples.filter((s) => s.resolution !== 'minute' || s.bucketStart >= cutoff);
  await db.write();
}

async function rollup(fromResolution: PowerResolution, toResolution: PowerResolution, bucketStartOf: (d: Date) => string, retentionMs: number | null): Promise<void> {
  const db = getPowerDb();
  const now = new Date();
  const targetBucket = bucketStartOf(now);
  const source = db.data.samples.filter((s) => s.resolution === fromResolution && s.bucketStart < targetBucket && s.bucketStart >= new Date(now.getTime() - 2 * periodMsFor(toResolution)).toISOString());

  const groups = new Map<string, StoredPowerSample[]>();
  for (const s of source) {
    const bucket = bucketStartOf(new Date(s.bucketStart));
    if (bucket >= targetBucket) continue;
    const key = `${s.stripId}:${s.relayIndex}:${bucket}`;
    const arr = groups.get(key) ?? [];
    arr.push(s);
    groups.set(key, arr);
  }
  if (groups.size === 0) return;

  const existingKeys = new Set(
    db.data.samples.filter((s) => s.resolution === toResolution).map((s) => `${s.stripId}:${s.relayIndex}:${s.bucketStart}`),
  );

  for (const [key, rows] of groups.entries()) {
    if (existingKeys.has(key)) continue;
    const [stripId, relayIndexStr, bucketStart] = key.split(':');
    const relayIndex = Number(relayIndexStr);
    const avgW = rows.reduce((a, r) => a + r.avgW, 0) / rows.length;
    db.data.samples.push({
      id: newId('pwr'),
      stripId,
      relayIndex,
      resolution: toResolution,
      bucketStart,
      avgW: round2(avgW),
      minW: round2(Math.min(...rows.map((r) => r.minW))),
      maxW: round2(Math.max(...rows.map((r) => r.maxW))),
      kwh: round4(rows.reduce((a, r) => a + r.kwh, 0)),
    });
  }

  if (retentionMs !== null) {
    const cutoff = new Date(now.getTime() - retentionMs).toISOString();
    db.data.samples = db.data.samples.filter((s) => s.resolution !== fromResolution || s.bucketStart >= cutoff);
  }
  await db.write();
}

function periodMsFor(resolution: PowerResolution): number {
  if (resolution === 'hour') return 60 * 60 * 1000;
  if (resolution === 'day') return 24 * 60 * 60 * 1000;
  return 60 * 1000;
}

export async function rollupHours(): Promise<void> {
  await rollup('minute', 'hour', startOfHour, null);
}

export async function rollupDays(): Promise<void> {
  await rollup('hour', 'day', startOfDay, null);
}

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}
function round4(n: number): number {
  return Math.round(n * 10000) / 10000;
}

const RANGE_CONFIG: Record<PowerRange, { resolution: PowerResolution; sinceMs: number | null; downsampleTo?: number }> = {
  day: { resolution: 'minute', sinceMs: 24 * 60 * 60 * 1000, downsampleTo: 144 },
  week: { resolution: 'hour', sinceMs: 7 * 24 * 60 * 60 * 1000 },
  month: { resolution: 'day', sinceMs: 31 * 24 * 60 * 60 * 1000 },
  '7d': { resolution: 'day', sinceMs: 7 * 24 * 60 * 60 * 1000 },
  '30d': { resolution: 'day', sinceMs: 30 * 24 * 60 * 60 * 1000 },
  '90d': { resolution: 'day', sinceMs: 90 * 24 * 60 * 60 * 1000 },
  all: { resolution: 'day', sinceMs: null, downsampleTo: 365 },
};

function downsample(points: PowerPointDTO[], targetCount: number): PowerPointDTO[] {
  if (points.length <= targetCount) return points;
  const groupSize = Math.ceil(points.length / targetCount);
  const result: PowerPointDTO[] = [];
  for (let i = 0; i < points.length; i += groupSize) {
    const chunk = points.slice(i, i + groupSize);
    result.push({
      bucketStart: chunk[0].bucketStart,
      avgW: round2(chunk.reduce((a, p) => a + p.avgW, 0) / chunk.length),
      minW: round2(Math.min(...chunk.map((p) => p.minW))),
      maxW: round2(Math.max(...chunk.map((p) => p.maxW))),
      kwh: round4(chunk.reduce((a, p) => a + p.kwh, 0)),
    });
  }
  return result;
}

export function getHistory(stripId: string, relayIndex: number | null, range: PowerRange): PowerHistoryResponse {
  const db = getPowerDb();
  const config = RANGE_CONFIG[range];
  const since = config.sinceMs === null ? null : new Date(Date.now() - config.sinceMs).toISOString();
  const rows = db.data.samples.filter(
    (s) => s.stripId === stripId && s.resolution === config.resolution && (since === null || s.bucketStart >= since) && (relayIndex === null || s.relayIndex === relayIndex),
  );

  const byBucket = new Map<string, StoredPowerSample[]>();
  for (const row of rows) {
    const arr = byBucket.get(row.bucketStart) ?? [];
    arr.push(row);
    byBucket.set(row.bucketStart, arr);
  }
  let points: PowerPointDTO[] = [...byBucket.entries()]
    .sort(([a], [b]) => (a < b ? -1 : 1))
    .map(([bucketStart, rowsAtBucket]) => ({
      bucketStart,
      avgW: round2(rowsAtBucket.reduce((a, r) => a + r.avgW, 0)),
      minW: round2(rowsAtBucket.reduce((a, r) => a + r.minW, 0)),
      maxW: round2(rowsAtBucket.reduce((a, r) => a + r.maxW, 0)),
      kwh: round4(rowsAtBucket.reduce((a, r) => a + r.kwh, 0)),
    }));

  if (config.downsampleTo) points = downsample(points, config.downsampleTo);

  return { stripId, relayIndex, range, points };
}

export async function getCloudEnergyHistory(
  strip: StripDTO,
  relayIndex: number | null,
  range: PowerRange,
  days: number,
  getEnergy: (driverStripId: string, days: number) => Promise<{ buckets: Array<{ day: string; kwh: number; byOutlet: Record<string, number> }> }>,
): Promise<PowerHistoryResponse> {
  const response = await getEnergy(strip.driverStripId, days);
  const points = response.buckets.map((bucket) => {
    const kwh = relayIndex === null ? bucket.kwh : bucket.byOutlet[String(relayIndex + 1)] ?? 0;
    const avgW = kwh * 1000 / 24;
    return {
      bucketStart: new Date(`${bucket.day}T00:00:00`).toISOString(),
      avgW: round2(avgW),
      minW: 0,
      maxW: round2(avgW),
      kwh: round4(kwh),
    };
  });
  return { stripId: strip.id, relayIndex, range, points };
}

export function getSummary(stripId: string, liveWatts: number): PowerSummaryResponse {
  const db = getPowerDb();
  const settings = getMainDb().data.settings;
  const since = startOfDay(new Date());
  const rows = db.data.samples.filter((s) => s.stripId === stripId && s.resolution === 'minute' && s.bucketStart >= since);
  const todayKwh = round4(rows.reduce((a, r) => a + r.kwh, 0));
  return {
    stripId,
    todayKwh,
    todayCost: round2(todayKwh * settings.ratePerKwh),
    currency: settings.currency,
    liveWatts,
  };
}

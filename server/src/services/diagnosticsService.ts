import fs from 'node:fs';
import path from 'node:path';
import { dataFilePath } from '../db/paths.js';

export type DiagSource = 'server' | 'android' | 'web';
export type DiagLevel = 'debug' | 'info' | 'warn' | 'error';

export interface DiagEntry {
  ts: string;
  source: DiagSource;
  level: DiagLevel;
  tag: string;
  msg: string;
  device?: string;
  version?: string;
}

const MAX_LOG_BYTES = 4 * 1024 * 1024;
const MAX_CRASHES = 50;
const MAX_SCREENS = 60;
const MAX_MSG = 2000;

const dir = dataFilePath('diagnostics');
const crashDir = path.join(dir, 'crashes');
const screenDir = path.join(dir, 'screens');
const logFile = path.join(dir, 'logs.jsonl');
const logFileOld = path.join(dir, 'logs.1.jsonl');

function ensureDirs(): void {
  fs.mkdirSync(crashDir, { recursive: true });
  fs.mkdirSync(screenDir, { recursive: true });
}

const LEVELS: DiagLevel[] = ['debug', 'info', 'warn', 'error'];
const SOURCES: DiagSource[] = ['server', 'android', 'web'];

function safeName(value: string, max = 40): string {
  return value.replace(/[^A-Za-z0-9_.-]/g, '_').slice(0, max) || 'x';
}

function rotateIfNeeded(): void {
  try {
    if (fs.statSync(logFile).size > MAX_LOG_BYTES) fs.renameSync(logFile, logFileOld);
  } catch {
    // No log file yet.
  }
}

export function appendEntries(entries: DiagEntry[]): void {
  if (entries.length === 0) return;
  try {
    ensureDirs();
    rotateIfNeeded();
    fs.appendFileSync(logFile, entries.map((e) => JSON.stringify(e)).join('\n') + '\n');
  } catch {
    // Diagnostics must never take the server down.
  }
}

export function appendServer(level: DiagLevel, tag: string, msg: string): void {
  appendEntries([{ ts: new Date().toISOString(), source: 'server', level, tag, msg: msg.slice(0, MAX_MSG) }]);
}

/** Normalises untrusted client entries before they hit disk. */
export function sanitizeEntries(raw: unknown, source: DiagSource, device: string, version: string): DiagEntry[] {
  if (!Array.isArray(raw)) return [];
  return raw.slice(0, 300).flatMap((item): DiagEntry[] => {
    if (!item || typeof item !== 'object') return [];
    const o = item as Record<string, unknown>;
    const level = LEVELS.includes(o.level as DiagLevel) ? (o.level as DiagLevel) : 'info';
    const parsed = typeof o.ts === 'string' || typeof o.ts === 'number' ? new Date(o.ts) : new Date();
    return [
      {
        ts: Number.isNaN(parsed.getTime()) ? new Date().toISOString() : parsed.toISOString(),
        source,
        level,
        tag: String(o.tag ?? '').slice(0, 60),
        msg: String(o.msg ?? '').slice(0, MAX_MSG),
        device,
        version,
      },
    ];
  });
}

export function parseSource(value: unknown): DiagSource {
  return SOURCES.includes(value as DiagSource) ? (value as DiagSource) : 'android';
}

function readLines(file: string): DiagEntry[] {
  try {
    return fs
      .readFileSync(file, 'utf8')
      .split('\n')
      .filter(Boolean)
      .flatMap((line) => {
        try {
          return [JSON.parse(line) as DiagEntry];
        } catch {
          return [];
        }
      });
  } catch {
    return [];
  }
}

export interface LogQuery {
  source?: string;
  level?: string;
  since?: string;
  q?: string;
  device?: string;
  limit?: number;
}

export function queryLogs(query: LogQuery): DiagEntry[] {
  const minLevel = LEVELS.indexOf((query.level as DiagLevel) ?? 'debug');
  const since = query.since ? new Date(query.since).getTime() : 0;
  const q = query.q?.toLowerCase();
  const all = [...readLines(logFileOld), ...readLines(logFile)];
  const out = all.filter(
    (e) =>
      (!query.source || e.source === query.source) &&
      (!query.device || e.device === query.device) &&
      LEVELS.indexOf(e.level) >= Math.max(minLevel, 0) &&
      (!since || new Date(e.ts).getTime() >= since) &&
      (!q || `${e.tag} ${e.msg}`.toLowerCase().includes(q)),
  );
  return out.slice(-Math.min(Math.max(query.limit ?? 200, 1), 1000));
}

function prune(folder: string, keep: number): void {
  const files = fs
    .readdirSync(folder)
    .map((name) => ({ name, t: fs.statSync(path.join(folder, name)).mtimeMs }))
    .sort((a, b) => b.t - a.t);
  for (const f of files.slice(keep)) fs.rmSync(path.join(folder, f.name), { force: true });
}

export function saveCrash(source: DiagSource, device: string, payload: Record<string, unknown>): string {
  ensureDirs();
  const name = `${Date.now()}_${safeName(source)}_${safeName(device)}.json`;
  const text = JSON.stringify({ receivedAt: new Date().toISOString(), source, device, ...payload }, null, 2);
  fs.writeFileSync(path.join(crashDir, name), text.slice(0, 900_000));
  prune(crashDir, MAX_CRASHES);
  appendEntries([
    {
      ts: new Date().toISOString(),
      source,
      level: 'error',
      tag: 'crash',
      msg: String(payload.summary ?? payload.message ?? 'crash report').slice(0, MAX_MSG),
      device,
    },
  ]);
  return name;
}

export function listCrashes(): { name: string; at: string; summary: string }[] {
  ensureDirs();
  return fs
    .readdirSync(crashDir)
    .sort()
    .reverse()
    .map((name) => {
      let summary = '';
      try {
        const j = JSON.parse(fs.readFileSync(path.join(crashDir, name), 'utf8')) as Record<string, unknown>;
        summary = String(j.summary ?? j.message ?? '').slice(0, 200);
      } catch {
        // Unreadable report: still list the file.
      }
      return { name, at: new Date(fs.statSync(path.join(crashDir, name)).mtimeMs).toISOString(), summary };
    });
}

export function crashPath(name: string): string | null {
  const file = path.join(crashDir, path.basename(name));
  return fs.existsSync(file) ? file : null;
}

export function saveScreenshot(device: string, screen: string, data: Buffer): string {
  ensureDirs();
  const name = `${Date.now()}_${safeName(device)}_${safeName(screen)}.jpg`;
  fs.writeFileSync(path.join(screenDir, name), data);
  prune(screenDir, MAX_SCREENS);
  return name;
}

export function listScreenshots(): { name: string; at: string; bytes: number }[] {
  ensureDirs();
  return fs
    .readdirSync(screenDir)
    .sort()
    .reverse()
    .map((name) => {
      const st = fs.statSync(path.join(screenDir, name));
      return { name, at: new Date(st.mtimeMs).toISOString(), bytes: st.size };
    });
}

export function screenshotPath(name: string): string | null {
  const file = path.join(screenDir, path.basename(name));
  return fs.existsSync(file) ? file : null;
}

export function summary() {
  const logs = queryLogs({ limit: 1000 });
  const since = Date.now() - 24 * 3600_000;
  const recent = logs.filter((e) => new Date(e.ts).getTime() >= since);
  const devices: Record<string, { source: string; version?: string; lastSeen: string; errors24h: number }> = {};
  for (const e of logs) {
    if (!e.device) continue;
    const d = (devices[e.device] ??= { source: e.source, version: e.version, lastSeen: e.ts, errors24h: 0 });
    d.lastSeen = e.ts;
    d.version = e.version ?? d.version;
    if (e.level === 'error' && new Date(e.ts).getTime() >= since) d.errors24h += 1;
  }
  return {
    now: new Date().toISOString(),
    counts24h: {
      error: recent.filter((e) => e.level === 'error').length,
      warn: recent.filter((e) => e.level === 'warn').length,
      total: recent.length,
    },
    devices,
    crashes: listCrashes().slice(0, 10),
    screenshots: listScreenshots().slice(0, 10),
    recentErrors: logs.filter((e) => e.level === 'error').slice(-15),
  };
}

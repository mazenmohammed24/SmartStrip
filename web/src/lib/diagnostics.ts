const BASE = `${import.meta.env.BASE_URL.replace(/\/$/, '')}/api/diagnostics`;
const DEVICE = `web-${navigator.platform || 'browser'}`.slice(0, 40);
const FLUSH_MS = 10_000;
const MAX_QUEUE = 100;

interface Entry {
  ts: string;
  level: 'warn' | 'error';
  tag: string;
  msg: string;
}

const queue: Entry[] = [];
const seen = new Map<string, number>();

function push(level: Entry['level'], tag: string, msg: string): void {
  const key = `${tag}|${msg}`;
  const count = (seen.get(key) ?? 0) + 1;
  seen.set(key, count);
  // Report a repeating error once, then at 10, 100, ...; keeps an error storm from flooding the log.
  if (count > 1 && !(count === 10 || count === 100 || count === 1000)) return;
  if (queue.length >= MAX_QUEUE) queue.shift();
  queue.push({ ts: new Date().toISOString(), level, tag, msg: count > 1 ? `${msg} (x${count})` : msg });
}

function flush(): void {
  if (queue.length === 0) return;
  const entries = queue.splice(0, queue.length);
  const body = JSON.stringify({ source: 'web', device: DEVICE, version: navigator.userAgent.slice(0, 120), entries });
  const blob = new Blob([body], { type: 'application/json' });
  if (!navigator.sendBeacon(`${BASE}/logs`, blob)) {
    void fetch(`${BASE}/logs`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body, keepalive: true }).catch(() => {});
  }
}

export function installDiagnostics(): void {
  window.addEventListener('error', (e) => {
    push('error', 'window', `${e.message} @ ${e.filename}:${e.lineno}:${e.colno}${e.error?.stack ? `\n${e.error.stack}` : ''}`);
  });
  window.addEventListener('unhandledrejection', (e) => {
    const r = e.reason;
    push('error', 'promise', r instanceof Error ? (r.stack ?? r.message) : String(r));
  });
  const original = console.error.bind(console);
  console.error = (...args: unknown[]) => {
    original(...args);
    push('error', 'console', args.map((a) => (a instanceof Error ? (a.stack ?? a.message) : typeof a === 'string' ? a : safeJson(a))).join(' ').slice(0, 1500));
  };
  window.setInterval(flush, FLUSH_MS);
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') flush();
  });
}

function safeJson(value: unknown): string {
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

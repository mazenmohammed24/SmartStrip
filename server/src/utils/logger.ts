import { appendServer } from '../services/diagnosticsService.js';

type LogLevel = 'info' | 'warn' | 'error' | 'debug';

function timestamp(): string {
  return new Date().toISOString();
}

function write(level: LogLevel, scope: string, message: string, extra?: unknown): void {
  const line = `[${timestamp()}] [${level.toUpperCase()}] [${scope}] ${message}`;
  const fn = level === 'error' ? console.error : level === 'warn' ? console.warn : console.log;
  if (extra !== undefined) {
    fn(line, extra);
  } else {
    fn(line);
  }
  if (level === 'warn' || level === 'error') {
    const detail = extra instanceof Error ? (extra.stack ?? extra.message) : extra !== undefined ? String(extra) : '';
    appendServer(level, scope, detail ? ` ` : message);
  }
}

export function createLogger(scope: string) {
  return {
    info: (message: string, extra?: unknown) => write('info', scope, message, extra),
    warn: (message: string, extra?: unknown) => write('warn', scope, message, extra),
    error: (message: string, extra?: unknown) => write('error', scope, message, extra),
    debug: (message: string, extra?: unknown) => write('debug', scope, message, extra),
  };
}

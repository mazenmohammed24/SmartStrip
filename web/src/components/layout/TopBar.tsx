import { useEffect, useState } from 'react';
import { ArrowLeft, TriangleAlert } from 'lucide-react';
import { api } from '../../lib/apiClient';
import { getConnectionStatus, onConnectionStatus, type ConnectionStatus } from '../../lib/wsClient';
import { NotificationBell } from './NotificationBell';

interface DriverHealth {
  name?: string;
  authFailed?: boolean;
  lastError?: string | null;
}

export function TopBar({ title, onBack }: { title: string; onBack?: () => void }) {
  const [status, setStatus] = useState<ConnectionStatus>(getConnectionStatus());
  const [driver, setDriver] = useState<DriverHealth | null>(null);
  useEffect(() => onConnectionStatus(setStatus), []);

  // Poll slowly: this is a session-health indicator, not a live data feed.
  useEffect(() => {
    let cancelled = false;
    const check = async () => {
      try {
        const health = await api.get<{ driver: DriverHealth | null }>('/health');
        if (!cancelled) setDriver(health.driver);
      } catch {
        // The app-level status pill already covers transport failures.
      }
    };
    void check();
    const interval = setInterval(() => void check(), 30_000);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, []);

  const tokenExpired = driver?.authFailed === true;
  const subtitle = status === 'open' ? 'Live sync active' : status === 'connecting' ? 'Connecting to live sync' : 'Live sync offline';

  return (
    <header className="pt-safe sticky top-0 z-30 border-b border-border bg-surface/92 px-4 py-3 backdrop-blur-xl sm:px-6">
      <div className="flex items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2">
          {onBack && (
            <button type="button" onClick={onBack} className="-ml-1 flex h-9 w-9 items-center justify-center rounded-xl border border-border text-text hover:bg-surface-hover" aria-label="Back">
              <ArrowLeft size={19} strokeWidth={1.8} />
            </button>
          )}
          <div className="min-w-0">
            <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-primary">SmartStrip</p>
            <h1 className="truncate text-lg font-semibold leading-tight text-text">{title}</h1>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {tokenExpired ? (
            <span className="flex shrink-0 items-center gap-2 rounded-full border border-danger bg-danger-bg px-3 py-1.5 text-xs font-medium text-danger">
              <TriangleAlert size={14} strokeWidth={1.8} />
              Voltra token expired
            </span>
          ) : (
            <span className="hidden shrink-0 items-center gap-2 rounded-full border border-border bg-surface-alt px-3 py-1.5 text-xs text-text-muted sm:flex">
              <span className={`h-2 w-2 rounded-full ${status === 'open' ? 'bg-success' : status === 'connecting' ? 'bg-warning' : 'bg-danger'}`} />
              {subtitle}
            </span>
          )}
          <NotificationBell />
        </div>
      </div>
    </header>
  );
}

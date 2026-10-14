import { useEffect, useState } from 'react';
import { ArrowLeft } from 'lucide-react';
import { getConnectionStatus, onConnectionStatus, type ConnectionStatus } from '../../lib/wsClient';
import { NotificationBell } from './NotificationBell';

export function TopBar({ title, onBack }: { title: string; onBack?: () => void }) {
  const [status, setStatus] = useState<ConnectionStatus>(getConnectionStatus());
  useEffect(() => onConnectionStatus(setStatus), []);

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
          <span className="hidden shrink-0 items-center gap-2 rounded-full border border-border bg-surface-alt px-3 py-1.5 text-xs text-text-muted sm:flex">
            <span className={`h-2 w-2 rounded-full ${status === 'open' ? 'bg-success' : status === 'connecting' ? 'bg-warning' : 'bg-danger'}`} />
            {subtitle}
          </span>
          <NotificationBell />
        </div>
      </div>
    </header>
  );
}

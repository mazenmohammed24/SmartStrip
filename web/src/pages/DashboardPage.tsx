import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Activity, CalendarClock, Home, LayoutGrid, List, Plus, Power, Search, Settings2 } from 'lucide-react';
import type { StripDTO } from '@shared/types';
import { AddStripModal } from '../components/strips/AddStripModal';
import { DeviceIcon } from '../components/strips/IconPicker';
import { StripGrid } from '../components/strips/StripGrid';
import { WifiBars } from '../components/strips/WifiBars';
import { Spinner } from '../components/ui/Button';
import { EmptyState } from '../components/ui/EmptyState';
import { Input } from '../components/ui/Input';
import { Sheet } from '../components/ui/Sheet';
import { formatWatts } from '../lib/formatters';
import { getConnectionStatus, onConnectionStatus } from '../lib/wsClient';
import type { ConnectionStatus } from '../lib/wsClient';
import { useFleetStore } from '../store/fleetStore';

type View = 'grid' | 'list';
const VIEW_KEY = 'smartstrip.dashboard.view';

function useLive(): ConnectionStatus {
  const [status, setStatus] = useState(getConnectionStatus());
  useEffect(() => onConnectionStatus(setStatus), []);
  return status;
}

export default function DashboardPage() {
  const navigate = useNavigate();
  const strips = useFleetStore((s) => s.strips);
  const loading = useFleetStore((s) => s.loading);
  const loaded = useFleetStore((s) => s.loaded);
  const fetchStrips = useFleetStore((s) => s.fetchStrips);
  const setAllRelays = useFleetStore((s) => s.setAllRelays);
  const live = useLive();
  const [query, setQuery] = useState('');
  const [view, setView] = useState<View>(() => (localStorage.getItem(VIEW_KEY) === 'list' ? 'list' : 'grid'));
  const [picking, setPicking] = useState(false);
  const [adding, setAdding] = useState(false);

  useEffect(() => {
    void fetchStrips();
  }, [fetchStrips]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return strips;
    return strips.filter((s) => [s.name, s.location ?? '', s.model, ...s.relays.map((r) => r.name)].some((t) => t.toLowerCase().includes(q)));
  }, [strips, query]);

  if (loading && !loaded) {
    return (
      <div className="flex justify-center py-16">
        <Spinner size={28} />
      </div>
    );
  }

  if (strips.length === 0) {
    return (
      <>
        <EmptyState
          icon={<span>📡</span>}
          title="No strips in your fleet yet"
          description="Provision a new strip or pick up one that has already connected."
          action={
            <button type="button" onClick={() => setAdding(true)} className="inline-flex items-center justify-center gap-1.5 rounded-xl bg-primary px-4 py-2 text-sm font-medium text-primary-fg hover:bg-primary-hover">
              <Plus size={16} />
              Add strip
            </button>
          }
        />
        <AddStripModal open={adding} onClose={() => setAdding(false)} />
      </>
    );
  }

  const totalWatts = strips.reduce((a, s) => a + s.totalWatts, 0);
  const onlineStrips = strips.filter((s) => s.connectivity === 'online');
  const outletsOn = strips.reduce((a, s) => a + s.relays.filter((r) => r.state === 'on').length, 0);
  const outletsTotal = strips.reduce((a, s) => a + s.relays.length, 0);

  const openAutomations = () => (strips.length === 1 ? navigate(`/strips/${strips[0].id}/settings`) : setPicking(true));
  const allOff = () => void Promise.all(onlineStrips.map((s) => setAllRelays(s.id, 'off')));

  const changeView = (v: View) => {
    setView(v);
    localStorage.setItem(VIEW_KEY, v);
  };

  return (
    <div className="flex flex-col gap-3">
      <section className="rounded-2xl border border-border bg-surface p-4 shadow-[0_1px_2px_rgb(24_32_27/0.05)]">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-wide text-primary">
              Total power
              <span className={`inline-flex items-center gap-1 rounded-full px-1.5 py-0.5 text-[10px] ${live === 'open' ? 'bg-success-bg text-success' : 'bg-surface-alt text-text-muted'}`}>
                <span className={`h-1.5 w-1.5 rounded-full ${live === 'open' ? 'animate-pulse bg-success' : 'bg-text-muted'}`} />
                {live === 'open' ? 'LIVE' : live === 'connecting' ? 'CONNECTING' : 'OFFLINE'}
              </span>
            </p>
            <p className="mt-1 font-mono text-3xl font-semibold tabular-nums text-text">{formatWatts(totalWatts)}</p>
          </div>
          <div className="flex shrink-0 gap-1.5">
            <button type="button" onClick={() => setAdding(true)} className="inline-flex items-center gap-1.5 rounded-xl bg-primary px-3 py-2 text-xs font-semibold text-primary-fg hover:bg-primary-hover">
              <Plus size={14} />
              Add strip
            </button>
            <button type="button" onClick={allOff} disabled={outletsOn === 0} title="Turn every outlet off" className="inline-flex items-center gap-1.5 rounded-xl border border-border px-3 py-2 text-xs font-semibold text-text hover:bg-surface-hover disabled:opacity-40">
              <Power size={14} />
              All off
            </button>
          </div>
        </div>
        <div className="mt-3 grid grid-cols-3 gap-2">
          <Chip label="Strips online" value={`${onlineStrips.length}/${strips.length}`} />
          <Chip label="Outlets on" value={`${outletsOn}/${outletsTotal}`} />
          <Chip label="Rooms" value={String(new Set(strips.map((s) => s.location).filter(Boolean)).size)} />
        </div>
      </section>

      <nav className="grid grid-cols-4 gap-2" aria-label="Quick links">
        <QuickLink to="/power" icon={<Activity size={16} />} label="Usage" />
        <QuickLink to="/automations" icon={<CalendarClock size={16} />} label="Timers" />
        <QuickLink onClick={openAutomations} icon={<Settings2 size={16} />} label="Rules" />
        <QuickLink to="/rooms" icon={<Home size={16} />} label="Rooms" />
      </nav>

      <div className="flex items-center gap-2">
        <div className="relative flex-1">
          <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
          <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search strips and outlets" className="pl-9" aria-label="Search strips" />
        </div>
        <div className="flex rounded-xl bg-surface-alt p-1" role="group" aria-label="View">
          {(['grid', 'list'] as const).map((v) => (
            <button
              key={v}
              type="button"
              aria-pressed={view === v}
              aria-label={`${v} view`}
              onClick={() => changeView(v)}
              className={`rounded-lg p-1.5 transition-colors ${view === v ? 'bg-surface text-primary shadow-sm' : 'text-text-muted'}`}
            >
              {v === 'grid' ? <LayoutGrid size={15} /> : <List size={15} />}
            </button>
          ))}
        </div>
      </div>

      {filtered.length === 0 ? (
        <p className="py-10 text-center text-sm text-text-muted">No strips match “{query}”.</p>
      ) : view === 'grid' ? (
        <StripGrid strips={filtered} />
      ) : (
        <StripList strips={filtered} />
      )}

      <Sheet open={picking} onClose={() => setPicking(false)} title="Automations for…">
        <div className="flex flex-col gap-2">
          {strips.map((s) => (
            <Link key={s.id} to={`/strips/${s.id}/settings`} className="rounded-xl border border-border px-3 py-3 text-sm font-medium text-text hover:bg-surface-hover">
              {s.name}
            </Link>
          ))}
        </div>
      </Sheet>
      <AddStripModal open={adding} onClose={() => setAdding(false)} />
    </div>
  );
}

function Chip({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-surface-alt px-3 py-1.5">
      <p className="text-[10px] uppercase tracking-wide text-text-muted">{label}</p>
      <p className="text-sm font-semibold tabular-nums text-text">{value}</p>
    </div>
  );
}

function QuickLink({ to, onClick, icon, label }: { to?: string; onClick?: () => void; icon: ReactNode; label: string }) {
  const cls = 'flex items-center justify-center gap-1.5 rounded-xl border border-border bg-surface px-2 py-2 text-xs font-medium text-text transition hover:bg-surface-hover';
  const inner = (
    <>
      <span className="text-primary">{icon}</span>
      <span className="truncate">{label}</span>
    </>
  );
  return to ? (
    <Link to={to} className={cls}>
      {inner}
    </Link>
  ) : (
    <button type="button" onClick={onClick} className={cls}>
      {inner}
    </button>
  );
}

function StripList({ strips }: { strips: StripDTO[] }) {
  return (
    <div className="flex flex-col divide-y divide-border overflow-hidden rounded-xl border border-border bg-surface">
      {strips.map((s) => (
        <Link key={s.id} to={`/strips/${s.id}`} className="flex items-center gap-3 px-3 py-2.5 transition-colors hover:bg-surface-hover">
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-primary text-primary-fg">
            <DeviceIcon icon={s.icon} size={18} />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm font-semibold text-text">{s.name}</span>
            <span className="block truncate text-[11px] text-text-muted">
              {s.location ?? s.model} · {s.relays.filter((r) => r.state === 'on').length}/{s.relays.length} on
            </span>
          </span>
          {s.connectivity === 'online' ? <WifiBars rssi={s.telemetry?.rssi} size={14} /> : <span className="text-[11px] font-medium text-danger">{s.connectivity}</span>}
          <span className="w-16 text-right font-mono text-sm font-semibold tabular-nums text-text">{formatWatts(s.totalWatts)}</span>
        </Link>
      ))}
    </div>
  );
}

import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { EventDTO } from '@shared/types';
import { Clock3, History, Palette, Pencil, Thermometer, Zap } from 'lucide-react';
import { api } from '../../lib/apiClient';
import { formatClock, formatDuration, formatKwh, formatRelativeTime, formatWatts } from '../../lib/formatters';
import { useFleetStore } from '../../store/fleetStore';
import { useSettingsStore } from '../../store/settingsStore';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';
import { Modal } from '../ui/Modal';
import { Toggle } from '../ui/Toggle';
import { DeviceIcon, IconGrid } from './IconPicker';
import { RelayTimerPanel } from './RelayTimerModal';

type Tab = 'details' | 'icon' | 'timer' | 'history';

const TABS: Array<{ id: Tab; label: string; icon: typeof Pencil }> = [
  { id: 'details', label: 'Details', icon: Pencil },
  { id: 'icon', label: 'Icon', icon: Palette },
  { id: 'timer', label: 'Timer', icon: Clock3 },
  { id: 'history', label: 'History', icon: History },
];

export function OutletModal({ stripId, relayIndex, timerLabel, onClose }: { stripId: string | null; relayIndex: number | null; timerLabel?: string; onClose: () => void }) {
  const strip = useFleetStore((s) => s.strips.find((x) => x.id === stripId));
  const relay = strip?.relays.find((r) => r.index === relayIndex);
  const open = !!strip && !!relay;
  const [tab, setTab] = useState<Tab>('details');

  useEffect(() => {
    if (open) setTab('details');
  }, [open, stripId, relayIndex]);

  if (!strip || !relay) return null;

  return (
    <Modal
      open
      onClose={onClose}
      size="lg"
      title={
        <span className="flex items-center gap-2">
          <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary text-primary-fg">
            <DeviceIcon icon={relay.icon} size={18} />
          </span>
          <span className="truncate">{relay.name}</span>
          <span className="truncate text-xs font-normal text-text-muted">· {strip.name}</span>
        </span>
      }
    >
      <div className="mb-3 grid grid-cols-4 gap-1 rounded-xl bg-surface-alt p-1" role="tablist">
        {TABS.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            type="button"
            role="tab"
            aria-selected={tab === id}
            onClick={() => setTab(id)}
            className={`flex items-center justify-center gap-1.5 rounded-lg px-2 py-1.5 text-xs font-semibold ${tab === id ? 'bg-surface text-text shadow-sm' : 'text-text-muted hover:text-text'}`}
          >
            <Icon size={14} />
            {label}
          </button>
        ))}
      </div>

      {tab === 'details' && <DetailsTab stripId={strip.id} relayIndex={relay.index} />}
      {tab === 'icon' && <IconTab stripId={strip.id} relayIndex={relay.index} />}
      {tab === 'timer' && <TimerTab stripId={strip.id} relayIndex={relay.index} activeLabel={timerLabel} onDone={() => setTab('details')} />}
      {tab === 'history' && <HistoryTab stripId={strip.id} relayIndex={relay.index} />}
    </Modal>
  );
}

function useRelay(stripId: string, relayIndex: number) {
  const strip = useFleetStore((s) => s.strips.find((x) => x.id === stripId))!;
  const relay = strip.relays.find((r) => r.index === relayIndex)!;
  return { strip, relay };
}

function DetailsTab({ stripId, relayIndex }: { stripId: string; relayIndex: number }) {
  const { strip, relay } = useRelay(stripId, relayIndex);
  const renameRelay = useFleetStore((s) => s.renameRelay);
  const setRelay = useFleetStore((s) => s.setRelay);
  const [name, setName] = useState(relay.name);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [now, setNow] = useState(Date.now());
  const outlet = strip.telemetry?.outlets.find((o) => o.relayIndex === relay.index);
  const on = relay.state === 'on';

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);

  const save = async () => {
    const next = name.trim();
    if (!next || next === relay.name) return;
    setSaving(true);
    setError(null);
    try {
      await renameRelay(strip.id, relay.index, next);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Rename failed');
    } finally {
      setSaving(false);
    }
  };

  const since = formatDuration(now - new Date(relay.lastChangedAt).getTime());

  return (
    <div className="flex flex-col gap-3">
      <div className="flex gap-2">
        <Input value={name} maxLength={48} onChange={(e) => setName(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && void save()} aria-label="Outlet name" />
        <Button onClick={() => void save()} loading={saving} disabled={!name.trim() || name.trim() === relay.name}>
          Save
        </Button>
      </div>
      {error && <p className="rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger">{error}</p>}

      <div className="flex items-center justify-between rounded-xl border border-border px-3 py-2.5">
        <div>
          <p className="text-sm font-semibold text-text">{on ? 'On' : 'Off'}</p>
          <p className="text-xs text-text-muted">{on ? `for ${since}` : `off for ${since}`}</p>
        </div>
        <Toggle checked={on} onChange={(c) => void setRelay(strip.id, relay.index, c ? 'on' : 'off')} disabled={strip.connectivity !== 'online'} label={relay.name} size="lg" />
      </div>

      <div className="grid grid-cols-3 gap-2">
        <Stat icon={<Zap size={14} />} label="Power" value={on ? formatWatts(relay.watts) : '0 W'} />
        <Stat icon={<Zap size={14} />} label="Energy" value={outlet ? formatKwh(outlet.energyKwh) : '—'} />
        <Stat icon={<Thermometer size={14} />} label="Temp" value={outlet ? `${outlet.temperatureC}°C` : '—'} />
      </div>

      <Link to="/power" className="text-center text-xs font-medium text-primary hover:underline">
        Open consumption
      </Link>
    </div>
  );
}

function Stat({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
  return (
    <div className="rounded-xl bg-surface-alt px-3 py-2">
      <p className="flex items-center gap-1 text-[11px] text-text-muted">
        {icon}
        {label}
      </p>
      <p className="text-sm font-semibold tabular-nums text-text">{value}</p>
    </div>
  );
}

function IconTab({ stripId, relayIndex }: { stripId: string; relayIndex: number }) {
  const { relay } = useRelay(stripId, relayIndex);
  const setRelayIcon = useFleetStore((s) => s.setRelayIcon);
  return <IconGrid value={relay.icon} maxHeight="50vh" onSelect={(icon) => void setRelayIcon(stripId, relayIndex, icon)} />;
}

function TimerTab({ stripId, relayIndex, activeLabel, onDone }: { stripId: string; relayIndex: number; activeLabel?: string; onDone: () => void }) {
  const startRelayTimer = useFleetStore((s) => s.startRelayTimer);
  const cancelRelayTimer = useFleetStore((s) => s.cancelRelayTimer);
  return (
    <RelayTimerPanel
      activeTimerLabel={activeLabel}
      onDone={onDone}
      onStart={(state, minutes, mode) => startRelayTimer(stripId, relayIndex, state, minutes, mode)}
      onCancelTimer={() => cancelRelayTimer(stripId, relayIndex)}
    />
  );
}

function HistoryTab({ stripId, relayIndex }: { stripId: string; relayIndex: number }) {
  const timeFormat = useSettingsStore((s) => s.settings?.timeFormat ?? '24h');
  const [events, setEvents] = useState<EventDTO[] | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setEvents(null);
    api
      .get<{ events: EventDTO[] }>(`/events?stripId=${encodeURIComponent(stripId)}&relayIndex=${relayIndex}&limit=40`)
      .then((r) => !cancelled && setEvents(r.events))
      .catch(() => !cancelled && setError(true));
    return () => {
      cancelled = true;
    };
  }, [stripId, relayIndex]);

  if (error) return <p className="py-6 text-center text-sm text-danger">Could not load history.</p>;
  if (!events) return <p className="py-6 text-center text-sm text-text-muted">Loading…</p>;
  if (events.length === 0) return <p className="py-6 text-center text-sm text-text-muted">No activity recorded for this outlet yet.</p>;

  return (
    <ul className="flex max-h-[50vh] flex-col divide-y divide-border overflow-y-auto rounded-xl border border-border">
      {events.map((e) => (
        <li key={e.id} className="flex items-center gap-3 px-3 py-2">
          <span className={`h-2 w-2 shrink-0 rounded-full ${e.type === 'relay-on' ? 'bg-success' : e.type === 'overload' ? 'bg-danger' : 'bg-text-muted'}`} />
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm text-text">{e.message}</span>
            <span className="block text-[11px] text-text-muted">
              {e.cause} · {formatRelativeTime(e.createdAt)} · {formatClock(e.createdAt, timeFormat)}
            </span>
          </span>
        </li>
      ))}
    </ul>
  );
}

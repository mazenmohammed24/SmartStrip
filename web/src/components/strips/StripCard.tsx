import { memo, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight, Pencil, Power } from 'lucide-react';
import type { RelayDTO, StripDTO } from '@shared/types';
import { formatDuration, formatWatts } from '../../lib/formatters';
import { useFleetStore } from '../../store/fleetStore';
import type { RelayTimerState } from '../../store/fleetStore';
import { Toggle } from '../ui/Toggle';
import { DeviceIcon } from './IconPicker';
import { OutletModal } from './OutletModal';
import { StripEditModal } from './StripEditModal';
import { WifiBars } from './WifiBars';

function useNow(): number {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);
  return now;
}

export const StripCard = memo(function StripCard({ strip }: { strip: StripDTO }) {
  const setAllRelays = useFleetStore((s) => s.setAllRelays);
  const setRelay = useFleetStore((s) => s.setRelay);
  const relayTimers = useFleetStore((s) => s.relayTimers);
  const [editing, setEditing] = useState(false);
  const [outlet, setOutlet] = useState<number | null>(null);
  const now = useNow();
  const online = strip.connectivity === 'online';
  const onCount = strip.relays.filter((r) => r.state === 'on').length;
  const dot = online ? 'bg-success' : strip.connectivity === 'offline' ? 'bg-danger' : 'bg-warning';

  return (
    <article className="flex flex-col rounded-2xl border border-border bg-surface shadow-[0_1px_2px_rgb(24_32_27/0.05)]">
      <header className="flex items-center gap-2.5 px-3 pt-3">
        <button type="button" onClick={() => setEditing(true)} title="Edit strip" className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary text-primary-fg transition hover:brightness-110">
          <DeviceIcon icon={strip.icon} size={20} />
        </button>
        <div className="min-w-0 flex-1">
          <button type="button" onClick={() => setEditing(true)} className="group flex max-w-full items-center gap-1 text-left" title="Rename">
            <span className="truncate text-sm font-semibold text-text">{strip.name}</span>
            <Pencil size={11} className="shrink-0 text-text-muted opacity-0 transition group-hover:opacity-100" />
          </button>
          <p className="flex items-center gap-1.5 truncate text-[11px] text-text-muted">
            <span className={`h-1.5 w-1.5 shrink-0 rounded-full ${dot}`} />
            <span className="truncate">{online ? strip.location ?? strip.ipAddress ?? strip.model : strip.connectivity}</span>
          </p>
        </div>
        {online && <WifiBars rssi={strip.telemetry?.rssi} size={15} />}
        <Link to={`/strips/${strip.id}`} aria-label={`Open ${strip.name}`} className="flex h-8 w-8 items-center justify-center rounded-lg text-text-muted hover:bg-surface-hover hover:text-text">
          <ChevronRight size={18} />
        </Link>
      </header>

      <div className="flex items-center justify-between gap-2 px-3 pb-2 pt-2.5">
        <p className="flex items-baseline gap-1.5">
          <span className="font-mono text-xl font-semibold tabular-nums text-text">{formatWatts(strip.totalWatts)}</span>
          <span className="text-[11px] text-text-muted">
            {onCount}/{strip.relays.length} on
            {strip.telemetry?.voltageV != null && ` · ${Math.round(strip.telemetry.voltageV)} V`}
          </span>
        </p>
        <div className="flex gap-1.5">
          <button type="button" disabled={!online} onClick={() => void setAllRelays(strip.id, 'on')} className="rounded-lg bg-primary/10 px-2.5 py-1 text-[11px] font-semibold text-primary hover:bg-primary/20 disabled:opacity-40">
            All on
          </button>
          <button type="button" disabled={!online} onClick={() => void setAllRelays(strip.id, 'off')} className="flex items-center gap-1 rounded-lg bg-surface-alt px-2.5 py-1 text-[11px] font-semibold text-text-muted hover:text-text disabled:opacity-40">
            <Power size={11} />
            Off
          </button>
        </div>
      </div>

      <ul className="flex flex-col gap-1.5 px-2 pb-2">
        {strip.relays.map((relay) => (
          <OutletRow
            key={relay.index}
            strip={strip}
            relay={relay}
            now={now}
            timer={relayTimers[`${strip.id}:${relay.index}`]}
            disabled={!online}
            onToggle={(checked) => void setRelay(strip.id, relay.index, checked ? 'on' : 'off')}
            onOpen={() => setOutlet(relay.index)}
          />
        ))}
      </ul>

      {editing && <StripEditModal stripId={strip.id} onClose={() => setEditing(false)} />}
      {outlet !== null && (
        <OutletModal
          stripId={strip.id}
          relayIndex={outlet}
          timerLabel={timerLabel(relayTimers[`${strip.id}:${outlet}`], now)}
          onClose={() => setOutlet(null)}
        />
      )}
    </article>
  );
});

function OutletRow({
  strip,
  relay,
  now,
  timer,
  disabled,
  onToggle,
  onOpen,
}: {
  strip: StripDTO;
  relay: RelayDTO;
  now: number;
  timer?: RelayTimerState;
  disabled: boolean;
  onToggle: (checked: boolean) => void;
  onOpen: () => void;
}) {
  const on = relay.state === 'on';
  const temp = strip.telemetry?.outlets.find((o) => o.relayIndex === relay.index)?.temperatureC;
  const parts: string[] = [];
  if (on) {
    parts.push(formatDuration(now - new Date(relay.lastChangedAt).getTime()));
    parts.push(formatWatts(relay.watts));
    if (temp !== undefined && temp > 0) parts.push(`${temp}°C`);
  } else {
    parts.push('Off');
  }
  const timed = timerLabel(timer, now);

  return (
    <li className={`flex items-center gap-2 rounded-xl px-2 py-1.5 transition-colors ${on ? 'bg-success-bg/60' : 'bg-surface-alt/60'}`}>
      <button type="button" onClick={onOpen} className="flex min-w-0 flex-1 items-center gap-2.5 text-left" title={`Customise ${relay.name}`}>
        <span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${on ? 'bg-primary text-primary-fg' : 'bg-surface text-text-muted'}`}>
          <DeviceIcon icon={relay.icon} size={16} />
        </span>
        <span className="min-w-0">
          <span className="block truncate text-[13px] font-medium leading-tight text-text">{relay.name}</span>
          <span className="block truncate text-[11px] leading-tight text-text-muted">{timed ?? parts.join(' · ')}</span>
        </span>
      </button>
      <Toggle checked={on} onChange={onToggle} disabled={disabled} label={relay.name} />
    </li>
  );
}

function timerLabel(timer: RelayTimerState | undefined, now: number): string | undefined {
  if (!timer) return undefined;
  const remainingSeconds = Math.max(0, Math.ceil((new Date(timer.restoreAt).getTime() - now) / 1000));
  const hours = Math.floor(remainingSeconds / 3600);
  const minutes = Math.floor((remainingSeconds % 3600) / 60);
  const seconds = remainingSeconds % 60;
  const clock = hours > 0
    ? `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
    : `${minutes}:${String(seconds).padStart(2, '0')}`;
  return timer.mode === 'after' ? `${timer.state.toUpperCase()} in ${clock}` : `${timer.state.toUpperCase()} for ${clock}`;
}

import { Link } from 'react-router-dom';
import type { StripDTO } from '@shared/types';
import { formatWatts } from '../../lib/formatters';
import { useFleetStore } from '../../store/fleetStore';
import { Card, CardBody, CardHeader } from '../ui/Card';
import { AllOnAllOffButtons } from './AllOnAllOffButtons';
import { ConnectivityBadge } from './ConnectivityBadge';
import { RelayToggle } from './RelayToggle';
import { DeviceIcon, IconPicker } from './IconPicker';
import { useEffect, useState } from 'react';
import { RelayTimerModal } from './RelayTimerModal';

export function StripCard({ strip }: { strip: StripDTO }) {
  const setAllRelays = useFleetStore((s) => s.setAllRelays);
  const setRelay = useFleetStore((s) => s.setRelay);
  const setRelayIcon = useFleetStore((s) => s.setRelayIcon);
  const setStripIcon = useFleetStore((s) => s.setStripIcon);
  const startRelayTimer = useFleetStore((s) => s.startRelayTimer);
  const cancelRelayTimer = useFleetStore((s) => s.cancelRelayTimer);
  const relayTimers = useFleetStore((s) => s.relayTimers);
  const [pickingIconFor, setPickingIconFor] = useState<number | 'strip' | null>(null);
  const [timerRelayIndex, setTimerRelayIndex] = useState<number | null>(null);
  const [now, setNow] = useState(Date.now());
  const onCount = strip.relays.filter((r) => r.state === 'on').length;

  useEffect(() => {
    const interval = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(interval);
  }, []);

  return (
    <Card className="flex flex-col">
      <CardHeader>
        <div className="flex min-w-0 items-start gap-3">
          <button type="button" onClick={() => setPickingIconFor('strip')} title="Change strip icon" className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-primary text-primary-fg shadow-sm transition hover:-translate-y-0.5 hover:shadow-md">
            <DeviceIcon icon={strip.icon} size={22} />
          </button>
          <div className="min-w-0">
            <Link to={`/strips/${strip.id}`} className="truncate text-base font-semibold text-text hover:underline">
              {strip.name}
            </Link>
            <p className="truncate text-xs text-text-muted">{strip.location ?? strip.ipAddress ?? strip.model}</p>
          </div>
        </div>
        <ConnectivityBadge connectivity={strip.connectivity} />
      </CardHeader>
      <CardBody className="flex flex-1 flex-col justify-between gap-4">
        <div className="flex items-baseline gap-2">
          <span className="text-2xl font-semibold text-text">{formatWatts(strip.totalWatts)}</span>
          <span className="text-sm text-text-muted">
            {onCount}/{strip.relays.length} outlets on
          </span>
        </div>
        <AllOnAllOffButtons
          onAllOn={() => void setAllRelays(strip.id, 'on')}
          onAllOff={() => void setAllRelays(strip.id, 'off')}
          disabled={strip.connectivity !== 'online'}
        />
        <div className="flex flex-col gap-2">
          {strip.relays.map((relay) => (
            <RelayToggle
              key={relay.index}
              relay={relay}
              disabled={strip.connectivity !== 'online'}
              onToggle={(checked) => void setRelay(strip.id, relay.index, checked ? 'on' : 'off')}
              onIconClick={() => setPickingIconFor(relay.index)}
              onTimerClick={() => setTimerRelayIndex(relay.index)}
              timerLabel={timerLabel(relayTimers[`${strip.id}:${relay.index}`], now)}
            />
          ))}
        </div>
      </CardBody>
      <IconPicker
        open={pickingIconFor !== null}
        value={pickingIconFor === 'strip' ? strip.icon : strip.relays.find((relay) => relay.index === pickingIconFor)?.icon ?? 'idle'}
        onClose={() => setPickingIconFor(null)}
        onSelect={async (icon) => {
          if (pickingIconFor === 'strip') await setStripIcon(strip.id, icon);
          else if (typeof pickingIconFor === 'number') await setRelayIcon(strip.id, pickingIconFor, icon);
          setPickingIconFor(null);
        }}
      />
      <RelayTimerModal
        open={timerRelayIndex !== null}
        relayName={strip.relays.find((relay) => relay.index === timerRelayIndex)?.name ?? 'Outlet'}
        activeTimerLabel={timerRelayIndex === null ? undefined : timerLabel(relayTimers[`${strip.id}:${timerRelayIndex}`], now)}
        onClose={() => setTimerRelayIndex(null)}
        onStart={async (state, durationMinutes, mode) => {
          if (timerRelayIndex === null) return;
          await startRelayTimer(strip.id, timerRelayIndex, state, durationMinutes, mode);
        }}
        onCancelTimer={async () => {
          if (timerRelayIndex === null) return;
          await cancelRelayTimer(strip.id, timerRelayIndex);
        }}
      />
    </Card>
  );
}

function timerLabel(timer: ReturnType<typeof useFleetStore.getState>['relayTimers'][string] | undefined, now: number): string | undefined {
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

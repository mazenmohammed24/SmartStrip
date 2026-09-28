import type { RelayDTO } from '@shared/types';
import { Clock3 } from 'lucide-react';
import { formatWatts } from '../../lib/formatters';
import { Toggle } from '../ui/Toggle';
import { DeviceIcon } from './IconPicker';

export function RelayToggle({
  relay,
  onToggle,
  onRename,
  onIconClick,
  onTimerClick,
  timerLabel,
  disabled,
}: {
  relay: RelayDTO;
  onToggle: (checked: boolean) => void;
  onRename?: () => void;
  onIconClick?: () => void;
  onTimerClick?: () => void;
  timerLabel?: string;
  disabled?: boolean;
}) {
  return (
    <div className="flex items-center gap-3 rounded-xl border border-border bg-surface px-3 py-2.5">
      <button type="button" onClick={onIconClick} disabled={!onIconClick} className="flex h-10 w-10 items-center justify-center rounded-lg bg-surface-alt text-primary transition hover:bg-primary hover:text-primary-fg disabled:cursor-default disabled:bg-transparent disabled:text-text-muted" title={`Change ${relay.name} icon`}>
        <DeviceIcon icon={relay.icon} size={20} />
      </button>
      <button type="button" onClick={onRename} disabled={!onRename} className="min-w-0 flex-1 text-left disabled:cursor-default">
        <p className="truncate text-sm font-medium text-text">{relay.name}</p>
        <p className="text-xs text-text-muted">{timerLabel ?? (relay.state === 'on' ? formatWatts(relay.watts) : 'Off')}</p>
      </button>
      <Toggle checked={relay.state === 'on'} onChange={onToggle} disabled={disabled} label={relay.name} size="lg" />
      {onTimerClick && (
        <button
          type="button"
          onClick={onTimerClick}
          disabled={disabled}
          className="flex h-10 w-10 items-center justify-center rounded-lg border border-border text-text-muted hover:border-primary hover:text-primary disabled:cursor-not-allowed disabled:opacity-40"
          title={`Start timer for ${relay.name}`}
        >
          <Clock3 size={18} strokeWidth={1.8} />
        </button>
      )}
    </div>
  );
}

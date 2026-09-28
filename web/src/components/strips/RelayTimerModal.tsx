import { useEffect, useState } from 'react';
import type { RelayStateValue, RelayTimerMode } from '@shared/types';
import { Clock3, Power, Timer } from 'lucide-react';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';

const DURATIONS = [15, 30, 45, 60, 120, 180, 360, 720];

function durationLabel(minutes: number): string {
    if (minutes < 60) return `${minutes} min`;
    const hours = minutes / 60;
    return `${hours} ${hours === 1 ? 'hour' : 'hours'}`;
}

export function RelayTimerModal({
    open,
    relayName,
    activeTimerLabel,
    onClose,
    onStart,
    onCancelTimer,
}: {
    open: boolean;
    relayName: string;
    activeTimerLabel?: string;
    onClose: () => void;
    onStart: (state: RelayStateValue, durationMinutes: number, mode: RelayTimerMode) => Promise<void>;
    onCancelTimer: () => Promise<void>;
}) {
    const [mode, setMode] = useState<RelayTimerMode>('for');
    const [pending, setPending] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);

    // Clear stale feedback whenever the sheet is opened for a different outlet.
    useEffect(() => {
        if (open) {
            setPending(null);
            setError(null);
        }
    }, [open]);

    const handleStart = async (state: RelayStateValue, durationMinutes: number) => {
        if (pending) return;
        setPending(`${state}:${durationMinutes}`);
        setError(null);
        try {
            await onStart(state, durationMinutes, mode);
            onClose();
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to start timer');
        } finally {
            setPending(null);
        }
    };

    return (
        <Modal open={open} onClose={onClose} title={`Timer · ${relayName}`}>
            <div className="flex flex-col gap-4">
                {activeTimerLabel && (
                    <div className="flex items-center justify-between gap-3 rounded-xl border border-primary bg-success-bg px-3 py-2.5">
                        <div>
                            <p className="text-sm font-semibold text-text">Timer running</p>
                            <p className="text-xs text-text-muted">{activeTimerLabel}</p>
                        </div>
                        <Button
                            type="button"
                            variant="secondary"
                            size="sm"
                            disabled={pending !== null}
                            onClick={async () => {
                                setPending('cancel');
                                try {
                                    await onCancelTimer();
                                    onClose();
                                } catch (err) {
                                    setError(err instanceof Error ? err.message : 'Failed to cancel timer');
                                } finally {
                                    setPending(null);
                                }
                            }}
                        >
                            Cancel timer
                        </Button>
                    </div>
                )}

                <div className="grid grid-cols-2 gap-1 rounded-xl border border-border bg-surface-alt p-1">
                    <ModeButton
                        active={mode === 'for'}
                        onClick={() => setMode('for')}
                        icon={<Timer size={16} strokeWidth={1.8} />}
                        label="For"
                        hint="then revert"
                    />
                    <ModeButton
                        active={mode === 'after'}
                        onClick={() => setMode('after')}
                        icon={<Clock3 size={16} strokeWidth={1.8} />}
                        label="After"
                        hint="then switch"
                    />
                </div>

                <p className="text-xs text-text-muted">
                    {mode === 'for'
                        ? 'Switches the outlet now, then puts it back when the time is up.'
                        : 'Leaves the outlet as it is, then switches it when the time is up.'}
                </p>

                <StateRow
                    title={mode === 'for' ? 'Turn on for' : 'Turn on after'}
                    tone="on"
                    disabled={pending !== null}
                    pending={pending}
                    onPick={(minutes) => void handleStart('on', minutes)}
                />
                <StateRow
                    title={mode === 'for' ? 'Turn off for' : 'Turn off after'}
                    tone="off"
                    disabled={pending !== null}
                    pending={pending}
                    onPick={(minutes) => void handleStart('off', minutes)}
                />

                {error && <p className="rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger">{error}</p>}
            </div>
        </Modal>
    );
}

function ModeButton({
    active,
    onClick,
    icon,
    label,
    hint,
}: {
    active: boolean;
    onClick: () => void;
    icon: React.ReactNode;
    label: string;
    hint: string;
}) {
    return (
        <button
            type="button"
            onClick={onClick}
            className={`flex items-center justify-center gap-2 rounded-lg px-3 py-2 text-sm font-semibold ${active ? 'bg-surface text-text shadow-sm' : 'text-text-muted hover:text-text'
                }`}
        >
            {icon}
            {label}
            <span className="hidden text-[11px] font-normal text-text-muted sm:inline">{hint}</span>
        </button>
    );
}

function StateRow({
    title,
    tone,
    disabled,
    pending,
    onPick,
}: {
    title: string;
    tone: RelayStateValue;
    disabled: boolean;
    pending: string | null;
    onPick: (minutes: number) => void;
}) {
    return (
        <div className="rounded-xl border border-border bg-surface p-3">
            <div className="mb-2 flex items-center gap-2">
                <Power size={14} strokeWidth={1.8} className={tone === 'on' ? 'text-success' : 'text-text-muted'} />
                <p className={`text-sm font-semibold ${tone === 'on' ? 'text-success' : 'text-text'}`}>{title}</p>
            </div>
            <div className="grid grid-cols-4 gap-2">
                {DURATIONS.map((minutes) => (
                    <Button
                        key={minutes}
                        type="button"
                        variant={tone === 'on' ? 'primary' : 'secondary'}
                        size="sm"
                        disabled={disabled}
                        loading={pending === `${tone}:${minutes}`}
                        onClick={() => onPick(minutes)}
                    >
                        {durationLabel(minutes)}
                    </Button>
                ))}
            </div>
        </div>
    );
}
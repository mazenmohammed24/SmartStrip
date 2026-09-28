import type { RelayStateValue, RelayTimerDTO, RelayTimerMode } from '../../../shared/types.js';
import { getMainDb } from '../db/mainDb.js';
import type { StoredRelayTimer } from '../db/schema.js';
import type { StripDriver } from '../drivers/StripDriver.js';
import { broadcast } from '../ws/broadcast.js';
import { setRelay } from './fleetService.js';

function timerKey(stripId: string, relayIndex: number): string {
    return `${stripId}:${relayIndex}`;
}

function toDTO(timer: StoredRelayTimer): RelayTimerDTO {
    return {
        stripId: timer.stripId,
        relayIndex: timer.relayIndex,
        mode: timer.mode,
        state: timer.state,
        restoreState: timer.restoreState,
        restoreAt: timer.restoreAt,
    };
}

export function listRelayTimers(): RelayTimerDTO[] {
    const now = Date.now();
    return getMainDb()
        .data
        .relayTimers
        .filter((timer) => new Date(timer.restoreAt).getTime() > now)
        .map(toDTO);
}

/** Removes a pending timer without touching the outlet. */
export async function cancelRelayTimer(stripId: string, relayIndex: number): Promise<boolean> {
    const db = getMainDb();
    const key = timerKey(stripId, relayIndex);
    const before = db.data.relayTimers.length;
    db.data.relayTimers = db.data.relayTimers.filter((timer) => timerKey(timer.stripId, timer.relayIndex) !== key);
    const removed = db.data.relayTimers.length !== before;
    if (removed) {
        await db.write();
        broadcast({ type: 'relay-timer', stripId, relayIndex, mode: 'for', state: 'off', restoreState: null, restoreAt: null });
    }
    return removed;
}

/**
 * Starts a one-shot outlet timer.
 *
 * - `for`: switches the outlet now and reverts it when the duration elapses.
 * - `after`: leaves the outlet untouched and switches it when the duration elapses.
 */
export async function startRelayTimer(
    driver: StripDriver,
    stripId: string,
    relayIndex: number,
    state: RelayStateValue,
    durationMinutes: number,
    mode: RelayTimerMode,
): Promise<{ strip: Awaited<ReturnType<typeof setRelay>> | null; timer: RelayTimerDTO }> {
    const strip = mode === 'for' ? await setRelay(driver, stripId, relayIndex, state) : null;

    const timer: StoredRelayTimer = {
        stripId,
        relayIndex,
        mode,
        state,
        restoreState: mode === 'for' ? (state === 'on' ? 'off' : 'on') : null,
        restoreAt: new Date(Date.now() + durationMinutes * 60 * 1000).toISOString(),
        createdAt: new Date().toISOString(),
    };

    const db = getMainDb();
    const key = timerKey(stripId, relayIndex);
    db.data.relayTimers = db.data.relayTimers.filter((candidate) => timerKey(candidate.stripId, candidate.relayIndex) !== key);
    db.data.relayTimers.push(timer);
    await db.write();

    const dto = toDTO(timer);
    broadcast({ type: 'relay-timer', ...dto });
    return { strip, timer: dto };
}

export async function runDueRelayTimers(driver: StripDriver): Promise<void> {
    const db = getMainDb();
    const now = Date.now();
    const due = db.data.relayTimers.filter((timer) => new Date(timer.restoreAt).getTime() <= now);

    for (const timer of due) {
        const key = timerKey(timer.stripId, timer.relayIndex);
        // "for" timers revert to the opposite state; "after" timers apply their target state now.
        const actionState = timer.mode === 'for' ? timer.restoreState ?? (timer.state === 'on' ? 'off' : 'on') : timer.state;

        // Remove first so a failing command cannot spin forever on every tick.
        db.data.relayTimers = db.data.relayTimers.filter((candidate) => timerKey(candidate.stripId, candidate.relayIndex) !== key);
        await db.write();
        broadcast({ type: 'relay-timer', stripId: timer.stripId, relayIndex: timer.relayIndex, mode: timer.mode, state: timer.state, restoreState: timer.restoreState, restoreAt: null });

        await setRelay(driver, timer.stripId, timer.relayIndex, actionState, 'schedule');
    }
}
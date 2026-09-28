import type { StripDriver } from '../drivers/StripDriver.js';
import { pruneOldEvents } from '../services/eventService.js';
import { flushMinuteBucket, rollupDays, rollupHours } from '../services/powerService.js';
import { runDueRelayTimers } from '../services/relayTimerService.js';
import { createLogger } from '../utils/logger.js';
import { runDueSchedules } from './scheduleRunner.js';

const log = createLogger('ticker');

const SCHEDULE_CHECK_MS = 15_000;
const RELAY_TIMER_CHECK_MS = 1_000;
const MINUTE_FLUSH_MS = 60_000;
const HOUR_ROLLUP_MS = 60 * 60 * 1000;
const DAY_ROLLUP_MS = 24 * 60 * 60 * 1000;
const PRUNE_MS = 24 * 60 * 60 * 1000;

export interface TickerHandle {
  stop(): void;
}

export function startTicker(driver: StripDriver): TickerHandle {
  const timers: ReturnType<typeof setInterval>[] = [];

  const guarded = (name: string, fn: () => Promise<void>) => {
    return () => {
      fn().catch((err) => log.error(`${name} failed`, err));
    };
  };

  timers.push(setInterval(guarded('runDueSchedules', () => runDueSchedules(driver)), SCHEDULE_CHECK_MS));
  timers.push(setInterval(guarded('runDueRelayTimers', () => runDueRelayTimers(driver)), RELAY_TIMER_CHECK_MS));
  timers.push(setInterval(guarded('flushMinuteBucket', flushMinuteBucket), MINUTE_FLUSH_MS));
  timers.push(setInterval(guarded('rollupHours', rollupHours), HOUR_ROLLUP_MS));
  timers.push(setInterval(guarded('rollupDays', rollupDays), DAY_ROLLUP_MS));
  timers.push(setInterval(guarded('pruneOldEvents', () => pruneOldEvents()), PRUNE_MS));

  log.info('ticker started (schedules every 15s, power rollups hourly/daily)');

  return {
    stop: () => timers.forEach((t) => clearInterval(t)),
  };
}

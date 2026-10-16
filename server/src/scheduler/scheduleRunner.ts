import type { StripDriver } from '../drivers/StripDriver.js';
import { getMainDb } from '../db/mainDb.js';
import type { StoredSchedule } from '../db/schema.js';
import { logEvent } from '../services/eventService.js';
import { setAllRelays, setRelay } from '../services/fleetService.js';
import { notify } from '../services/notificationService.js';
import { defaultTimezone, disableSchedule, markFired } from '../services/scheduleService.js';
import { createLogger } from '../utils/logger.js';

const log = createLogger('scheduleRunner');

const DOW = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

/** Wall-clock time and weekday of `d` in an IANA zone (falls back to server zone if unknown). */
export function zonedParts(d: Date, zone: string): { hhmm: string; dow: number } {
  const fmt = (timeZone?: string) =>
    new Intl.DateTimeFormat('en-US', { timeZone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23', weekday: 'short' }).formatToParts(d);
  let parts: Intl.DateTimeFormatPart[];
  try {
    parts = fmt(zone);
  } catch {
    parts = fmt();
  }
  const get = (t: string) => parts.find((p) => p.type === t)?.value ?? '';
  return { hhmm: `${get('hour')}:${get('minute')}`, dow: DOW.indexOf(get('weekday')) };
}

function truncToMinute(iso: string): string {
  return iso.slice(0, 16); // "YYYY-MM-DDTHH:MM"
}

export async function fireSchedule(driver: StripDriver, schedule: StoredSchedule): Promise<void> {
  const db = getMainDb();
  const strip = db.data.strips.find((s) => s.id === schedule.stripId);
  if (!strip) return;

  if (schedule.relayIndex === null) {
    await setAllRelays(driver, schedule.stripId, schedule.action, 'schedule');
  } else {
    await setRelay(driver, schedule.stripId, schedule.relayIndex, schedule.action, 'schedule');
  }
  await markFired(schedule.id);
  if (schedule.repeat === 'once') await disableSchedule(schedule.id);

  const relay = schedule.relayIndex !== null ? db.data.relays.find((r) => r.stripId === schedule.stripId && r.index === schedule.relayIndex) : null;
  const target = relay ? relay.name : 'all outlets';
  await logEvent({
    type: 'schedule-fired',
    stripId: strip.id,
    stripName: strip.name,
    driverStripId: strip.driverStripId,
    relayIndex: schedule.relayIndex,
    message: `Automation "${schedule.name}" turned ${target} ${schedule.action}`,
    cause: 'schedule',
  });
  await notify({
    type: 'schedule-fired',
    title: 'Automation ran',
    body: `"${schedule.name}" - ${strip.name} (${target}) turned ${schedule.action}`,
    severity: 'info',
    relatedStripId: strip.id,
    relatedStripMac: strip.driverStripId,
    relatedRelayIndex: schedule.relayIndex,
  });
}

export async function runDueSchedules(driver: StripDriver): Promise<void> {
  const now = new Date();
  const nowMinuteKey = truncToMinute(now.toISOString());

  const db = getMainDb();
  const due = db.data.schedules.filter((s) => {
    if (!s.enabled || (s.lastFiredAt && truncToMinute(s.lastFiredAt) === nowMinuteKey)) return false;
    const { hhmm, dow } = zonedParts(now, s.timezone ?? defaultTimezone());
    return s.time === hhmm && (s.repeat === 'once' || s.daysOfWeek.includes(dow));
  });

  for (const schedule of due) {
    try {
      await fireSchedule(driver, schedule);
    } catch (err) {
      log.error(`failed to run schedule ${schedule.id}`, err);
    }
  }
}

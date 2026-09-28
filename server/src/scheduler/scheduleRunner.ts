import type { StripDriver } from '../drivers/StripDriver.js';
import { getMainDb } from '../db/mainDb.js';
import type { StoredSchedule } from '../db/schema.js';
import { logEvent } from '../services/eventService.js';
import { setAllRelays, setRelay } from '../services/fleetService.js';
import { notify } from '../services/notificationService.js';
import { markFired } from '../services/scheduleService.js';
import { createLogger } from '../utils/logger.js';

const log = createLogger('scheduleRunner');

function currentHHMM(d: Date): string {
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
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
  const hhmm = currentHHMM(now);
  const dow = now.getDay();
  const nowMinuteKey = truncToMinute(now.toISOString());

  const db = getMainDb();
  const due = db.data.schedules.filter(
    (s) => s.enabled && s.time === hhmm && s.daysOfWeek.includes(dow) && (!s.lastFiredAt || truncToMinute(s.lastFiredAt) !== nowMinuteKey),
  );

  for (const schedule of due) {
    try {
      await fireSchedule(driver, schedule);
    } catch (err) {
      log.error(`failed to run schedule ${schedule.id}`, err);
    }
  }
}

import type { ScheduleDTO } from '../../../shared/types.js';
import { getMainDb } from '../db/mainDb.js';
import type { StoredSchedule } from '../db/schema.js';
import { newId } from '../utils/id.js';

export function defaultTimezone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
}

function toDTO(s: StoredSchedule): ScheduleDTO {
  return {
    id: s.id,
    name: s.name,
    stripId: s.stripId,
    relayIndex: s.relayIndex,
    action: s.action,
    time: s.time,
    daysOfWeek: s.daysOfWeek,
    timezone: s.timezone ?? defaultTimezone(),
    repeat: s.repeat ?? 'weekly',
    enabled: s.enabled,
    lastFiredAt: s.lastFiredAt,
    createdAt: s.createdAt,
  };
}

export function listSchedules(): ScheduleDTO[] {
  return getMainDb().data.schedules.map(toDTO);
}

export interface CreateScheduleInput {
  name?: string;
  stripId: string;
  relayIndex: number | null;
  action: 'on' | 'off';
  time: string;
  daysOfWeek: number[];
  timezone?: string;
  repeat?: 'weekly' | 'once';
  enabled?: boolean;
}

export async function createSchedule(input: CreateScheduleInput): Promise<ScheduleDTO> {
  const db = getMainDb();
  const schedule: StoredSchedule = {
    id: newId('sched'),
    name: (input.name ?? '').trim(),
    stripId: input.stripId,
    relayIndex: input.relayIndex,
    action: input.action,
    time: input.time,
    daysOfWeek: input.daysOfWeek,
    timezone: input.timezone ?? defaultTimezone(),
    repeat: input.repeat ?? 'weekly',
    enabled: input.enabled ?? true,
    lastFiredAt: null,
    createdAt: new Date().toISOString(),
  };
  db.data.schedules.push(schedule);
  await db.write();
  return toDTO(schedule);
}

export async function updateSchedule(id: string, patch: Partial<CreateScheduleInput>): Promise<ScheduleDTO> {
  const db = getMainDb();
  const schedule = db.data.schedules.find((s) => s.id === id);
  if (!schedule) throw new Error('Schedule not found');
  Object.assign(schedule, patch, patch.name !== undefined ? { name: patch.name.trim() } : {});
  if (patch.enabled === true && schedule.repeat === 'once') schedule.lastFiredAt = null;
  await db.write();
  return toDTO(schedule);
}

export async function deleteSchedule(id: string): Promise<void> {
  const db = getMainDb();
  db.data.schedules = db.data.schedules.filter((s) => s.id !== id);
  await db.write();
}

export function getScheduleOr(id: string): StoredSchedule | null {
  return getMainDb().data.schedules.find((s) => s.id === id) ?? null;
}

export async function disableSchedule(id: string): Promise<void> {
  const db = getMainDb();
  const schedule = db.data.schedules.find((s) => s.id === id);
  if (schedule) {
    schedule.enabled = false;
    await db.write();
  }
}

export async function markFired(id: string): Promise<void> {
  const db = getMainDb();
  const schedule = db.data.schedules.find((s) => s.id === id);
  if (schedule) {
    schedule.lastFiredAt = new Date().toISOString();
    await db.write();
  }
}

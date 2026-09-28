import type { EventDTO } from '../../../shared/types.js';
import { getEventsDb } from '../db/eventsDb.js';
import type { StoredEvent } from '../db/schema.js';
import { newId } from '../utils/id.js';
import { broadcast } from '../ws/broadcast.js';

const MAX_EVENTS = 5000;

export interface LogEventInput {
  type: StoredEvent['type'];
  stripId: string | null;
  stripName: string | null;
  driverStripId: string | null; // MAC address for rename resilience
  relayIndex: number | null;
  message: string;
  cause: StoredEvent['cause'];
}

function toDTO(e: StoredEvent): EventDTO {
  return {
    id: e.id,
    type: e.type,
    stripId: e.stripId,
    stripName: e.stripName,
    driverStripId: e.driverStripId,
    relayIndex: e.relayIndex,
    message: e.message,
    cause: e.cause,
    createdAt: e.createdAt,
  };
}

export async function logEvent(input: LogEventInput): Promise<EventDTO> {
  const db = getEventsDb();
  const event: StoredEvent = { id: newId('evt'), createdAt: new Date().toISOString(), ...input };
  db.data.events.unshift(event);
  if (db.data.events.length > MAX_EVENTS) {
    db.data.events.length = MAX_EVENTS;
  }
  await db.write();
  const dto = toDTO(event);
  broadcast({ type: 'event-logged', event: dto });
  return dto;
}

export interface ListEventsQuery {
  stripId?: string;
  limit?: number;
  before?: string;
}

export function listEvents(query: ListEventsQuery): EventDTO[] {
  const db = getEventsDb();
  let events = db.data.events;
  if (query.stripId) events = events.filter((e) => e.stripId === query.stripId);
  if (query.before) events = events.filter((e) => e.createdAt < query.before!);
  const limit = Math.min(query.limit ?? 100, 500);
  return events.slice(0, limit).map(toDTO);
}

export async function pruneOldEvents(maxAgeDays = 90): Promise<void> {
  const db = getEventsDb();
  const cutoff = new Date(Date.now() - maxAgeDays * 24 * 60 * 60 * 1000).toISOString();
  const before = db.data.events.length;
  db.data.events = db.data.events.filter((e) => e.createdAt >= cutoff);
  if (db.data.events.length !== before) await db.write();
}

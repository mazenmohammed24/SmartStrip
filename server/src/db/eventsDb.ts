import { JSONFilePreset } from 'lowdb/node';
import type { Low } from 'lowdb';
import { dataFilePath } from './paths.js';
import { defaultEventsDb, type EventsDbSchema } from './schema.js';

let db: Low<EventsDbSchema> | null = null;

export async function initEventsDb(): Promise<void> {
  db = await JSONFilePreset<EventsDbSchema>(dataFilePath('events.json'), defaultEventsDb);
}

export function getEventsDb(): Low<EventsDbSchema> {
  if (!db) throw new Error('eventsDb not initialized - call initEventsDb() at startup');
  return db;
}

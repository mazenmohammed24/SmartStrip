import { JSONFilePreset } from 'lowdb/node';
import type { Low } from 'lowdb';
import { dataFilePath } from './paths.js';
import { defaultPowerDb, type PowerDbSchema } from './schema.js';

let db: Low<PowerDbSchema> | null = null;

export async function initPowerDb(): Promise<void> {
  db = await JSONFilePreset<PowerDbSchema>(dataFilePath('power.json'), defaultPowerDb);
}

export function getPowerDb(): Low<PowerDbSchema> {
  if (!db) throw new Error('powerDb not initialized - call initPowerDb() at startup');
  return db;
}

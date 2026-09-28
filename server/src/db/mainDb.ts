import { JSONFilePreset } from 'lowdb/node';
import type { Low } from 'lowdb';
import { dataFilePath } from './paths.js';
import { defaultMainDb, type MainDbSchema } from './schema.js';

let db: Low<MainDbSchema> | null = null;

export async function initMainDb(): Promise<void> {
  db = await JSONFilePreset<MainDbSchema>(dataFilePath('main.json'), defaultMainDb);
  // Backfill any keys missing from an older data file with defaults.
  let dirty = false;
  for (const key of Object.keys(defaultMainDb) as Array<keyof MainDbSchema>) {
    if (db.data[key] === undefined) {
      // @ts-expect-error - assigning matching default for a possibly-missing key
      db.data[key] = defaultMainDb[key];
      dirty = true;
    }
  }
  if (dirty) await db.write();
}

export function getMainDb(): Low<MainDbSchema> {
  if (!db) throw new Error('mainDb not initialized - call initMainDb() at startup');
  return db;
}

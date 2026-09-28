import type { SettingsDTO } from '../../../shared/types.js';
import { getMainDb } from '../db/mainDb.js';

export function getSettings(): SettingsDTO {
  const settings = getMainDb().data.settings;
  return { ...settings, accentColor: settings.accentColor ?? '#0f766e' };
}

export async function updateSettings(patch: Partial<SettingsDTO>): Promise<SettingsDTO> {
  const db = getMainDb();
  db.data.settings = { ...db.data.settings, ...patch };
  await db.write();
  return { ...db.data.settings };
}

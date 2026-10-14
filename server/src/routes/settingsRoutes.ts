import { Router } from 'express';
import { z } from 'zod';
import { getSettings, updateSettings } from '../services/settingsService.js';

export const settingsRoutes = Router();

settingsRoutes.get('/', (_req, res) => {
  res.json({ settings: getSettings() });
});

const settingsPatchSchema = z.object({
  theme: z.enum(['light', 'dark', 'system']).optional(),
  accentColor: z.string().regex(/^#[0-9a-fA-F]{6}$/).optional(),
  energyUnit: z.enum(['kWh', 'Wh']).optional(),
  currency: z.string().min(1).max(8).optional(),
  ratePerKwh: z.number().min(0).max(10).optional(),
  overloadThresholdWatts: z.number().min(100).max(5000).optional(),
  timeFormat: z.enum(['12h', '24h']).optional(),
});

settingsRoutes.patch('/', async (req, res) => {
  const parsed = settingsPatchSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: 'Invalid settings', details: parsed.error.issues });
    return;
  }
  res.json({ settings: await updateSettings(parsed.data) });
});

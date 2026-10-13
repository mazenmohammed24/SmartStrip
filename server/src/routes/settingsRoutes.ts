import { Router } from 'express';
import { z } from 'zod';
import { getSettings, updateSettings } from '../services/settingsService.js';
import { voltraTokenManager } from '../drivers/voltra/tokenManager.js';

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

const voltraTokensSchema = z.object({
  accessToken: z.string().min(1),
  refreshToken: z.string().min(1),
});

settingsRoutes.post('/voltra-tokens', async (req, res) => {
  try {
    const parsed = voltraTokensSchema.safeParse(req.body);
    if (!parsed.success) {
      res.status(400).json({ error: 'Invalid tokens', details: parsed.error.issues });
      return;
    }

    await voltraTokenManager.saveTokens(parsed.data);
    res.json({ success: true, message: 'Voltra tokens saved' });
  } catch (error) {
    res.status(401).json({ error: 'Failed to save Voltra tokens', details: error instanceof Error ? error.message : String(error) });
  }
});

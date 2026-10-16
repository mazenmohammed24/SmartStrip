import { Router } from 'express';
import { z } from 'zod';
import { getDriver } from '../drivers/DriverRegistry.js';
import * as fleetService from '../services/fleetService.js';
import * as powerService from '../services/powerService.js';

export const powerRoutes = Router();

powerRoutes.get('/live', (_req, res) => {
  const strips = fleetService.listStrips();
  const live = strips.map((s) => ({
    stripId: s.id,
    totalWatts: s.totalWatts,
    relays: s.relays.map((r) => ({ relayIndex: r.index, watts: r.watts })),
  }));
  res.json({ live });
});

const rangeSchema = z.enum(['day', 'week', 'month', '7d', '30d', '90d', 'all']);

powerRoutes.get('/history', async (req, res) => {
  const stripId = String(req.query.stripId ?? '');
  const rangeParsed = rangeSchema.safeParse(req.query.range ?? 'day');
  if (!stripId || !rangeParsed.success) {
    res.status(400).json({ error: 'stripId and a valid range (day|week|month|7d|30d|90d|all) are required' });
    return;
  }
  const relayIndex = req.query.relayIndex !== undefined ? Number(req.query.relayIndex) : null;
  if (rangeParsed.data === '7d' || rangeParsed.data === '30d' || rangeParsed.data === '90d') {
    const strip = fleetService.getStrip(stripId);
    const driver = getDriver();
    if (strip && 'getEnergy' in driver && typeof driver.getEnergy === 'function') {
      const days = Number(rangeParsed.data.replace('d', ''));
      res.json(await powerService.getCloudEnergyHistory(strip, relayIndex, rangeParsed.data, days, driver.getEnergy.bind(driver)));
      return;
    }
  }
  res.json(powerService.getHistory(stripId, relayIndex, rangeParsed.data));
});

powerRoutes.get('/summary', (req, res) => {
  const stripId = String(req.query.stripId ?? '');
  if (!stripId) {
    res.status(400).json({ error: 'stripId is required' });
    return;
  }
  const { total } = fleetService.getLiveWattsForStrip(stripId);
  res.json(powerService.getSummary(stripId, total));
});

powerRoutes.get('/energy', (req, res) => {
  const stripId = String(req.query.stripId ?? '');
  const strip = stripId ? fleetService.getStrip(stripId) : null;
  if (!strip) {
    res.status(404).json({ error: 'Strip not found' });
    return;
  }
  const days = Math.min(365, Math.max(1, Math.round(Number(req.query.days ?? 7)) || 7));
  const names = new Map(strip.relays.map((r) => [r.index, r.name] as [number, string]));
  res.json(powerService.getEnergyReport(strip.id, days, names));
});

powerRoutes.delete('/energy', async (req, res) => {
  const stripId = String(req.query.stripId ?? '');
  if (!stripId || !fleetService.getStrip(stripId)) {
    res.status(404).json({ error: 'Strip not found' });
    return;
  }
  await powerService.resetEnergy(stripId);
  res.json({ ok: true });
});
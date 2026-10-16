import { DEVICE_ICON_IDS } from '../../../shared/deviceIcons.js';
import { Router } from 'express';
import { z } from 'zod';
import { getDriver } from '../drivers/DriverRegistry.js';
import * as fleetService from '../services/fleetService.js';
import * as relayTimerService from '../services/relayTimerService.js';
import { getScan, startScan } from '../services/scanService.js';

export const stripsRoutes = Router();

stripsRoutes.get('/', (_req, res) => {
  res.json({ strips: fleetService.listStrips() });
});

stripsRoutes.get('/timers/active', (_req, res) => {
  res.json({ timers: relayTimerService.listRelayTimers() });
});

stripsRoutes.get('/:id', (req, res) => {
  const strip = fleetService.getStrip(req.params.id);
  if (!strip) {
    res.status(404).json({ error: 'Strip not found' });
    return;
  }
  res.json({ strip });
});

stripsRoutes.post('/scan', (_req, res) => {
  const driver = getDriver();
  const scanId = startScan(driver);
  res.status(202).json({ scanId });
});

stripsRoutes.get('/scan/:scanId', (req, res) => {
  const record = getScan(req.params.scanId);
  if (!record) {
    res.status(404).json({ error: 'Scan not found' });
    return;
  }
  res.json(record);
});

const adoptSchema = z.object({
  driverStripId: z.string().min(1),
  model: z.string().min(1),
  relayCount: z.number().int().min(1).max(8),
  ipAddress: z.string().optional().nullable(),
  suggestedName: z.string().min(1),
  name: z.string().optional(),
  location: z.string().optional().nullable(),
});

stripsRoutes.post('/', async (req, res) => {
  const parsed = adoptSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: 'Invalid strip payload', details: parsed.error.issues });
    return;
  }
  try {
    const strip = await fleetService.adoptStrip(getDriver(), parsed.data);
    res.status(201).json({ strip });
  } catch (err) {
    res.status(409).json({ error: err instanceof Error ? err.message : 'Failed to adopt strip' });
  }
});

const updateStripSchema = z.object({
  name: z.string().min(1).max(64).optional(),
  location: z.string().max(64).nullable().optional(),
  order: z.number().int().min(0).optional(),
  icon: z.enum(DEVICE_ICON_IDS).optional(),
});

stripsRoutes.patch('/:id', async (req, res) => {
  const parsed = updateStripSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: 'Invalid payload' });
    return;
  }
  try {
    const strip = await fleetService.updateStrip(req.params.id, parsed.data);
    res.json({ strip });
  } catch (err) {
    res.status(404).json({ error: err instanceof Error ? err.message : 'Strip not found' });
  }
});

stripsRoutes.delete('/:id', async (req, res) => {
  try {
    await fleetService.removeStrip(getDriver(), req.params.id);
    res.json({ ok: true });
  } catch (err) {
    res.status(404).json({ error: err instanceof Error ? err.message : 'Strip not found' });
  }
});

stripsRoutes.post('/:id/relays/all-on', async (req, res) => {
  try {
    const strip = await fleetService.setAllRelays(getDriver(), req.params.id, 'on');
    res.json({ strip });
  } catch (err) {
    res.status(400).json({ error: err instanceof Error ? err.message : 'Failed to turn on all relays' });
  }
});

stripsRoutes.post('/:id/relays/all-off', async (req, res) => {
  try {
    const strip = await fleetService.setAllRelays(getDriver(), req.params.id, 'off');
    res.json({ strip });
  } catch (err) {
    res.status(400).json({ error: err instanceof Error ? err.message : 'Failed to turn off all relays' });
  }
});

const relayPatchSchema = z.object({
  state: z.enum(['on', 'off']).optional(),
  name: z.string().min(1).max(48).optional(),
  icon: z.enum(DEVICE_ICON_IDS).optional(),
});

const relayTimerSchema = z.object({
  state: z.enum(['on', 'off']),
  durationMinutes: z.number().int().min(1).max(24 * 60),
  mode: z.enum(['for', 'after']).default('for'),
});

stripsRoutes.patch('/:id/relays/:index', async (req, res) => {
  const relayIndex = Number(req.params.index);
  if (!Number.isInteger(relayIndex) || relayIndex < 0) {
    res.status(400).json({ error: 'Invalid relay index' });
    return;
  }
  const parsed = relayPatchSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: 'Invalid payload' });
    return;
  }
  try {
    let strip = fleetService.getStrip(req.params.id);
    if (!strip) {
      res.status(404).json({ error: 'Strip not found' });
      return;
    }
    if (parsed.data.name !== undefined) {
      strip = await fleetService.renameRelay(req.params.id, relayIndex, parsed.data.name);
    }
    if (parsed.data.icon !== undefined) {
      strip = await fleetService.updateRelayIcon(req.params.id, relayIndex, parsed.data.icon);
    }
    if (parsed.data.state !== undefined) {
      strip = await fleetService.setRelay(getDriver(), req.params.id, relayIndex, parsed.data.state);
    }
    res.json({ strip });
  } catch (err) {
    res.status(400).json({ error: err instanceof Error ? err.message : 'Failed to update relay' });
  }
});

stripsRoutes.post('/:id/relays/:index/timer', async (req, res) => {
  const relayIndex = Number(req.params.index);
  if (!Number.isInteger(relayIndex) || relayIndex < 0) {
    res.status(400).json({ error: 'Invalid relay index' });
    return;
  }
  const parsed = relayTimerSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: 'Invalid timer payload', details: parsed.error.issues });
    return;
  }
  try {
    const { strip, timer } = await relayTimerService.startRelayTimer(
      getDriver(),
      req.params.id,
      relayIndex,
      parsed.data.state,
      parsed.data.durationMinutes,
      parsed.data.mode,
    );
    res.json({ strip, timer, restoreState: timer.restoreState, restoreAt: timer.restoreAt, mode: timer.mode });
  } catch (err) {
    res.status(400).json({ error: err instanceof Error ? err.message : 'Failed to start relay timer' });
  }
});

stripsRoutes.delete('/:id/relays/:index/timer', async (req, res) => {
  const relayIndex = Number(req.params.index);
  if (!Number.isInteger(relayIndex) || relayIndex < 0) {
    res.status(400).json({ error: 'Invalid relay index' });
    return;
  }
  const removed = await relayTimerService.cancelRelayTimer(req.params.id, relayIndex);
  res.json({ ok: true, removed });
});

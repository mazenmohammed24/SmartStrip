import { Router } from 'express';
import { z } from 'zod';
import * as scheduleService from '../services/scheduleService.js';
import { fireSchedule } from '../scheduler/scheduleRunner.js';
import { getDriver } from '../drivers/DriverRegistry.js';

export const schedulesRoutes = Router();

schedulesRoutes.get('/', (_req, res) => {
  res.json({ schedules: scheduleService.listSchedules() });
});

const scheduleSchema = z.object({
  name: z.string().min(1).max(64),
  stripId: z.string().min(1),
  relayIndex: z.number().int().min(0).max(7).nullable(),
  action: z.enum(['on', 'off']),
  time: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, 'time must be HH:MM (24h)'),
  daysOfWeek: z.array(z.number().int().min(0).max(6)).min(1),
  enabled: z.boolean().optional(),
});

schedulesRoutes.post('/', async (req, res) => {
  const parsed = scheduleSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: 'Invalid schedule', details: parsed.error.issues });
    return;
  }
  const schedule = await scheduleService.createSchedule(parsed.data);
  res.status(201).json({ schedule });
});

schedulesRoutes.patch('/:id', async (req, res) => {
  const parsed = scheduleSchema.partial().safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: 'Invalid schedule', details: parsed.error.issues });
    return;
  }
  try {
    const schedule = await scheduleService.updateSchedule(req.params.id, parsed.data);
    res.json({ schedule });
  } catch (err) {
    res.status(404).json({ error: err instanceof Error ? err.message : 'Schedule not found' });
  }
});

schedulesRoutes.delete('/:id', async (req, res) => {
  await scheduleService.deleteSchedule(req.params.id);
  res.json({ ok: true });
});

schedulesRoutes.post('/:id/run-now', async (req, res) => {
  const schedule = scheduleService.getScheduleOr(req.params.id);
  if (!schedule) {
    res.status(404).json({ error: 'Schedule not found' });
    return;
  }
  await fireSchedule(getDriver(), schedule);
  res.json({ ok: true });
});

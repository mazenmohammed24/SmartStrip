import { Router } from 'express';
import { z } from 'zod';
import * as ruleService from '../services/ruleService.js';

export const rulesRoutes = Router();

rulesRoutes.get('/', (_req, res) => {
  res.json({ rules: ruleService.listRules() });
});

rulesRoutes.post('/', async (req, res) => {
  const parsed = ruleService.ruleInputSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: 'Invalid rule', details: parsed.error.issues });
    return;
  }
  res.status(201).json({ rule: await ruleService.createRule(parsed.data) });
});

rulesRoutes.put('/:id', async (req, res) => {
  const parsed = ruleService.ruleInputSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: 'Invalid rule', details: parsed.error.issues });
    return;
  }
  try {
    res.json({ rule: await ruleService.updateRule(req.params.id, parsed.data) });
  } catch (err) {
    res.status(404).json({ error: err instanceof Error ? err.message : 'Rule not found' });
  }
});

rulesRoutes.patch('/:id', async (req, res) => {
  const parsed = z.object({ enabled: z.boolean() }).safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: 'Only "enabled" can be patched' });
    return;
  }
  try {
    res.json({ rule: await ruleService.setRuleEnabled(req.params.id, parsed.data.enabled) });
  } catch (err) {
    res.status(404).json({ error: err instanceof Error ? err.message : 'Rule not found' });
  }
});

rulesRoutes.delete('/:id', async (req, res) => {
  await ruleService.deleteRule(req.params.id);
  res.json({ ok: true });
});

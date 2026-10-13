import { Router } from 'express';
import { z } from 'zod';
import { getPublicKey, isPushConfigured, sendPushToAll, subscribe, unsubscribe } from '../services/pushService.js';

export const pushRoutes = Router();

pushRoutes.get('/public-key', (_req, res) => {
  res.json({ publicKey: getPublicKey(), configured: isPushConfigured() });
});

const subscribeSchema = z.object({
  endpoint: z.string().url(),
  keys: z.object({ p256dh: z.string().min(1), auth: z.string().min(1) }),
});

pushRoutes.post('/subscribe', async (req, res) => {
  const parsed = subscribeSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: 'Invalid subscription' });
    return;
  }
  await subscribe(parsed.data, req.get('user-agent') ?? null);
  res.status(201).json({ ok: true });
});

pushRoutes.delete('/subscribe', async (req, res) => {
  const endpoint = String(req.body?.endpoint ?? req.query.endpoint ?? '');
  if (!endpoint) {
    res.status(400).json({ error: 'endpoint is required' });
    return;
  }
  await unsubscribe(endpoint);
  res.json({ ok: true });
});

pushRoutes.post('/test', async (_req, res) => {
  if (!isPushConfigured()) {
    res.status(400).json({ error: 'Web Push is not configured on the server (missing VAPID keys)' });
    return;
  }
  await sendPushToAll({ title: 'SmartStrip', body: 'Push notifications are working.' });
  res.json({ ok: true });
});

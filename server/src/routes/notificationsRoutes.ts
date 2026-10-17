import { Router } from 'express';
import { listNotifications, markAllRead, markRead, removeAllNotifications, removeNotification } from '../services/notificationService.js';

export const notificationsRoutes = Router();

notificationsRoutes.get('/', (req, res) => {
  const unreadOnly = req.query.unreadOnly === 'true';
  const limit = req.query.limit ? Number(req.query.limit) : undefined;
  res.json({ notifications: listNotifications({ unreadOnly, limit }) });
});

notificationsRoutes.patch('/:id/read', async (req, res) => {
  await markRead(req.params.id);
  res.json({ ok: true });
});

notificationsRoutes.post('/read-all', async (_req, res) => {
  await markAllRead();
  res.json({ ok: true });
});

notificationsRoutes.delete('/', async (_req, res) => {
  await removeAllNotifications();
  res.json({ ok: true });
});

notificationsRoutes.delete('/:id', async (req, res) => {
  await removeNotification(req.params.id);
  res.json({ ok: true });
});

import { Router } from 'express';
import { listEvents } from '../services/eventService.js';

export const eventsRoutes = Router();

eventsRoutes.get('/', (req, res) => {
  const stripId = req.query.stripId ? String(req.query.stripId) : undefined;
  const limit = req.query.limit ? Number(req.query.limit) : undefined;
  const before = req.query.before ? String(req.query.before) : undefined;
  const relayIndex = req.query.relayIndex !== undefined ? Number(req.query.relayIndex) : undefined;
  res.json({ events: listEvents({ stripId, relayIndex, limit, before }) });
});

import express, { Router } from 'express';
import {
  appendEntries,
  crashPath,
  listCrashes,
  listScreenshots,
  parseSource,
  queryLogs,
  sanitizeEntries,
  saveCrash,
  saveScreenshot,
  screenshotPath,
  summary,
} from '../services/diagnosticsService.js';

export const diagnosticsRoutes = Router();

function clientInfo(body: Record<string, unknown>) {
  return {
    source: parseSource(body.source),
    device: String(body.device ?? 'unknown').slice(0, 80),
    version: String(body.version ?? '').slice(0, 40),
  };
}

diagnosticsRoutes.post('/logs', express.json({ limit: '512kb' }), (req, res) => {
  const body = (req.body ?? {}) as Record<string, unknown>;
  const { source, device, version } = clientInfo(body);
  const entries = sanitizeEntries(body.entries, source, device, version);
  appendEntries(entries);
  res.json({ ok: true, stored: entries.length });
});

diagnosticsRoutes.post('/crash', express.json({ limit: '1mb' }), (req, res) => {
  const body = (req.body ?? {}) as Record<string, unknown>;
  const { source, device, version } = clientInfo(body);
  const name = saveCrash(source, device, { ...body, version });
  res.json({ ok: true, name });
});

diagnosticsRoutes.post('/screenshot', express.raw({ type: 'image/*', limit: '2mb' }), (req, res) => {
  if (!Buffer.isBuffer(req.body) || req.body.length < 100) {
    res.status(400).json({ error: 'image body required' });
    return;
  }
  const device = String(req.query.device ?? 'unknown');
  const screen = String(req.query.screen ?? 'screen');
  res.json({ ok: true, name: saveScreenshot(device, screen, req.body) });
});

diagnosticsRoutes.get('/', (_req, res) => {
  res.json(summary());
});

diagnosticsRoutes.get('/logs', (req, res) => {
  const q = req.query;
  res.json(
    queryLogs({
      source: typeof q.source === 'string' ? q.source : undefined,
      level: typeof q.level === 'string' ? q.level : undefined,
      since: typeof q.since === 'string' ? q.since : undefined,
      q: typeof q.q === 'string' ? q.q : undefined,
      device: typeof q.device === 'string' ? q.device : undefined,
      limit: q.limit ? Number(q.limit) : undefined,
    }),
  );
});

diagnosticsRoutes.get('/crashes', (_req, res) => {
  res.json(listCrashes());
});

diagnosticsRoutes.get('/crashes/:name', (req, res) => {
  const file = crashPath(req.params.name);
  if (!file) {
    res.status(404).json({ error: 'not found' });
    return;
  }
  res.sendFile(file);
});

diagnosticsRoutes.get('/screens', (_req, res) => {
  res.json(listScreenshots());
});

diagnosticsRoutes.get('/screens/:name', (req, res) => {
  const file = screenshotPath(req.params.name);
  if (!file) {
    res.status(404).json({ error: 'not found' });
    return;
  }
  res.sendFile(file);
});

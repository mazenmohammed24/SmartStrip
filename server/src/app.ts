import path from 'node:path';
import { fileURLToPath } from 'node:url';
import express, { type Express } from 'express';
import { env } from './config/env.js';
import { diagnosticsRoutes } from './routes/diagnosticsRoutes.js';
import { eventsRoutes } from './routes/eventsRoutes.js';
import { healthRoutes } from './routes/healthRoutes.js';
import { mttlRoutes } from './routes/mttlRoutes.js';
import { notificationsRoutes } from './routes/notificationsRoutes.js';
import { powerRoutes } from './routes/powerRoutes.js';
import { pushRoutes } from './routes/pushRoutes.js';
import { rulesRoutes } from './routes/rulesRoutes.js';
import { schedulesRoutes } from './routes/schedulesRoutes.js';
import { settingsRoutes } from './routes/settingsRoutes.js';
import { stripsRoutes } from './routes/stripsRoutes.js';
import { createLogger } from './utils/logger.js';

const httpLog = createLogger('http');
const thisDir = path.dirname(fileURLToPath(import.meta.url));

export function createApp(): Express {
  const app = express();
  // Mounted before the global JSON parser: it brings its own larger body limits.
  app.use('/api/diagnostics', diagnosticsRoutes);
  app.use(express.json());

  app.use('/api/strips', stripsRoutes);
  app.use('/api/power', powerRoutes);
  app.use('/api/events', eventsRoutes);
  app.use('/api/schedules', schedulesRoutes);
  app.use('/api/rules', rulesRoutes);
  app.use('/api/notifications', notificationsRoutes);
  app.use('/api/push', pushRoutes);
  app.use('/api/settings', settingsRoutes);
  app.use('/api/health', healthRoutes);
  app.use('/api/mttl', mttlRoutes);

  if (env.isProduction) {
    // server/dist/server/src/app.js -> ../../../../web/dist
    const webDist = path.join(thisDir, '..', '..', '..', '..', 'web', 'dist');
    app.use(express.static(webDist));

    // Requesting a missing *asset* must be a 404, not the app shell. Returning
    // HTML here makes the browser parse it as JavaScript and report the
    // confusing "Unexpected token '<'" instead of an honest 404 — which is what
    // a stale cached page referencing an old asset hash would hit.
    const ASSET_EXT = /\.(?:js|mjs|css|map|json|webmanifest|png|jpe?g|gif|svg|ico|woff2?|ttf|eot|wasm|mp4|webm|txt|xml)$/i;

    app.get(/^(?!\/api).*/, (req, res) => {
      if (ASSET_EXT.test(req.path)) {
        res.status(404).type('text/plain').send(`Not found: ${req.path}`);
        return;
      }
      res.sendFile(path.join(webDist, 'index.html'));
    });
  }

  app.use((err: Error & { status?: number; statusCode?: number }, req: express.Request, res: express.Response, _next: express.NextFunction) => {
    const status = err.status ?? err.statusCode ?? 500;
    if (status >= 500) httpLog.error(`${req.method} ${req.path}: ${err.stack ?? err.message}`);
    if (res.headersSent) return;
    res.status(status).json({ error: status >= 500 ? 'internal error' : err.message });
  });

  return app;
}

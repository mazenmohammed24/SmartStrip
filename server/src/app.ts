import path from 'node:path';
import { fileURLToPath } from 'node:url';
import express, { type Express } from 'express';
import { env } from './config/env.js';
import { eventsRoutes } from './routes/eventsRoutes.js';
import { healthRoutes } from './routes/healthRoutes.js';
import { mttlRoutes } from './routes/mttlRoutes.js';
import { notificationsRoutes } from './routes/notificationsRoutes.js';
import { powerRoutes } from './routes/powerRoutes.js';
import { pushRoutes } from './routes/pushRoutes.js';
import { schedulesRoutes } from './routes/schedulesRoutes.js';
import { settingsRoutes } from './routes/settingsRoutes.js';
import { stripsRoutes } from './routes/stripsRoutes.js';

const thisDir = path.dirname(fileURLToPath(import.meta.url));

export function createApp(): Express {
  const app = express();
  app.use(express.json());

  app.use('/api/strips', stripsRoutes);
  app.use('/api/power', powerRoutes);
  app.use('/api/events', eventsRoutes);
  app.use('/api/schedules', schedulesRoutes);
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

  return app;
}

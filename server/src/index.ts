import { createServer } from 'node:http';
import { createApp } from './app.js';
import { env } from './config/env.js';
import { initEventsDb } from './db/eventsDb.js';
import { initMainDb } from './db/mainDb.js';
import { initPowerDb } from './db/powerDb.js';
import { initDriver } from './drivers/DriverRegistry.js';
import { voltraTokenManager } from './drivers/voltra/tokenManager.js';
import { startTicker } from './scheduler/ticker.js';
import { resyncDriverState, wireDriverEvents } from './services/fleetService.js';
import { configurePush } from './services/pushService.js';
import { createLogger } from './utils/logger.js';
import { attachWebSocketServer } from './ws/wsServer.js';

const log = createLogger('bootstrap');

async function main(): Promise<void> {
  await Promise.all([initMainDb(), initEventsDb(), initPowerDb()]);
  log.info('data stores ready');

  // Initialize Voltra token manager (loads stored credentials if available)
  await voltraTokenManager.init();

  configurePush();

  const driver = await initDriver();
  wireDriverEvents(driver);
  await resyncDriverState(driver);
  log.info(`strip driver "${driver.name}" ready`);

  const ticker = startTicker(driver);

  const app = createApp();
  const server = createServer(app);
  attachWebSocketServer(server);

  server.listen(env.port, () => {
    log.info(`SmartStrip server listening on http://localhost:${env.port}`);
  });

  const shutdown = async (signal: string) => {
    log.info(`received ${signal}, shutting down`);
    ticker.stop();
    voltraTokenManager.shutdown();
    await driver.shutdown();
    server.close(() => process.exit(0));
    setTimeout(() => process.exit(0), 3000).unref();
  };
  process.on('SIGINT', () => void shutdown('SIGINT'));
  process.on('SIGTERM', () => void shutdown('SIGTERM'));
}

main().catch((err) => {
  log.error('fatal error during startup', err);
  process.exit(1);
});

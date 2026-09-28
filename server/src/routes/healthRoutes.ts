import { Router } from 'express';
import { getDriver } from '../drivers/DriverRegistry.js';

export const healthRoutes = Router();

healthRoutes.get('/', (_req, res) => {
  let driver: unknown = null;
  try {
    const active = getDriver() as { name?: string; health?: () => unknown };
    driver = typeof active.health === 'function' ? active.health() : { name: active.name };
  } catch {
    // Driver not initialised yet; the base health payload is still useful.
  }
  res.json({ status: 'ok', time: new Date().toISOString(), driver });
});

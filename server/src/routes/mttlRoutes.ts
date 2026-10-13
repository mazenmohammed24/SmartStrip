import { Router } from 'express';
import { z } from 'zod';
import type { MttlStatusResponse, MttlStripTelemetryResponse } from '../../../shared/types.js';
import { getMainDb } from '../db/mainDb.js';
import { env } from '../config/env.js';
import { getDriver, getAdvertisedIp } from '../drivers/DriverRegistry.js';
import { MttlDriver } from '../drivers/mttl/MttlDriver.js';
import { MTTL_SETUP_PORT } from '../drivers/mttl/protocol.js';
import { assertIpv4, provisionStrip } from '../drivers/mttl/provision.js';
import { localIPv4s } from '../utils/localIp.js';

/**
 * Diagnostics and provisioning for the direct MTTL-W01 driver.
 *
 * These routes only exist meaningfully when DRIVER=mttl; with another driver
 * selected they report 409 instead of pretending to work. Provisioning writes
 * the strip's Wi-Fi credentials and its destination server address, so these
 * are the most sensitive routes on the server.
 */
export const mttlRoutes = Router();

function requireMttlDriver(): MttlDriver | null {
  const driver = getDriver();
  return driver instanceof MttlDriver ? driver : null;
}

/** Live per-strip telemetry plus the addresses needed to provision a new strip. */
mttlRoutes.get('/status', (_req, res) => {
  const driver = requireMttlDriver();
  if (!driver) {
    res.status(409).json({ error: 'The MTTL driver is not active. Set DRIVER=mttl to use these endpoints.' });
    return;
  }
  const ips = localIPv4s();
  const response: MttlStatusResponse = {
    // The port actually bound, not the one that was requested.
    devicePort: driver.listeningPort(),
    setupHost: env.mttlSetupHost,
    setupPort: MTTL_SETUP_PORT,
    // One resolution, in one place, so the form's default can never disagree with
    // what the server would actually tell a strip to dial.
    advertisedIp: getAdvertisedIp(),
    localIps: ips,
    strips: driver.listKnownDevices(),
  };
  res.json(response);
});

/** Telemetry for one strip, keyed by driver id (the strip's MAC). */
mttlRoutes.get('/strips/:driverStripId', (req, res) => {
  const driver = requireMttlDriver();
  if (!driver) {
    res.status(409).json({ error: 'The MTTL driver is not active. Set DRIVER=mttl to use these endpoints.' });
    return;
  }
  const strip = findFleetStrip(req.params.driverStripId);
  const response: MttlStripTelemetryResponse = {
    stripId: strip?.id ?? null,
    driverStripId: req.params.driverStripId,
    telemetry: driver.getTelemetry(req.params.driverStripId),
  };
  if (!strip && !response.telemetry) {
    res.status(404).json({ error: 'No strip with that identifier has connected to this server yet.' });
    return;
  }
  res.json(response);
});

const provisionSchema = z.object({
  // Validated with the same rule provisionStrip uses, so a bad address is a 400
  // here rather than an assertion failure surfacing as a 502 later.
  serverIp: z.string().refine(
    (value) => {
      try {
        assertIpv4(value);
        return true;
      } catch {
        return false;
      }
    },
    { message: 'Server IP must be a dotted-quad IPv4 address, e.g. 192.168.1.10' },
  ),
  ssid: z.string().min(1).max(32),
  password: z.string().min(1).max(64),
  host: z.string().min(1).max(64).optional(),
  waitMs: z.number().int().min(0).max(120_000).optional(),
});

/**
 * Writes the home Wi-Fi credentials and this server's IP into a strip that is in
 * SoftAP setup mode. The requesting machine must be joined to the strip's
 * "TONLY_TAP_*" network for this to work.
 */
mttlRoutes.post('/provision', async (req, res) => {
  const driver = requireMttlDriver();
  if (!driver) {
    res.status(409).json({ error: 'The MTTL driver is not active. Set DRIVER=mttl to provision a strip.' });
    return;
  }
  const parsed = provisionSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: 'Invalid provisioning payload', details: parsed.error.issues });
    return;
  }

  const { serverIp, ssid, password, host, waitMs } = parsed.data;
  try {
    const result = await provisionStrip({
      serverIp,
      ssid,
      password,
      host: host ?? env.mttlSetupHost,
      waitMs,
    });
    res.json({
      ok: true,
      ...result,
      message: `Strip will join "${ssid}" and dial ${serverIp}:${driver.listeningPort()}. It re-appears here within a minute.`,
    });
  } catch (err) {
    res.status(502).json({ error: err instanceof Error ? err.message : 'Provisioning failed' });
  }
});

function findFleetStrip(driverStripId: string) {
  const wanted = driverStripId.trim().toUpperCase();
  const db = getMainDb();
  const strip = db.data.strips.find((s) => s.driverStripId.toUpperCase() === wanted);
  return strip ? { id: strip.id, name: strip.name } : null;
}

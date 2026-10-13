import { env } from '../config/env.js';
import { localIPv4s } from '../utils/localIp.js';
import { MockDriver } from './mock/MockDriver.js';
import { MttlDriver } from './mttl/MttlDriver.js';
import type { StripDriver } from './StripDriver.js';
import { VoltraDriver } from './voltra/VoltraDriver.js';

let activeDriver: StripDriver | null = null;

/**
 * The one place a strip driver implementation is chosen. To add real hardware
 * support later, implement `StripDriver` (see StripDriver.ts) and add a case
 * here - no route or service code needs to change.
 */
export async function initDriver(): Promise<StripDriver> {
  switch (env.driver) {
    case 'voltra':
      if (!env.voltraAccessToken) {
        throw new Error('VOLTRA_ACCESS_TOKEN is required when DRIVER=voltra');
      }
      activeDriver = new VoltraDriver({
        accessToken: env.voltraAccessToken,
        baseUrl: env.voltraBaseUrl,
      });
      break;
    case 'mttl':
      // No credentials: the strip dials this server on TCP 10086 and is
      // provisioned once through its own setup access point.
      activeDriver = new MttlDriver({
        port: env.mttlDevicePort,
        pollIntervalMs: env.mttlPollIntervalMs,
      });
      break;
    case 'mock':
    default:
      activeDriver = new MockDriver();
      break;
  }
  await activeDriver.init();
  return activeDriver;
}

export function getDriver(): StripDriver {
  if (!activeDriver) throw new Error('Driver not initialized - call initDriver() at startup');
  return activeDriver;
}

/**
 * The IPv4 a strip should be provisioned with: an explicit override when the server
 * is hosted where auto-detection would guess wrong, otherwise the first LAN address.
 * Callers must not re-derive this; the fallback belongs in one place.
 */
export function getAdvertisedIp(): string {
  return env.mttlAdvertiseIp || localIPv4s()[0] || '';
}

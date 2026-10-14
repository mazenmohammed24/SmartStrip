import 'dotenv/config';

function intEnv(name: string, fallback: number, min: number, max: number): number {
  const raw = process.env[name];
  if (raw === undefined || raw.trim() === '') return fallback;
  const value = Number(raw);
  if (!Number.isInteger(value) || value < min || value > max) {
    // Silently accepting a bad value is worse than falling back: port 0 would bind a
    // random port, and a 0/NaN poll interval collapses to ~1ms, turning a typo into a
    // continuous stream of device traffic, database writes and broadcasts.
    console.warn(`[env] ${name}=${raw} is not an integer between ${min} and ${max}; using ${fallback}`);
    return fallback;
  }
  return value;
}

export const env = {
  port: Number(process.env.PORT ?? 4001),
  driver: process.env.DRIVER ?? 'mock',
  vapidPublicKey: process.env.VAPID_PUBLIC_KEY ?? '',
  vapidPrivateKey: process.env.VAPID_PRIVATE_KEY ?? '',
  vapidSubject: process.env.VAPID_SUBJECT ?? 'mailto:admin@example.com',
  isProduction: process.env.NODE_ENV === 'production',

  // MTTL-W01 direct driver. The device port is fixed by the strip's firmware
  // (it dials 10086), so it is deliberately not configurable in practice.
  mttlDevicePort: intEnv('MTTL_DEVICE_PORT', 10086, 1, 65535),
  mttlPollIntervalMs: intEnv('MTTL_POLL_INTERVAL_MS', 5000, 1000, 600_000),
  /** IPv4 printed in hints and used as the default when provisioning this host. */
  mttlAdvertiseIp: process.env.MTTL_ADVERTISE_IP ?? '',
  mttlSetupHost: process.env.MTTL_SETUP_HOST ?? '192.168.1.1',
};

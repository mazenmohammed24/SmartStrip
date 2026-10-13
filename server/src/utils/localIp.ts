import os from 'node:os';

/**
 * Every non-internal IPv4 on this host, best effort. Used only to suggest an
 * address when provisioning; it never decides anything on its own.
 */
export function localIPv4s(): string[] {
  const found = new Set<string>();
  for (const addresses of Object.values(os.networkInterfaces())) {
    for (const address of addresses ?? []) {
      if (address.family !== 'IPv4' || address.internal) continue;
      if (address.address.startsWith('169.254.')) continue;
      found.add(address.address);
    }
  }
  return [...found];
}

import os from 'node:os';

// Virtual/VM/tunnel adapters that commonly sort before the real LAN adapter in
// os.networkInterfaces() (Hyper-V mirrors the Wi-Fi/Ethernet names, so these are
// unreachable from a strip's actual home network even though they look "private").
const VIRTUAL_ADAPTER_PATTERN = /vEthernet|Virtual|Loopback|Hyper-V|VMware|VirtualBox|Docker|WSL|Tailscale|ZeroTier|utun|Bluetooth/i;

/**
 * Every non-internal IPv4 on this host, best effort, with adapters that are
 * obviously virtual sorted last so the auto-picked "first" address is far more
 * likely to be the real LAN adapter. Used only to suggest an address when
 * provisioning; it never decides anything on its own.
 */
export function localIPv4s(): string[] {
  const real: string[] = [];
  const virtual: string[] = [];
  for (const [name, addresses] of Object.entries(os.networkInterfaces())) {
    for (const address of addresses ?? []) {
      if (address.family !== 'IPv4' || address.internal) continue;
      if (address.address.startsWith('169.254.')) continue;
      (VIRTUAL_ADAPTER_PATTERN.test(name) ? virtual : real).push(address.address);
    }
  }
  return [...new Set([...real, ...virtual])];
}

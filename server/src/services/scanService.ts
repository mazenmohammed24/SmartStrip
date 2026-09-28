import { newId } from '../utils/id.js';
import { broadcast } from '../ws/broadcast.js';
import type { StripDriver } from '../drivers/StripDriver.js';
import { scanForStrips } from './fleetService.js';
import type { DiscoveredStripDTO } from '../../../shared/types.js';

interface ScanRecord {
  status: 'pending' | 'done' | 'error';
  percent: number;
  found: number;
  discovered: DiscoveredStripDTO[];
  error?: string;
}

const scans = new Map<string, ScanRecord>();

export function startScan(driver: StripDriver): string {
  const scanId = newId('scan');
  scans.set(scanId, { status: 'pending', percent: 0, found: 0, discovered: [] });
  void runScan(driver, scanId);
  return scanId;
}

async function runScan(driver: StripDriver, scanId: string): Promise<void> {
  const record = scans.get(scanId);
  if (!record) return;
  try {
    const discovered = await scanForStrips(driver, {
      onProgress: (p) => {
        record.percent = p.percent;
        record.found = p.found;
        broadcast({ type: 'scan-progress', scanId, percent: p.percent, found: p.found });
      },
    });
    record.status = 'done';
    record.discovered = discovered;
    broadcast({ type: 'scan-result', scanId, discovered });
  } catch (err) {
    record.status = 'error';
    record.error = err instanceof Error ? err.message : 'scan failed';
  }
}

export function getScan(scanId: string): ScanRecord | null {
  return scans.get(scanId) ?? null;
}

import type { ApplianceProfileName } from '../../../../shared/types.js';

export interface ApplianceProfile {
  name: ApplianceProfileName;
  /** Watts drawn while the relay is on, before jitter/cycling is applied. */
  baseWatts: number;
  /** Watts leaked while the relay is off (standby draw), if any. */
  standbyWatts: number;
  /** Fractional jitter applied per tick, e.g. 0.05 = +/-5%. */
  jitter: number;
  /** Compressor-style periodic spikes (fridge). Undefined = no cycling. */
  cyclePeakWatts?: number;
  cyclePeriodTicks?: number;
}

export const APPLIANCE_PROFILES: Record<'lamp' | 'charger' | 'fridge' | 'heater' | 'idle', ApplianceProfile> = {
  lamp: { name: 'lamp', baseWatts: 40, standbyWatts: 0.2, jitter: 0.05 },
  charger: { name: 'charger', baseWatts: 15, standbyWatts: 0.3, jitter: 0.1 },
  fridge: {
    name: 'fridge',
    baseWatts: 150,
    standbyWatts: 0,
    jitter: 0.05,
    cyclePeakWatts: 600,
    cyclePeriodTicks: 24,
  },
  heater: { name: 'heater', baseWatts: 1500, standbyWatts: 0, jitter: 0.03 },
  idle: { name: 'idle', baseWatts: 0, standbyWatts: 0.3, jitter: 0 },
};

export function computeWatts(profileName: ApplianceProfileName, isOn: boolean, tick: number): number {
  const profile = APPLIANCE_PROFILES[profileName as keyof typeof APPLIANCE_PROFILES] ?? APPLIANCE_PROFILES.idle;
  if (!isOn) {
    return round2(profile.standbyWatts);
  }
  let watts = profile.baseWatts;
  if (profile.cyclePeakWatts && profile.cyclePeriodTicks) {
    const phase = tick % profile.cyclePeriodTicks;
    const inCompressorPhase = phase < profile.cyclePeriodTicks / 3;
    if (inCompressorPhase) watts = profile.cyclePeakWatts;
  }
  const jitterFactor = 1 + (Math.random() * 2 - 1) * profile.jitter;
  return round2(Math.max(0, watts * jitterFactor));
}

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

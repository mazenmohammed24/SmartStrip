import type { ApplianceProfileName, ConnectivityState, RelayStateValue } from '../../../../shared/types.js';

export interface MockRelayState {
  relayIndex: number;
  state: RelayStateValue;
  profile: ApplianceProfileName;
  currentWatts: number;
}

export interface MockStripState {
  driverStripId: string;
  model: string;
  suggestedName: string;
  ipAddress: string;
  signalStrength: number;
  connectivity: ConnectivityState;
  adopted: boolean;
  offlineTicksRemaining: number;
  relays: MockRelayState[];
}

const NAME_POOL = [
  'Kitchen Strip',
  'Living Room Strip',
  'Home Office Strip',
  'Bedroom Strip',
  'Garage Strip',
  'Entertainment Center Strip',
];

const PROFILE_SETS: ApplianceProfileName[][] = [
  ['lamp', 'charger', 'fridge', 'idle'],
  ['heater', 'lamp', 'charger', 'idle'],
  ['charger', 'charger', 'lamp', 'idle'],
  ['fridge', 'lamp', 'idle', 'idle'],
];

function randomMac(seedIndex: number): string {
  // Deterministic per index (no Math.random()) so a discovered/adopted strip's
  // driverStripId is stable across server restarts - otherwise every restart would
  // regenerate the pool with fresh ids and orphan anything already in the fleet DB.
  const bytes = [0x02, 0x1a, 0x7f, seedIndex, 0xa0 + seedIndex, 0xb0 + seedIndex];
  return bytes.map((b) => b.toString(16).padStart(2, '0')).join(':');
}

export function createMockStripPool(): Map<string, MockStripState> {
  const pool = new Map<string, MockStripState>();
  NAME_POOL.forEach((suggestedName, i) => {
    const driverStripId = randomMac(i);
    const profiles = PROFILE_SETS[i % PROFILE_SETS.length];
    const relays: MockRelayState[] = profiles.map((profile, relayIndex) => ({
      relayIndex,
      state: 'off',
      profile,
      currentWatts: 0,
    }));
    pool.set(driverStripId, {
      driverStripId,
      model: 'MTTL-W01',
      suggestedName,
      ipAddress: `192.168.1.${20 + i}`,
      signalStrength: 60 + Math.floor(Math.random() * 35),
      connectivity: 'online',
      adopted: false,
      offlineTicksRemaining: 0,
      relays,
    });
  });
  return pool;
}

import type {
  ApplianceProfileName,
  ConnectivityState,
  EnergyUnit,
  TariffMode,
  EventCause,
  NotificationSeverity,
  RelayStateValue,
  RuleType,
  ThemePreference,
  TimeFormat,
} from '../../../shared/types.js';

// Storage-level shapes for lowdb. These are intentionally separate from the
// shared DTOs: they hold internal-only fields (password hashes, driver ids)
// that must never be sent to the client as-is.

export interface StoredStrip {
  id: string;
  name: string;
  model: string;
  location: string | null;
  driverName: string;
  driverStripId: string;
  ipAddress: string | null;
  connectivity: ConnectivityState;
  order: number;
  addedAt: string;
  icon?: ApplianceProfileName;
}

export interface StoredRelay {
  id: string;
  stripId: string;
  index: number;
  name: string;
  state: RelayStateValue;
  icon: ApplianceProfileName;
  lastChangedAt: string;
}

export interface StoredSchedule {
  id: string;
  name: string;
  stripId: string;
  relayIndex: number | null;
  action: RelayStateValue;
  time: string;
  daysOfWeek: number[];
  timezone?: string;
  repeat?: 'weekly' | 'once';
  enabled: boolean;
  lastFiredAt: string | null;
  createdAt: string;
}

export interface StoredRule {
  id: string;
  type: RuleType;
  stripId: string;
  relayIndex: number | null;
  name: string;
  enabled: boolean;
  params: Record<string, unknown>;
  lastTriggeredAt: string | null;
  createdAt: string;
}

export interface StoredRelayTimer {
  stripId: string;
  relayIndex: number;
  mode: 'for' | 'after';
  state: RelayStateValue;
  restoreState: RelayStateValue | null;
  restoreAt: string;
  createdAt: string;
}

export interface StoredNotification {
  id: string;
  type: 'relay-changed' | 'schedule-fired' | 'overload' | 'offline' | 'online' | 'system';
  title: string;
  body: string;
  severity: NotificationSeverity;
  relatedStripId: string | null;
  relatedStripMac: string | null; // MAC address for rename resilience
  relatedRelayIndex: number | null;
  createdAt: string;
  readAt: string | null;
}

export interface StoredPushSubscription {
  id: string;
  endpoint: string;
  keys: { p256dh: string; auth: string };
  userAgent: string | null;
  createdAt: string;
}

export interface StoredSettings {
  theme: ThemePreference;
  accentColor: string;
  energyUnit: EnergyUnit;
  currency: string;
  ratePerKwh: number;
  tariffMode?: TariffMode;
  overloadThresholdWatts: number;
  timeFormat: TimeFormat;
}

export interface MainDbSchema {
  strips: StoredStrip[];
  relays: StoredRelay[];
  schedules: StoredSchedule[];
  rules: StoredRule[];
  relayTimers: StoredRelayTimer[];
  notifications: StoredNotification[];
  pushSubscriptions: StoredPushSubscription[];
  settings: StoredSettings;
}

export const defaultSettings: StoredSettings = {
  theme: 'system',
  accentColor: '#0f766e',
  energyUnit: 'kWh',
  currency: 'EGP',
  ratePerKwh: 0.16,
  tariffMode: 'egypt',
  overloadThresholdWatts: 1800,
  timeFormat: '24h',
};

export const defaultMainDb: MainDbSchema = {
  strips: [],
  relays: [],
  schedules: [],
  rules: [],
  relayTimers: [],
  notifications: [],
  pushSubscriptions: [],
  settings: defaultSettings,
};

export interface StoredEvent {
  id: string;
  type:
  | 'relay-on'
  | 'relay-off'
  | 'schedule-fired'
  | 'overload'
  | 'offline'
  | 'online'
  | 'strip-added'
  | 'strip-removed';
  stripId: string | null;
  stripName: string | null;
  driverStripId: string | null; // MAC address / serial - stable identifier for rename resilience
  relayIndex: number | null;
  message: string;
  cause: EventCause;
  createdAt: string;
}

export interface EventsDbSchema {
  events: StoredEvent[];
}

export const defaultEventsDb: EventsDbSchema = { events: [] };

export type PowerResolution = 'minute' | 'hour' | 'day';

export interface StoredPowerSample {
  id: string;
  stripId: string;
  relayIndex: number;
  resolution: PowerResolution;
  bucketStart: string;
  avgW: number;
  minW: number;
  maxW: number;
  kwh: number;
}

export interface PowerDbSchema {
  samples: StoredPowerSample[];
}

export const defaultPowerDb: PowerDbSchema = { samples: [] };

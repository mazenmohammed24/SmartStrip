// Shared type-only definitions used by both server and web.
// Imported everywhere with `import type` so this file has zero runtime footprint.

export type RelayStateValue = 'on' | 'off';
export type ConnectivityState = 'online' | 'offline' | 'unreachable';
export type ApplianceProfileName =
  | 'power-strip' | 'lamp' | 'charger' | 'fridge' | 'heater' | 'idle'
  | 'computer' | 'laptop' | 'monitor' | 'printer' | 'tv' | 'fan'
  | 'air-conditioner' | 'coffee' | 'kettle' | 'microwave' | 'washer'
  | 'router' | 'speaker' | 'gamepad' | 'phone' | 'plug';
export type EventCause = 'user' | 'schedule' | 'driver' | 'system';
export type NotificationSeverity = 'info' | 'warning' | 'critical';
export type EnergyUnit = 'kWh' | 'Wh';
export type TimeFormat = '12h' | '24h';
export type ThemePreference = 'light' | 'dark' | 'system';
export type PowerRange = 'day' | 'week' | 'month' | '7d' | '30d' | '90d' | 'all';

export interface RelayDTO {
  id: string;
  stripId: string;
  index: number; // 0-3
  name: string;
  state: RelayStateValue;
  watts: number;
  icon: ApplianceProfileName;
  lastChangedAt: string;
}

export interface StripDTO {
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
  relays: RelayDTO[];
  totalWatts: number;
  icon: ApplianceProfileName;
}

export interface DiscoveredStripDTO {
  driverStripId: string;
  model: string;
  suggestedName: string;
  ipAddress?: string;
  relayCount: number;
  signalStrength?: number;
}

export interface EventDTO {
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
  driverStripId: string | null; // MAC address / serial - used to resolve current strip name at render time
  relayIndex: number | null;
  message: string;
  cause: EventCause;
  createdAt: string;
}

export interface ScheduleDTO {
  id: string;
  name: string;
  stripId: string;
  relayIndex: number | null; // null = whole strip
  action: RelayStateValue;
  time: string; // "HH:MM" 24h
  daysOfWeek: number[]; // 0=Sun .. 6=Sat
  enabled: boolean;
  lastFiredAt: string | null;
  createdAt: string;
}

export type RelayTimerMode = 'for' | 'after';

export interface RelayTimerDTO {
  stripId: string;
  relayIndex: number;
  mode: RelayTimerMode;
  state: RelayStateValue;
  restoreState: RelayStateValue | null;
  restoreAt: string;
}

export interface NotificationDTO {
  id: string;
  type: 'relay-changed' | 'schedule-fired' | 'overload' | 'offline' | 'online' | 'system';
  title: string;
  body: string;
  severity: NotificationSeverity;
  relatedStripId: string | null;
  relatedStripMac: string | null; // MAC address - used to resolve current strip name at render time
  relatedRelayIndex: number | null;
  createdAt: string;
  readAt: string | null;
}

export interface SettingsDTO {
  theme: ThemePreference;
  accentColor: string;
  energyUnit: EnergyUnit;
  currency: string;
  ratePerKwh: number;
  overloadThresholdWatts: number;
  timeFormat: TimeFormat;
}

export interface PowerPointDTO {
  bucketStart: string;
  avgW: number;
  minW: number;
  maxW: number;
  kwh: number;
}

export interface PowerHistoryResponse {
  stripId: string;
  relayIndex: number | null;
  range: PowerRange;
  points: PowerPointDTO[];
}

export interface PowerSummaryResponse {
  stripId: string;
  todayKwh: number;
  todayCost: number;
  currency: string;
  liveWatts: number;
}

export interface UserPublicDTO {
  id: string;
  username: string;
  createdAt: string;
  lastLoginAt: string | null;
}

// ---- WebSocket message shapes (server -> client) ----

export type ServerWsMessage =
  | { type: 'relay-state'; stripId: string; relayIndex: number; state: RelayStateValue; changedAt: string }
  | { type: 'relay-timer'; stripId: string; relayIndex: number; mode: RelayTimerMode; state: RelayStateValue; restoreState: RelayStateValue | null; restoreAt: string | null }
  | { type: 'strip-connectivity'; stripId: string; connectivity: ConnectivityState }
  | {
    type: 'power-tick';
    stripId: string;
    relays: Array<{ relayIndex: number; watts: number }>;
    totalWatts: number;
    timestamp: string;
  }
  | { type: 'scan-progress'; scanId: string; percent: number; found: number }
  | { type: 'scan-result'; scanId: string; discovered: DiscoveredStripDTO[] }
  | { type: 'strip-added'; strip: StripDTO }
  | { type: 'strip-updated'; strip: StripDTO }
  | { type: 'strip-removed'; stripId: string }
  | { type: 'event-logged'; event: EventDTO }
  | { type: 'notification'; notification: NotificationDTO }
  | { type: 'schedule-fired'; scheduleId: string; stripId: string };

import type { ComponentType } from 'react';
import type { LucideProps } from 'lucide-react';
import { Activity, Bell, Bot, Gauge, Home, Radar, Settings, TimerReset } from 'lucide-react';

export interface NavItem {
  to: string;
  label: string;
  Icon: ComponentType<LucideProps>;
}

export const PRIMARY_NAV: NavItem[] = [
  { to: '/', label: 'Dashboard', Icon: Gauge },
  { to: '/power', label: 'Power', Icon: Activity },
  { to: '/automations', label: 'Automations', Icon: TimerReset },
  { to: '/notifications', label: 'Notifications', Icon: Bell },
];

export const SECONDARY_NAV: NavItem[] = [
  { to: '/rooms', label: 'Rooms', Icon: Home },
  { to: '/activity', label: 'Activity', Icon: Bot },
  { to: '/scan', label: 'Scan network', Icon: Radar },
  { to: '/settings', label: 'Settings', Icon: Settings },
];

export const ALL_NAV: NavItem[] = [...PRIMARY_NAV, ...SECONDARY_NAV];

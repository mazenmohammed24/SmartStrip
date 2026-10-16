import type { ComponentType } from 'react';
import type { LucideProps } from 'lucide-react';
import { Activity, Clock, Gauge, Hourglass, Zap } from 'lucide-react';
import type { RuleAction, RuleDTO, RuleType, StripDTO } from '@shared/types';
import { DAY_NAMES } from '../../lib/formatters';

export interface RuleKind {
  type: RuleType;
  title: string;
  blurb: string;
  addLabel: string;
  Icon: ComponentType<LucideProps>;
  needsOutlet: boolean;
}

export const RULE_KINDS: RuleKind[] = [
  { type: 'smart-timer', title: 'Smart timer', blurb: 'Paint the hours an outlet stays on', addLabel: 'Add smart timer', Icon: Clock, needsOutlet: true },
  { type: 'auto-timer', title: 'Auto-timers', blurb: 'Return an outlet home after a while', addLabel: 'Add auto-timer', Icon: Hourglass, needsOutlet: true },
  { type: 'power-automation', title: 'Power automations', blurb: 'React to an outlet drawing too little or too much', addLabel: 'Add power automation', Icon: Activity, needsOutlet: true },
  { type: 'voltage-protection', title: 'Voltage protection', blurb: 'Cut outlets when mains voltage leaves a safe range', addLabel: 'Add voltage protection', Icon: Zap, needsOutlet: false },
  { type: 'current-protection', title: 'Current protection', blurb: 'Trip on over-current for an outlet or the whole strip', addLabel: 'Add current protection', Icon: Gauge, needsOutlet: false },
];

export const KIND_BY_TYPE = Object.fromEntries(RULE_KINDS.map((k) => [k.type, k])) as Record<RuleType, RuleKind>;

export const ACTION_LABEL: Record<RuleAction, string> = { notify: 'Notify only', off: 'Switch off', both: 'Notify and switch off' };

export interface SmartTimerParams { slots: string; days: number[] }
export interface AutoTimerParams { homeState: 'on' | 'off'; returnAfterMin: number; repeat: boolean }
export interface PowerAutomationParams { direction: 'below' | 'above'; thresholdW: number; holdMin: number; action: RuleAction }
export interface VoltageParams { lowEnabled: boolean; lowV: number; highEnabled: boolean; highV: number; holdSec: number; action: RuleAction; restoreAfterMin: number; outlets: number[] }
export interface CurrentParams { scope: 'outlet' | 'strip'; limitA: number; holdSec: number; action: RuleAction }

export const ALL_DAYS = [0, 1, 2, 3, 4, 5, 6];

export function defaultParams(type: RuleType, strip: StripDTO): Record<string, unknown> {
  switch (type) {
    case 'smart-timer':
      return { slots: '0'.repeat(96), days: [...ALL_DAYS] } satisfies SmartTimerParams;
    case 'auto-timer':
      return { homeState: 'off', returnAfterMin: 30, repeat: true } satisfies AutoTimerParams;
    case 'power-automation':
      return { direction: 'below', thresholdW: 5, holdMin: 5, action: 'both' } satisfies PowerAutomationParams;
    case 'voltage-protection':
      return { lowEnabled: true, lowV: 190, highEnabled: true, highV: 250, holdSec: 10, action: 'both', restoreAfterMin: 5, outlets: strip.relays.map((r) => r.index) } satisfies VoltageParams;
    case 'current-protection':
      return { scope: 'strip', limitA: 10, holdSec: 5, action: 'both' } satisfies CurrentParams;
  }
}

export function formatDuration(totalMin: number): string {
  const d = Math.floor(totalMin / 1440);
  const h = Math.floor((totalMin % 1440) / 60);
  const m = totalMin % 60;
  return [d && `${d}d`, h && `${h}h`, m && `${m}m`].filter(Boolean).join(' ') || '0m';
}

export function formatDays(days: number[]): string {
  if (days.length === 7) return 'Every day';
  if (days.length === 0) return 'Never';
  const weekdays = [1, 2, 3, 4, 5];
  if (days.length === 5 && weekdays.every((d) => days.includes(d))) return 'Weekdays';
  return [...days].sort().map((d) => DAY_NAMES[d]).join(', ');
}

export function slotRanges(slots: string): Array<[number, number]> {
  const out: Array<[number, number]> = [];
  let start = -1;
  for (let i = 0; i <= 96; i++) {
    const on = i < 96 && slots[i] === '1';
    if (on && start < 0) start = i;
    if (!on && start >= 0) {
      out.push([start, i]);
      start = -1;
    }
  }
  return out;
}

export function slotTime(slot: number): string {
  const h = Math.floor(slot / 4) % 24;
  const m = (slot % 4) * 15;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

export function outletName(strip: StripDTO | undefined, index: number | null): string {
  if (index === null) return 'Whole strip';
  return strip?.relays.find((r) => r.index === index)?.name ?? `Outlet ${index + 1}`;
}

export function summarize(rule: RuleDTO, strip: StripDTO | undefined): string {
  const target = outletName(strip, rule.relayIndex);
  switch (rule.type) {
    case 'smart-timer': {
      const p = rule.params as unknown as SmartTimerParams;
      const ranges = slotRanges(p.slots);
      const text = ranges.length === 0 ? 'No hours set' : ranges.slice(0, 2).map(([a, b]) => `${slotTime(a)}–${slotTime(b)}`).join(', ') + (ranges.length > 2 ? ` +${ranges.length - 2}` : '');
      return `${target} · ${text} · ${formatDays(p.days)}`;
    }
    case 'auto-timer': {
      const p = rule.params as unknown as AutoTimerParams;
      return `${target} · back ${p.homeState.toUpperCase()} after ${formatDuration(p.returnAfterMin)}${p.repeat ? '' : ' · once'}`;
    }
    case 'power-automation': {
      const p = rule.params as unknown as PowerAutomationParams;
      return `${target} · ${p.direction} ${p.thresholdW} W for ${p.holdMin} min · ${ACTION_LABEL[p.action]}`;
    }
    case 'voltage-protection': {
      const p = rule.params as unknown as VoltageParams;
      const range = [p.lowEnabled && `< ${p.lowV} V`, p.highEnabled && `> ${p.highV} V`].filter(Boolean).join(' or ') || 'no limits';
      return `${range} for ${p.holdSec}s · ${ACTION_LABEL[p.action]}`;
    }
    case 'current-protection': {
      const p = rule.params as unknown as CurrentParams;
      return `${target} · over ${p.limitA} A for ${p.holdSec}s · ${ACTION_LABEL[p.action]}`;
    }
  }
}

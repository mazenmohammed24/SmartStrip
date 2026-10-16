import type { RuleAction } from '../../../shared/types.js';
import type { StripDriver } from '../drivers/StripDriver.js';
import { getMainDb } from '../db/mainDb.js';
import type { StoredRule, StoredStrip } from '../db/schema.js';
import { logEvent } from '../services/eventService.js';
import { getLiveTelemetry, getLiveWattsForStrip, setRelay } from '../services/fleetService.js';
import { notify } from '../services/notificationService.js';
import * as ruleService from '../services/ruleService.js';
import { createLogger } from '../utils/logger.js';

const log = createLogger('ruleRunner');

const NOMINAL_VOLTAGE = 230;

interface Runtime {
  slot?: number;
  since?: number;
  latched?: boolean;
  awaySince?: number;
  restore?: number[];
  okSince?: number;
}

const runtime = new Map<string, Runtime>();

function stripRelays(stripId: string) {
  return getMainDb().data.relays.filter((r) => r.stripId === stripId);
}

function relayName(stripId: string, index: number | null): string {
  if (index === null) return 'all outlets';
  return stripRelays(stripId).find((r) => r.index === index)?.name ?? `Outlet ${index + 1}`;
}

// Debounce: true once the condition has held for holdMs, then stays false until it clears.
function held(rt: Runtime, cond: boolean, holdMs: number, now: number): boolean {
  if (!cond) {
    rt.since = undefined;
    rt.latched = false;
    return false;
  }
  rt.since ??= now;
  if (rt.latched) return false;
  if (now - rt.since < holdMs) return false;
  rt.latched = true;
  return true;
}

async function trigger(driver: StripDriver, rule: StoredRule, strip: StoredStrip, message: string, act: RuleAction, offIndexes: number[]): Promise<void> {
  if (act !== 'notify') {
    for (const i of offIndexes) await setRelay(driver, strip.id, i, 'off', 'system');
  }
  if (act !== 'off') {
    await notify({
      type: 'system',
      title: rule.name || 'Automation triggered',
      body: `${strip.name}: ${message}`,
      severity: 'warning',
      relatedStripId: strip.id,
      relatedStripMac: strip.driverStripId,
      relatedRelayIndex: rule.relayIndex,
    });
  }
  await logEvent({
    type: 'overload',
    stripId: strip.id,
    stripName: strip.name,
    driverStripId: strip.driverStripId,
    relayIndex: rule.relayIndex,
    message: `${rule.name || rule.type}: ${message}`,
    cause: 'system',
  });
  await ruleService.markTriggered(rule.id);
}

async function smartTimer(driver: StripDriver, rule: StoredRule, strip: StoredStrip, rt: Runtime, now: Date): Promise<ruleService.Status> {
  const { slots, days } = rule.params as { slots: string; days: number[] };
  const slot = now.getHours() * 4 + Math.floor(now.getMinutes() / 15);
  const first = rt.slot === undefined;
  if (rt.slot !== slot && days.includes(now.getDay()) && rule.relayIndex !== null) {
    const desired = slots[slot] === '1' ? 'on' : 'off';
    const changed = slots[slot] !== slots[(slot + 95) % 96];
    if (first || changed) {
      const relay = stripRelays(strip.id).find((r) => r.index === rule.relayIndex);
      if (relay && relay.state !== desired) {
        await setRelay(driver, strip.id, rule.relayIndex, desired, 'schedule');
        await ruleService.markTriggered(rule.id);
      }
    }
  }
  rt.slot = slot;
  return slots[slot] === '1' ? 'active' : 'idle';
}

async function autoTimer(driver: StripDriver, rule: StoredRule, strip: StoredStrip, rt: Runtime, now: number): Promise<ruleService.Status> {
  const { homeState, returnAfterMin, repeat } = rule.params as { homeState: 'on' | 'off'; returnAfterMin: number; repeat: boolean };
  const relay = stripRelays(strip.id).find((r) => r.index === rule.relayIndex);
  if (!relay || relay.state === homeState) {
    rt.awaySince = undefined;
    return 'idle';
  }
  rt.awaySince ??= now;
  if (now - rt.awaySince < returnAfterMin * 60_000) return 'waiting';
  rt.awaySince = undefined;
  await setRelay(driver, strip.id, relay.index, homeState, 'system');
  await ruleService.markTriggered(rule.id);
  await notify({
    type: 'system',
    title: rule.name || 'Auto-timer',
    body: `${strip.name}: ${relay.name} returned to ${homeState}`,
    severity: 'info',
    relatedStripId: strip.id,
    relatedStripMac: strip.driverStripId,
    relatedRelayIndex: relay.index,
  });
  if (!repeat) await ruleService.setRuleEnabled(rule.id, false);
  return 'idle';
}

async function powerAutomation(driver: StripDriver, rule: StoredRule, strip: StoredStrip, rt: Runtime, now: number): Promise<ruleService.Status> {
  const p = rule.params as { direction: 'below' | 'above'; thresholdW: number; holdMin: number; action: RuleAction };
  const live = getLiveWattsForStrip(strip.id);
  const relays = stripRelays(strip.id).filter((r) => rule.relayIndex === null || r.index === rule.relayIndex);
  const isOn = relays.some((r) => r.state === 'on');
  const watts = rule.relayIndex === null ? live.total : live.relays.get(rule.relayIndex) ?? 0;
  const cond = isOn && (p.direction === 'below' ? watts < p.thresholdW : watts > p.thresholdW);
  if (held(rt, cond, p.holdMin * 60_000, now)) {
    const word = p.direction === 'below' ? 'below' : 'above';
    await trigger(driver, rule, strip, `${relayName(strip.id, rule.relayIndex)} drew ${Math.round(watts)} W, ${word} ${p.thresholdW} W for ${p.holdMin} min`, p.action, relays.filter((r) => r.state === 'on').map((r) => r.index));
  }
  return rt.latched ? 'tripped' : rt.since ? 'waiting' : 'idle';
}

async function voltageProtection(driver: StripDriver, rule: StoredRule, strip: StoredStrip, rt: Runtime, now: number): Promise<ruleService.Status> {
  const p = rule.params as { lowEnabled: boolean; lowV: number; highEnabled: boolean; highV: number; holdSec: number; action: RuleAction; restoreAfterMin: number; outlets: number[] };
  const v = getLiveTelemetry(strip.id)?.voltageV;
  if (typeof v !== 'number') return 'idle';
  const out = (p.lowEnabled && v < p.lowV) || (p.highEnabled && v > p.highV);

  if (rt.latched) {
    if (out) {
      rt.okSince = undefined;
      return 'tripped';
    }
    rt.okSince ??= now;
    const restore = rt.restore ?? [];
    if (restore.length === 0 || p.restoreAfterMin === 0) {
      rt.latched = false;
      rt.restore = undefined;
      rt.okSince = undefined;
      rt.since = undefined;
    } else if (now - rt.okSince >= p.restoreAfterMin * 60_000) {
      for (const i of restore) await setRelay(driver, strip.id, i, 'on', 'system');
      await notify({ type: 'system', title: rule.name || 'Voltage protection', body: `${strip.name}: voltage back to normal (${v} V), outlets restored`, severity: 'info', relatedStripId: strip.id, relatedStripMac: strip.driverStripId });
      rt.latched = false;
      rt.restore = undefined;
      rt.okSince = undefined;
      rt.since = undefined;
    }
    return rt.latched ? 'tripped' : 'idle';
  }

  const was = rt.latched;
  if (held(rt, out, p.holdSec * 1000, now) && !was) {
    const scope = p.outlets.length ? p.outlets : stripRelays(strip.id).map((r) => r.index);
    const live = stripRelays(strip.id).filter((r) => scope.includes(r.index) && r.state === 'on').map((r) => r.index);
    rt.restore = p.action === 'notify' ? [] : live;
    await trigger(driver, rule, strip, `voltage ${v} V is outside ${p.lowEnabled ? p.lowV : '–'}–${p.highEnabled ? p.highV : '–'} V`, p.action, live);
  }
  return rt.latched ? 'tripped' : rt.since ? 'waiting' : 'idle';
}

async function currentProtection(driver: StripDriver, rule: StoredRule, strip: StoredStrip, rt: Runtime, now: number): Promise<ruleService.Status> {
  const p = rule.params as { scope: 'outlet' | 'strip'; limitA: number; holdSec: number; action: RuleAction };
  const tel = getLiveTelemetry(strip.id);
  const volts = typeof tel?.voltageV === 'number' && tel.voltageV > 50 ? tel.voltageV : NOMINAL_VOLTAGE;
  const live = getLiveWattsForStrip(strip.id);
  const outletScope = p.scope === 'outlet' && rule.relayIndex !== null;
  const amps = outletScope ? (live.relays.get(rule.relayIndex as number) ?? 0) / volts : typeof tel?.currentA === 'number' ? tel.currentA : live.total / volts;
  if (held(rt, amps > p.limitA, p.holdSec * 1000, now)) {
    const targets = outletScope ? [rule.relayIndex as number] : stripRelays(strip.id).filter((r) => r.state === 'on').map((r) => r.index);
    await trigger(driver, rule, strip, `${outletScope ? relayName(strip.id, rule.relayIndex) : 'strip'} drew ${amps.toFixed(2)} A, over the ${p.limitA} A limit`, p.action, targets);
  }
  return rt.latched ? 'tripped' : rt.since ? 'waiting' : 'idle';
}

export async function runRules(driver: StripDriver): Promise<void> {
  const db = getMainDb();
  const now = new Date();
  const ms = now.getTime();
  let changed = false;

  for (const rule of [...db.data.rules]) {
    if (!rule.enabled) {
      runtime.delete(rule.id);
      continue;
    }
    const strip = db.data.strips.find((s) => s.id === rule.stripId);
    if (!strip || strip.connectivity !== 'online') continue;
    const rt = runtime.get(rule.id) ?? {};
    runtime.set(rule.id, rt);
    try {
      let status: ruleService.Status = 'idle';
      switch (rule.type) {
        case 'smart-timer': status = await smartTimer(driver, rule, strip, rt, now); break;
        case 'auto-timer': status = await autoTimer(driver, rule, strip, rt, ms); break;
        case 'power-automation': status = await powerAutomation(driver, rule, strip, rt, ms); break;
        case 'voltage-protection': status = await voltageProtection(driver, rule, strip, rt, ms); break;
        case 'current-protection': status = await currentProtection(driver, rule, strip, rt, ms); break;
      }
      if (ruleService.setRuleStatus(rule.id, status)) changed = true;
    } catch (err) {
      log.error(`rule ${rule.id} (${rule.type}) failed`, err);
    }
  }
  if (changed) ruleService.notifyRulesChanged();
}

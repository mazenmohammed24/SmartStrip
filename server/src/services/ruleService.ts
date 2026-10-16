import { z } from 'zod';
import type { RuleDTO } from '../../../shared/types.js';
import { getMainDb } from '../db/mainDb.js';
import type { StoredRule } from '../db/schema.js';
import { newId } from '../utils/id.js';
import { broadcast } from '../ws/broadcast.js';

const action = z.enum(['notify', 'off', 'both']);
const positive = z.number().finite().positive();

export const paramSchemas = {
  'smart-timer': z.object({
    slots: z.string().regex(/^[01]{96}$/, 'slots must be 96 chars of 0/1'),
    days: z.array(z.number().int().min(0).max(6)).min(1),
  }),
  'auto-timer': z.object({
    homeState: z.enum(['on', 'off']),
    returnAfterMin: z.number().int().min(1).max(60 * 24 * 7),
    repeat: z.boolean(),
  }),
  'power-automation': z.object({
    direction: z.enum(['below', 'above']),
    thresholdW: z.number().finite().min(0),
    holdMin: z.number().min(0).max(1440),
    action,
  }),
  'voltage-protection': z.object({
    lowEnabled: z.boolean(),
    lowV: z.number().min(50).max(300),
    highEnabled: z.boolean(),
    highV: z.number().min(100).max(400),
    holdSec: z.number().int().min(0).max(3600),
    action,
    restoreAfterMin: z.number().int().min(0).max(1440),
    outlets: z.array(z.number().int().min(0).max(7)),
  }),
  'current-protection': z.object({
    scope: z.enum(['outlet', 'strip']),
    limitA: positive.max(100),
    holdSec: z.number().int().min(0).max(3600),
    action,
  }),
} as const;

export const ruleInputSchema = z
  .object({
    type: z.enum(['smart-timer', 'auto-timer', 'power-automation', 'voltage-protection', 'current-protection']),
    stripId: z.string().min(1),
    relayIndex: z.number().int().min(0).max(7).nullable(),
    name: z.string().max(64).default(''),
    enabled: z.boolean().default(true),
    params: z.record(z.string(), z.unknown()),
  })
  .superRefine((v, ctx) => {
    const parsed = paramSchemas[v.type].safeParse(v.params);
    if (!parsed.success) parsed.error.issues.forEach((i) => ctx.addIssue({ ...i, path: ['params', ...i.path] }));
    if (v.type === 'auto-timer' && v.relayIndex === null) ctx.addIssue({ code: 'custom', message: 'auto-timer needs an outlet', path: ['relayIndex'] });
    if (v.type === 'current-protection' && v.params.scope === 'outlet' && v.relayIndex === null) ctx.addIssue({ code: 'custom', message: 'outlet scope needs an outlet', path: ['relayIndex'] });
    if (v.type === 'smart-timer' && v.relayIndex === null) ctx.addIssue({ code: 'custom', message: 'smart-timer needs an outlet', path: ['relayIndex'] });
  });

export type RuleInput = z.infer<typeof ruleInputSchema>;

export type Status = RuleDTO['status'];

const statusOf = new Map<string, Status>();

export function setRuleStatus(id: string, status: Status): boolean {
  if (statusOf.get(id) === status) return false;
  statusOf.set(id, status);
  return true;
}

export function notifyRulesChanged(): void {
  broadcast({ type: 'rules-changed' });
}

function toDTO(r: StoredRule): RuleDTO {
  return { ...r, status: r.enabled ? statusOf.get(r.id) ?? 'idle' : 'idle' };
}

export function listRules(): RuleDTO[] {
  return getMainDb().data.rules.map(toDTO);
}

export async function createRule(input: RuleInput): Promise<RuleDTO> {
  const db = getMainDb();
  const rule: StoredRule = { id: newId('rule'), ...input, lastTriggeredAt: null, createdAt: new Date().toISOString() };
  db.data.rules.push(rule);
  await db.write();
  notifyRulesChanged();
  return toDTO(rule);
}

export async function updateRule(id: string, input: RuleInput): Promise<RuleDTO> {
  const db = getMainDb();
  const rule = db.data.rules.find((r) => r.id === id);
  if (!rule) throw new Error('Rule not found');
  Object.assign(rule, input);
  statusOf.delete(id);
  await db.write();
  notifyRulesChanged();
  return toDTO(rule);
}

export async function setRuleEnabled(id: string, enabled: boolean): Promise<RuleDTO> {
  const db = getMainDb();
  const rule = db.data.rules.find((r) => r.id === id);
  if (!rule) throw new Error('Rule not found');
  rule.enabled = enabled;
  statusOf.delete(id);
  await db.write();
  notifyRulesChanged();
  return toDTO(rule);
}

export async function deleteRule(id: string): Promise<void> {
  const db = getMainDb();
  db.data.rules = db.data.rules.filter((r) => r.id !== id);
  statusOf.delete(id);
  await db.write();
  notifyRulesChanged();
}

export async function deleteRulesForStrip(stripId: string): Promise<void> {
  const db = getMainDb();
  const before = db.data.rules.length;
  db.data.rules = db.data.rules.filter((r) => r.stripId !== stripId);
  if (db.data.rules.length !== before) {
    await db.write();
    notifyRulesChanged();
  }
}

export async function markTriggered(id: string): Promise<void> {
  const db = getMainDb();
  const rule = db.data.rules.find((r) => r.id === id);
  if (!rule) return;
  rule.lastTriggeredAt = new Date().toISOString();
  await db.write();
  notifyRulesChanged();
}

import { useState } from 'react';
import type { ReactNode } from 'react';
import type { RuleAction, RuleDTO, StripDTO } from '@shared/types';
import { Button } from '../ui/Button';
import { Input, Label, Select } from '../ui/Input';
import { Modal } from '../ui/Modal';
import { Toggle } from '../ui/Toggle';
import { DAY_NAMES } from '../../lib/formatters';
import { useRulesStore } from '../../store/rulesStore';
import type { RuleInput } from '../../store/rulesStore';
import type { AutoTimerParams, CurrentParams, PowerAutomationParams, SmartTimerParams, VoltageParams } from './ruleMeta';
import { ACTION_LABEL, KIND_BY_TYPE, defaultParams } from './ruleMeta';
import { PRESETS, SmartTimerRing, presetSlots } from './SmartTimerRing';
import type { RuleType } from '@shared/types';

interface Props {
  type: RuleType;
  strip: StripDTO;
  rule: RuleDTO | null;
  open: boolean;
  onClose: () => void;
}

function Chip({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={`rounded-full border px-3 py-1.5 text-xs font-medium transition-colors ${active ? 'border-primary bg-primary text-primary-fg' : 'border-border bg-surface text-text-muted hover:bg-surface-hover'}`}
    >
      {children}
    </button>
  );
}

function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <Label>{label}</Label>
      {children}
      {hint && <p className="text-xs text-text-muted">{hint}</p>}
    </div>
  );
}

function Num({ value, onChange, min, max, step, unit }: { value: number; onChange: (v: number) => void; min?: number; max?: number; step?: number; unit?: string }) {
  return (
    <div className="flex items-center gap-2">
      <Input
        type="number"
        inputMode="decimal"
        className="font-mono"
        value={Number.isFinite(value) ? value : ''}
        min={min}
        max={max}
        step={step}
        onChange={(e) => onChange(e.target.value === '' ? NaN : Number(e.target.value))}
      />
      {unit && <span className="w-8 shrink-0 text-sm text-text-muted">{unit}</span>}
    </div>
  );
}

function Days({ value, onChange }: { value: number[]; onChange: (v: number[]) => void }) {
  return (
    <div className="flex flex-wrap gap-1.5">
      {DAY_NAMES.map((name, d) => (
        <Chip key={d} active={value.includes(d)} onClick={() => onChange(value.includes(d) ? value.filter((x) => x !== d) : [...value, d].sort())}>
          {name}
        </Chip>
      ))}
    </div>
  );
}

function ActionSelect({ value, onChange }: { value: RuleAction; onChange: (v: RuleAction) => void }) {
  return (
    <Select value={value} onChange={(e) => onChange(e.target.value as RuleAction)}>
      {(Object.keys(ACTION_LABEL) as RuleAction[]).map((a) => (
        <option key={a} value={a}>
          {ACTION_LABEL[a]}
        </option>
      ))}
    </Select>
  );
}

function OutletSelect({ strip, value, onChange }: { strip: StripDTO; value: number | null; onChange: (v: number) => void }) {
  return (
    <Select value={value ?? ''} onChange={(e) => onChange(Number(e.target.value))}>
      {value === null && <option value="">Choose outlet…</option>}
      {strip.relays.map((r) => (
        <option key={r.index} value={r.index}>
          {r.name}
        </option>
      ))}
    </Select>
  );
}

function validate(type: RuleType, input: RuleInput): string | null {
  const p = input.params;
  switch (type) {
    case 'smart-timer': {
      const t = p as unknown as SmartTimerParams;
      if (input.relayIndex === null) return 'Choose an outlet.';
      if (!t.slots.includes('1')) return 'Paint at least one time block.';
      if (t.days.length === 0) return 'Pick at least one day.';
      return null;
    }
    case 'auto-timer': {
      const t = p as unknown as AutoTimerParams;
      if (input.relayIndex === null) return 'Choose an outlet.';
      if (!(t.returnAfterMin >= 1)) return 'Return time must be at least 1 minute.';
      return null;
    }
    case 'power-automation': {
      const t = p as unknown as PowerAutomationParams;
      if (input.relayIndex === null) return 'Choose an outlet.';
      if (!(t.thresholdW >= 0) || !(t.holdMin >= 0)) return 'Enter valid numbers.';
      return null;
    }
    case 'voltage-protection': {
      const t = p as unknown as VoltageParams;
      if (!t.lowEnabled && !t.highEnabled) return 'Enable at least one limit.';
      if (t.lowEnabled && t.highEnabled && t.lowV >= t.highV) return 'Lower limit must be below the upper limit.';
      if (![t.lowV, t.highV, t.holdSec, t.restoreAfterMin].every(Number.isFinite)) return 'Enter valid numbers.';
      if (t.outlets.length === 0) return 'Pick at least one outlet.';
      return null;
    }
    case 'current-protection': {
      const t = p as unknown as CurrentParams;
      if (t.scope === 'outlet' && input.relayIndex === null) return 'Choose an outlet.';
      if (!(t.limitA > 0) || !(t.holdSec >= 0)) return 'Enter valid numbers.';
      return null;
    }
  }
}

export function RuleEditor({ type, strip, rule, open, onClose }: Props) {
  const kind = KIND_BY_TYPE[type];
  const saveRule = useRulesStore((s) => s.saveRule);
  const [name, setName] = useState(rule?.name ?? '');
  const [enabled, setEnabled] = useState(rule?.enabled ?? true);
  const [relayIndex, setRelayIndex] = useState<number | null>(rule ? rule.relayIndex : kind.needsOutlet ? (strip.relays[0]?.index ?? null) : null);
  const [params, setParams] = useState<Record<string, unknown>>(() => ({ ...defaultParams(type, strip), ...(rule?.params ?? {}) }));
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const set = (patch: Record<string, unknown>) => setParams((p) => ({ ...p, ...patch }));

  const submit = async () => {
    const outletScoped = type === 'current-protection' && params.scope === 'outlet';
    const input: RuleInput = {
      type,
      stripId: strip.id,
      relayIndex: kind.needsOutlet || outletScoped ? relayIndex : null,
      name: name.trim(),
      enabled,
      params,
    };
    const problem = validate(type, input);
    if (problem) return setError(problem);
    setSaving(true);
    try {
      await saveRule(input, rule?.id);
      onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={rule ? `Edit ${kind.title.toLowerCase()}` : kind.addLabel}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={() => void submit()} loading={saving}>
            Save
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <Field label="Label (optional)">
          <Input value={name} maxLength={64} onChange={(e) => setName(e.target.value)} placeholder={kind.title} />
        </Field>

        {kind.needsOutlet && (
          <Field label="Outlet">
            <OutletSelect strip={strip} value={relayIndex} onChange={setRelayIndex} />
          </Field>
        )}

        {type === 'smart-timer' && <SmartTimerFields params={params as unknown as SmartTimerParams} set={set} />}
        {type === 'auto-timer' && <AutoTimerFields params={params as unknown as AutoTimerParams} set={set} />}
        {type === 'power-automation' && <PowerFields params={params as unknown as PowerAutomationParams} set={set} />}
        {type === 'voltage-protection' && <VoltageFields strip={strip} params={params as unknown as VoltageParams} set={set} />}
        {type === 'current-protection' && (
          <CurrentFields strip={strip} params={params as unknown as CurrentParams} set={set} relayIndex={relayIndex} setRelayIndex={setRelayIndex} />
        )}

        <div className="flex items-center justify-between rounded-xl bg-surface-alt px-3 py-2.5">
          <span className="text-sm font-medium text-text">Enabled</span>
          <Toggle checked={enabled} onChange={setEnabled} label="Enabled" />
        </div>

        {error && (
          <p role="alert" className="rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger">
            {error}
          </p>
        )}
      </div>
    </Modal>
  );
}

type SetFn = (patch: Record<string, unknown>) => void;

function SmartTimerFields({ params, set }: { params: SmartTimerParams; set: SetFn }) {
  return (
    <>
      <div>
        <SmartTimerRing value={params.slots} onChange={(slots) => set({ slots })} />
        <p className="mt-1 text-center text-xs text-text-muted">Drag across the ring to paint or erase 15-minute blocks.</p>
      </div>
      <div className="flex flex-wrap gap-1.5">
        {PRESETS.map((p) => (
          <Chip key={p.label} active={false} onClick={() => set({ slots: presetSlots(p) })}>
            {p.label}
          </Chip>
        ))}
        <Chip active={false} onClick={() => set({ slots: '0'.repeat(96) })}>
          Clear all
        </Chip>
      </div>
      <Field label="Repeat on">
        <Days value={params.days} onChange={(days) => set({ days })} />
      </Field>
    </>
  );
}

function AutoTimerFields({ params, set }: { params: AutoTimerParams; set: SetFn }) {
  const d = Math.floor(params.returnAfterMin / 1440);
  const h = Math.floor((params.returnAfterMin % 1440) / 60);
  const m = params.returnAfterMin % 60;
  const update = (nd: number, nh: number, nm: number) => set({ returnAfterMin: (nd || 0) * 1440 + (nh || 0) * 60 + (nm || 0) });
  return (
    <>
      <Field label="Home state" hint="The state this outlet rests in.">
        <div className="flex gap-1.5">
          <Chip active={params.homeState === 'on'} onClick={() => set({ homeState: 'on' })}>
            ON
          </Chip>
          <Chip active={params.homeState === 'off'} onClick={() => set({ homeState: 'off' })}>
            OFF
          </Chip>
        </div>
      </Field>
      <Field label="Return home after">
        <div className="grid grid-cols-3 gap-2">
          <Num value={d} min={0} max={7} unit="d" onChange={(v) => update(v, h, m)} />
          <Num value={h} min={0} max={23} unit="h" onChange={(v) => update(d, v, m)} />
          <Num value={m} min={0} max={59} unit="m" onChange={(v) => update(d, h, v)} />
        </div>
      </Field>
      <div className="flex items-center justify-between rounded-xl bg-surface-alt px-3 py-2.5">
        <div>
          <p className="text-sm font-medium text-text">Repeat</p>
          <p className="text-xs text-text-muted">{params.repeat ? 'Applies every time the outlet leaves home.' : 'Runs once, then disables itself.'}</p>
        </div>
        <Toggle checked={params.repeat} onChange={(repeat) => set({ repeat })} label="Repeat" />
      </div>
    </>
  );
}

function PowerFields({ params, set }: { params: PowerAutomationParams; set: SetFn }) {
  return (
    <>
      <Field label="When power draw is">
        <div className="flex gap-1.5">
          <Chip active={params.direction === 'below'} onClick={() => set({ direction: 'below' })}>
            Below
          </Chip>
          <Chip active={params.direction === 'above'} onClick={() => set({ direction: 'above' })}>
            Above
          </Chip>
        </div>
      </Field>
      <Field label="Threshold">
        <Num value={params.thresholdW} min={0} step={1} unit="W" onChange={(v) => set({ thresholdW: v })} />
      </Field>
      <Field label="Held for">
        <Num value={params.holdMin} min={0} max={1440} unit="min" onChange={(v) => set({ holdMin: v })} />
      </Field>
      <Field label="Action">
        <ActionSelect value={params.action} onChange={(action) => set({ action })} />
      </Field>
    </>
  );
}

function VoltageFields({ strip, params, set }: { strip: StripDTO; params: VoltageParams; set: SetFn }) {
  return (
    <>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Lower limit">
          <div className="flex items-center gap-2">
            <Toggle checked={params.lowEnabled} onChange={(lowEnabled) => set({ lowEnabled })} label="Lower limit" />
            <Num value={params.lowV} min={50} max={300} unit="V" onChange={(v) => set({ lowV: v })} />
          </div>
        </Field>
        <Field label="Upper limit">
          <div className="flex items-center gap-2">
            <Toggle checked={params.highEnabled} onChange={(highEnabled) => set({ highEnabled })} label="Upper limit" />
            <Num value={params.highV} min={100} max={400} unit="V" onChange={(v) => set({ highV: v })} />
          </div>
        </Field>
      </div>
      <Field label="Outside range for">
        <Num value={params.holdSec} min={0} max={3600} unit="sec" onChange={(v) => set({ holdSec: v })} />
      </Field>
      <Field label="Action">
        <ActionSelect value={params.action} onChange={(action) => set({ action })} />
      </Field>
      <Field label="Outlets to switch off">
        <div className="flex flex-wrap gap-1.5">
          {strip.relays.map((r) => (
            <Chip
              key={r.index}
              active={params.outlets.includes(r.index)}
              onClick={() => set({ outlets: params.outlets.includes(r.index) ? params.outlets.filter((x) => x !== r.index) : [...params.outlets, r.index].sort() })}
            >
              {r.name}
            </Chip>
          ))}
        </div>
      </Field>
      <Field label="Restore after normal for" hint="0 keeps outlets off until you switch them on.">
        <Num value={params.restoreAfterMin} min={0} max={1440} unit="min" onChange={(v) => set({ restoreAfterMin: v })} />
      </Field>
    </>
  );
}

function CurrentFields({ strip, params, set, relayIndex, setRelayIndex }: { strip: StripDTO; params: CurrentParams; set: SetFn; relayIndex: number | null; setRelayIndex: (v: number) => void }) {
  return (
    <>
      <Field label="Protect">
        <div className="flex gap-1.5">
          <Chip active={params.scope === 'strip'} onClick={() => set({ scope: 'strip' })}>
            Whole strip
          </Chip>
          <Chip active={params.scope === 'outlet'} onClick={() => set({ scope: 'outlet' })}>
            One outlet
          </Chip>
        </div>
      </Field>
      {params.scope === 'outlet' && (
        <Field label="Outlet">
          <OutletSelect strip={strip} value={relayIndex} onChange={setRelayIndex} />
        </Field>
      )}
      <Field label="Current limit">
        <Num value={params.limitA} min={0.1} max={100} step={0.5} unit="A" onChange={(v) => set({ limitA: v })} />
      </Field>
      <Field label="Over limit for">
        <Num value={params.holdSec} min={0} max={3600} unit="sec" onChange={(v) => set({ holdSec: v })} />
      </Field>
      <Field label="Action">
        <ActionSelect value={params.action} onChange={(action) => set({ action })} />
      </Field>
    </>
  );
}

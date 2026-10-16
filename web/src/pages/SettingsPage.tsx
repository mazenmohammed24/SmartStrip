import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Bell, Check, ChevronRight, Info, Palette, Plus, Zap } from 'lucide-react';
import type { SettingsDTO } from '@shared/types';
import { AddStripModal } from '../components/strips/AddStripModal';
import { api } from '../lib/apiClient';
import { useSettingsStore } from '../store/settingsStore';

const ACCENT_PRESETS = ['#0f766e', '#16803c', '#176f91', '#7c3aed', '#c2410c', '#be123c'];

function Section({ icon, title, children }: { icon: ReactNode; title: string; children: ReactNode }) {
  return (
    <section className="overflow-hidden rounded-2xl border border-border bg-surface">
      <h2 className="flex items-center gap-2 border-b border-border bg-surface-alt/50 px-4 py-2.5 text-xs font-semibold uppercase tracking-wide text-text-muted">
        <span className="text-primary">{icon}</span>
        {title}
      </h2>
      <div className="divide-y divide-border">{children}</div>
    </section>
  );
}

function Row({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-4 px-4 py-3">
      <div className="min-w-0">
        <p className="text-sm font-medium text-text">{label}</p>
        {hint && <p className="text-[11px] text-text-muted">{hint}</p>}
      </div>
      <div className="shrink-0">{children}</div>
    </div>
  );
}

function Segmented<T extends string>({ value, options, onChange }: { value: T; options: { value: T; label: string }[]; onChange: (v: T) => void }) {
  return (
    <div className="flex rounded-xl bg-surface-alt p-1" role="radiogroup">
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          role="radio"
          aria-checked={value === o.value}
          onClick={() => onChange(o.value)}
          className={`rounded-lg px-3 py-1 text-xs font-semibold transition-colors ${value === o.value ? 'bg-surface text-primary shadow-sm' : 'text-text-muted hover:text-text'}`}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

function NumberField({ value, onCommit, min, step, suffix, width = 'w-24' }: { value: number; onCommit: (n: number) => void; min?: number; step?: number; suffix?: string; width?: string }) {
  const [draft, setDraft] = useState(String(value));
  useEffect(() => setDraft(String(value)), [value]);
  const commit = () => {
    const n = Number(draft);
    if (Number.isFinite(n) && n !== value && (min === undefined || n >= min)) onCommit(n);
    else setDraft(String(value));
  };
  return (
    <span className="flex items-center gap-1.5">
      <input
        type="number"
        inputMode="decimal"
        min={min}
        step={step}
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onBlur={commit}
        onKeyDown={(e) => e.key === 'Enter' && e.currentTarget.blur()}
        className={`${width} rounded-lg border border-border bg-surface px-2 py-1.5 text-right text-sm tabular-nums text-text focus:border-primary focus:outline-none`}
      />
      {suffix && <span className="text-xs text-text-muted">{suffix}</span>}
    </span>
  );
}

export default function SettingsPage() {
  const settings = useSettingsStore((s) => s.settings);
  const fetchSettings = useSettingsStore((s) => s.fetchSettings);
  const updateSettings = useSettingsStore((s) => s.updateSettings);
  const [push, setPush] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [currency, setCurrency] = useState('');

  useEffect(() => {
    void fetchSettings();
  }, [fetchSettings]);
  useEffect(() => {
    if (settings) setCurrency(settings.currency);
  }, [settings?.currency]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!settings) return null;
  const set = (patch: Partial<SettingsDTO>) => void updateSettings(patch);

  const sendTestPush = async () => {
    setPush(null);
    try {
      await api.post('/push/test');
      setPush('Test push sent.');
    } catch (err) {
      setPush(err instanceof Error ? err.message : 'Failed to send test push');
    }
  };

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-3">
      <Section icon={<Palette size={14} />} title="Appearance">
        <Row label="Theme">
          <Segmented
            value={settings.theme}
            onChange={(theme) => set({ theme })}
            options={[
              { value: 'system', label: 'Auto' },
              { value: 'light', label: 'Light' },
              { value: 'dark', label: 'Dark' },
            ]}
          />
        </Row>
        <Row label="Time format">
          <Segmented value={settings.timeFormat} onChange={(timeFormat) => set({ timeFormat })} options={[{ value: '24h', label: '24h' }, { value: '12h', label: '12h' }]} />
        </Row>
        <Row label="Accent colour" hint="Synced to the Android app">
          <div className="flex items-center gap-1.5">
            {ACCENT_PRESETS.map((color) => (
              <button
                key={color}
                type="button"
                onClick={() => set({ accentColor: color })}
                aria-label={`Use ${color}`}
                className="flex h-6 w-6 items-center justify-center rounded-full ring-offset-2 ring-offset-surface transition hover:scale-110 data-[on=true]:ring-2 data-[on=true]:ring-text"
                data-on={settings.accentColor.toLowerCase() === color}
                style={{ backgroundColor: color }}
              >
                {settings.accentColor.toLowerCase() === color && <Check size={13} className="text-white" />}
              </button>
            ))}
            <input type="color" value={settings.accentColor} onChange={(e) => set({ accentColor: e.target.value })} aria-label="Custom colour" className="h-6 w-7 cursor-pointer rounded border border-border bg-surface p-0.5" />
          </div>
        </Row>
      </Section>

      <Section icon={<Zap size={14} />} title="Energy & tariff">
        <Row label="Tariff" hint={settings.tariffMode === 'egypt' ? 'Egypt tiered residential prices' : 'One flat price per kWh'}>
          <Segmented value={settings.tariffMode} onChange={(tariffMode) => set({ tariffMode })} options={[{ value: 'egypt', label: 'Egypt' }, { value: 'flat', label: 'Flat' }]} />
        </Row>
        {settings.tariffMode === 'flat' && (
          <Row label="Rate per kWh">
            <NumberField value={settings.ratePerKwh} min={0} step={0.01} onCommit={(ratePerKwh) => set({ ratePerKwh })} suffix={settings.currency} />
          </Row>
        )}
        <Row label="Energy unit">
          <Segmented value={settings.energyUnit} onChange={(energyUnit) => set({ energyUnit })} options={[{ value: 'kWh', label: 'kWh' }, { value: 'Wh', label: 'Wh' }]} />
        </Row>
        <Row label="Currency">
          <input
            value={currency}
            maxLength={3}
            onChange={(e) => setCurrency(e.target.value.toUpperCase())}
            onBlur={() => currency.length === 3 && currency !== settings.currency && set({ currency })}
            onKeyDown={(e) => e.key === 'Enter' && e.currentTarget.blur()}
            aria-label="Currency"
            className="w-16 rounded-lg border border-border bg-surface px-2 py-1.5 text-center text-sm uppercase text-text focus:border-primary focus:outline-none"
          />
        </Row>
        <Row label="Overload threshold" hint="Warn above this load per strip">
          <NumberField value={settings.overloadThresholdWatts} min={100} step={50} suffix="W" onCommit={(overloadThresholdWatts) => set({ overloadThresholdWatts })} />
        </Row>
      </Section>

      <Section icon={<Bell size={14} />} title="Notifications">
        <Row label="Test push" hint={push ?? 'Verify push delivery to this device'}>
          <button type="button" onClick={() => void sendTestPush()} className="rounded-lg bg-primary/10 px-3 py-1.5 text-xs font-semibold text-primary hover:bg-primary/20">
            Send test
          </button>
        </Row>
      </Section>

      <Section icon={<Plus size={14} />} title="Devices">
        <button type="button" onClick={() => setAdding(true)} className="flex w-full items-center justify-between px-4 py-3 text-left hover:bg-surface-hover">
          <span>
            <span className="block text-sm font-medium text-text">Add a strip</span>
            <span className="block text-[11px] text-text-muted">Provision over Wi-Fi or adopt a connected strip</span>
          </span>
          <ChevronRight size={16} className="text-text-muted" />
        </button>
        <Link to="/rooms" className="flex items-center justify-between px-4 py-3 hover:bg-surface-hover">
          <span className="text-sm font-medium text-text">Rooms</span>
          <ChevronRight size={16} className="text-text-muted" />
        </Link>
      </Section>

      <Section icon={<Info size={14} />} title="About">
        <Row label="SmartStrip Portal" hint="Local control for MTTL-W01 strips, no cloud">
          <span className="text-xs text-text-muted">v1</span>
        </Row>
      </Section>

      <AddStripModal open={adding} onClose={() => setAdding(false)} />
    </div>
  );
}

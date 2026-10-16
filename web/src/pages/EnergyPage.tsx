import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { ChevronLeft, RotateCcw } from 'lucide-react';
import type { EnergyReportDTO } from '@shared/types';
import { Badge } from '../components/ui/Badge';
import { Button, Spinner } from '../components/ui/Button';
import { Card, CardBody } from '../components/ui/Card';
import { Input, Label } from '../components/ui/Input';
import { Modal } from '../components/ui/Modal';
import { api } from '../lib/apiClient';
import { CHART_PALETTE } from '../lib/chartPalette';
import { formatCurrency, formatKwh } from '../lib/formatters';
import { useFleetStore } from '../store/fleetStore';
import { useResolvedTheme, useSettingsStore } from '../store/settingsStore';

const RANGES = [7, 30, 90] as const;

export default function EnergyPage() {
  const { stripId } = useParams<{ stripId: string }>();
  const strip = useFleetStore((s) => s.strips.find((x) => x.id === stripId));
  const loaded = useFleetStore((s) => s.loaded);
  const fetchStrips = useFleetStore((s) => s.fetchStrips);
  const updateSettings = useSettingsStore((s) => s.updateSettings);
  const palette = CHART_PALETTE[useResolvedTheme()];
  const [days, setDays] = useState<(typeof RANGES)[number]>(7);
  const [report, setReport] = useState<EnergyReportDTO | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tariffOpen, setTariffOpen] = useState(false);
  const [rateDraft, setRateDraft] = useState('');
  const [modeDraft, setModeDraft] = useState<'egypt' | 'flat'>('egypt');
  const [confirmReset, setConfirmReset] = useState(false);

  const load = useCallback(async () => {
    if (!stripId) return;
    try {
      setReport(await api.get<EnergyReportDTO>(`/power/energy?stripId=${stripId}&days=${days}`));
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load energy data.');
    }
  }, [stripId, days]);

  useEffect(() => {
    if (!loaded) void fetchStrips();
  }, [loaded, fetchStrips]);

  useEffect(() => {
    void load();
    const t = setInterval(() => void load(), 60_000);
    return () => clearInterval(t);
  }, [load]);

  const back = (
    <Link to={`/strips/${stripId}/settings`} aria-label="Back" className="rounded-lg p-1.5 text-text-muted hover:bg-surface-hover">
      <ChevronLeft size={20} />
    </Link>
  );

  if (error) {
    return (
      <div className="flex flex-col gap-3">
        {back}
        <p role="alert" className="rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger">
          {error}
        </p>
      </div>
    );
  }
  if (!report) {
    return (
      <div className="flex flex-col gap-3">
        {back}
        <div className="flex justify-center py-16">
          <Spinner size={24} />
        </div>
      </div>
    );
  }

  const delta = report.previousKwh > 0 ? ((report.totalKwh - report.previousKwh) / report.previousKwh) * 100 : null;
  const maxOutlet = Math.max(0.0001, ...report.outlets.map((o) => o.kwh));

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-2">
        {back}
        <div className="min-w-0 flex-1">
          <h1 className="text-lg font-semibold text-text">Energy</h1>
          <p className="truncate text-xs text-text-muted">{strip?.name}</p>
        </div>
        <Button variant="secondary" size="sm" icon={<RotateCcw size={14} />} onClick={() => setConfirmReset(true)}>
          Reset
        </Button>
      </div>

      <div className="flex gap-1.5 rounded-xl bg-surface-alt p-1" role="tablist">
        {RANGES.map((r) => (
          <button
            key={r}
            role="tab"
            aria-selected={days === r}
            onClick={() => setDays(r)}
            className={`flex-1 rounded-lg py-1.5 text-sm font-medium transition-colors ${days === r ? 'bg-surface text-primary shadow-sm' : 'text-text-muted'}`}
          >
            {r} days
          </button>
        ))}
      </div>

      <Card>
        <CardBody className="flex flex-col gap-3">
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="text-xs font-semibold uppercase tracking-wide text-primary">Total consumption</p>
              <p className="mt-1 font-mono text-3xl font-semibold text-text">{formatKwh(report.totalKwh)}</p>
            </div>
            <div className="text-right">
              <p className="text-xs font-semibold uppercase tracking-wide text-primary">Cost</p>
              <p className="mt-1 font-mono text-xl font-semibold text-text">{formatCurrency(report.cost, report.currency)}</p>
            </div>
          </div>
          <div className="flex items-center justify-between gap-2">
            {delta === null ? (
              <Badge>No earlier data to compare</Badge>
            ) : (
              <Badge tone={delta > 0 ? 'warning' : 'success'}>
                {delta > 0 ? '▲' : '▼'} {Math.abs(delta).toFixed(0)}% vs previous {days} days
              </Badge>
            )}
            <button
              type="button"
              className="text-xs text-primary hover:underline"
              onClick={() => {
                setRateDraft(String(report.tariff.mode === 'flat' ? report.ratePerKwh : 0.16));
                setModeDraft(report.tariff.mode);
                setTariffOpen(true);
              }}
            >
              {report.tariff.mode === 'egypt' ? 'Egypt tariff' : 'Tariff'} · avg {formatCurrency(report.ratePerKwh, report.currency)}/kWh
            </button>
          </div>
        </CardBody>
      </Card>

      <Card>
        <CardBody>
          <p className="mb-3 text-xs font-semibold uppercase tracking-wide text-primary">Daily usage</p>
          <div className="h-56">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={report.daily} margin={{ left: -16, right: 4, top: 4 }}>
                <CartesianGrid stroke={palette.grid} vertical={false} />
                <XAxis
                  dataKey="date"
                  tickFormatter={(d: string) => d.slice(5)}
                  tick={{ fill: palette.textMuted, fontSize: 11 }}
                  stroke={palette.axis}
                  minTickGap={16}
                />
                <YAxis tick={{ fill: palette.textMuted, fontSize: 11 }} stroke={palette.axis} />
                <Tooltip
                  formatter={(v, _n, item) => [`${formatKwh(Number(v))} · ${formatCurrency((item.payload as { cost: number }).cost, report.currency)}`, 'Usage']}
                  contentStyle={{ background: palette.surface, border: `1px solid ${palette.grid}`, borderRadius: 12, color: palette.text }}
                />
                <Bar dataKey="kwh" className="fill-primary" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </CardBody>
      </Card>

      <Card>
        <CardBody className="flex flex-col gap-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-primary">By outlet</p>
          {report.outlets.map((o) => (
            <div key={o.relayIndex}>
              <div className="flex items-center justify-between text-sm">
                <span className="text-text">{o.name}</span>
                <span className="font-mono text-text-muted">
                  {formatKwh(o.kwh)} · {formatCurrency(o.cost, report.currency)}
                </span>
              </div>
              <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-surface-hover">
                <div className="h-full rounded-full bg-primary transition-[width] duration-500" style={{ width: `${(o.kwh / maxOutlet) * 100}%` }} />
              </div>
            </div>
          ))}
        </CardBody>
      </Card>

      <Modal
        open={tariffOpen}
        onClose={() => setTariffOpen(false)}
        title="Electricity tariff"
        footer={
          <>
            <Button variant="secondary" onClick={() => setTariffOpen(false)}>
              Cancel
            </Button>
            <Button
              disabled={modeDraft === 'flat' && (!(Number(rateDraft) >= 0) || rateDraft === '')}
              onClick={async () => {
                await updateSettings(modeDraft === 'egypt' ? { tariffMode: 'egypt' } : { tariffMode: 'flat', ratePerKwh: Number(rateDraft) });
                setTariffOpen(false);
                await load();
              }}
            >
              Save
            </Button>
          </>
        }
      >
        <div className="mb-3 flex gap-1.5 rounded-xl bg-surface-alt p-1" role="tablist">
          {(['egypt', 'flat'] as const).map((m) => (
            <button
              key={m}
              role="tab"
              aria-selected={modeDraft === m}
              onClick={() => setModeDraft(m)}
              className={`flex-1 rounded-lg py-1.5 text-sm font-medium transition-colors ${modeDraft === m ? 'bg-surface text-primary shadow-sm' : 'text-text-muted'}`}
            >
              {m === 'egypt' ? 'Egypt residential' : 'Flat rate'}
            </button>
          ))}
        </div>
        {modeDraft === 'egypt' ? (
          <div className="flex flex-col gap-2">
            <div className="overflow-hidden rounded-xl border border-border text-sm">
              <div className="grid grid-cols-3 bg-surface-alt px-3 py-1.5 text-xs font-semibold uppercase tracking-wide text-primary">
                <span>kWh / month</span>
                <span className="text-right">EGP / kWh</span>
                <span className="text-right">Service</span>
              </div>
              {report.tariff.levels.map((l, i) => (
                <div
                  key={l.label}
                  className={`grid grid-cols-3 px-3 py-1.5 font-mono ${i === report.tariff.currentLevel ? 'bg-primary/10 font-semibold text-text' : 'text-text-muted'}`}
                >
                  <span>{l.label}</span>
                  <span className="text-right">{l.rate.toFixed(2)}</span>
                  <span className="text-right">{l.service}</span>
                </div>
              ))}
            </div>
            <p className="text-xs text-text-muted">{report.tariff.note}</p>
            <p className="text-xs text-text-muted">Used this month: {formatKwh(report.tariff.monthToDateKwh)}</p>
          </div>
        ) : (
          <>
            <Label htmlFor="rate">Price per kWh</Label>
            <Input id="rate" type="number" inputMode="decimal" min={0} step={0.01} value={rateDraft} onChange={(e) => setRateDraft(e.target.value)} autoFocus />
          </>
        )}
      </Modal>

      <Modal
        open={confirmReset}
        onClose={() => setConfirmReset(false)}
        title="Reset energy data?"
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirmReset(false)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              onClick={async () => {
                await api.delete(`/power/energy?stripId=${stripId}`);
                setConfirmReset(false);
                await load();
              }}
            >
              Reset
            </Button>
          </>
        }
      >
        <p className="text-sm text-text-muted">Deletes recorded consumption history for this strip. This cannot be undone.</p>
      </Modal>
    </div>
  );
}

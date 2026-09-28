import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { PowerHistoryResponse, PowerRange } from '@shared/types';
import { PowerHistoryChart, type ChartSeries } from '../components/power/PowerHistoryChart';
import { RangeToggle } from '../components/power/RangeToggle';
import { Spinner } from '../components/ui/Button';
import { EmptyState } from '../components/ui/EmptyState';
import { Select } from '../components/ui/Input';
import { api } from '../lib/apiClient';
import { useFleetStore } from '../store/fleetStore';

interface SummaryResponse {
  todayKwh: number;
  todayCost: number;
  currency: string;
  liveWatts: number;
}

export default function PowerPage() {
  const strips = useFleetStore((s) => s.strips);
  const [searchParams, setSearchParams] = useSearchParams();
  const stripId = searchParams.get('stripId') ?? strips[0]?.id ?? '';
  const [range, setRange] = useState<PowerRange>('7d');
  const [breakdown, setBreakdown] = useState(false);
  const [series, setSeries] = useState<ChartSeries[]>([]);
  const [summary, setSummary] = useState<SummaryResponse | null>(null);
  const [loading, setLoading] = useState(false);

  const strip = strips.find((s) => s.id === stripId);
  const totalKwh = series[0]?.points.reduce((sum, point) => sum + point.kwh, 0) ?? 0;
  const avgDailyKwh = series[0]?.points.length ? totalKwh / series[0].points.length : 0;

  useEffect(() => {
    if (!stripId) return;
    let cancelled = false;
    setLoading(true);

    void (async () => {
      if (breakdown && strip) {
        const results = await Promise.all(
          strip.relays.map((r) => api.get<PowerHistoryResponse>(`/power/history?stripId=${stripId}&range=${range}&relayIndex=${r.index}`)),
        );
        if (cancelled) return;
        setSeries(results.map((res, i) => ({ key: `r${i}`, label: strip.relays[i].name, points: res.points })));
      } else {
        const res = await api.get<PowerHistoryResponse>(`/power/history?stripId=${stripId}&range=${range}`);
        if (cancelled) return;
        setSeries([{ key: 'total', label: 'Total', points: res.points }]);
      }
      const sum = await api.get<SummaryResponse>(`/power/summary?stripId=${stripId}`);
      if (!cancelled) {
        setSummary(sum);
        setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [stripId, range, breakdown, strip]);

  if (strips.length === 0) {
    return (
      <EmptyState icon={<span>⚡</span>} title="No strips yet" description="Add a strip from the Scan page to see power history." />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <Select value={stripId} onChange={(e) => setSearchParams({ stripId: e.target.value })} className="sm:max-w-xs">
          {strips.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </Select>
        <RangeToggle value={range} onChange={setRange} />
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <MetricCard label="Total used" value={totalKwh.toFixed(2)} unit="kWh" />
        <MetricCard label="Average daily" value={avgDailyKwh.toFixed(2)} unit="kWh" />
        {summary && strip && <MetricCard label="Live draw" value={String(Math.round(strip.totalWatts))} unit="W" />}
      </div>

      <div className="rounded-2xl border border-border bg-surface p-4">
        <div className="mb-2 flex items-center justify-between">
          <div>
            <h2 className="text-sm font-semibold text-text">Daily usage</h2>
            <p className="text-xs text-text-muted">Synced from Voltra energy buckets</p>
          </div>
          <label className="flex items-center gap-2 text-sm text-text-muted">
            <input type="checkbox" checked={breakdown} onChange={(e) => setBreakdown(e.target.checked)} className="accent-primary" />
            By outlet
          </label>
        </div>
        {loading ? (
          <div className="flex justify-center py-16">
            <Spinner size={24} />
          </div>
        ) : (
          <PowerHistoryChart series={series} range={range} />
        )}
      </div>
    </div>
  );
}

function MetricCard({ label, value, unit }: { label: string; value: string; unit: string }) {
  return (
    <div className="rounded-xl border border-border bg-surface p-4 shadow-sm">
      <p className="text-xs font-semibold uppercase tracking-wide text-text-muted">{label}</p>
      <p className="mt-2 flex items-baseline gap-2 text-3xl font-semibold text-success">
        {value}
        <span className="text-sm font-medium text-text-muted">{unit}</span>
      </p>
    </div>
  );
}

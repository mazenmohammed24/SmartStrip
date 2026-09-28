import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { PowerPointDTO, PowerRange } from '@shared/types';
import { formatWatts } from '../../lib/formatters';
import { CHART_PALETTE } from '../../lib/chartPalette';
import { useResolvedTheme } from '../../store/settingsStore';

export interface ChartSeries {
  key: string;
  label: string;
  points: PowerPointDTO[];
}

function tickFormatter(range: PowerRange) {
  return (iso: string) => {
    const d = new Date(iso);
    if (range === 'day') return d.toLocaleTimeString(undefined, { hour: 'numeric' });
    if (range === 'week') return d.toLocaleDateString(undefined, { weekday: 'short' });
    return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  };
}

export function PowerHistoryChart({ series, range }: { series: ChartSeries[]; range: PowerRange }) {
  const theme = useResolvedTheme();
  const palette = CHART_PALETTE[theme];

  const bucketMap = new Map<string, Record<string, number | string>>();
  for (const s of series) {
    for (const p of s.points) {
      const row = bucketMap.get(p.bucketStart) ?? { bucketStart: p.bucketStart };
      row[s.key] = p.avgW;
      bucketMap.set(p.bucketStart, row);
    }
  }
  const data = [...bucketMap.values()].sort((a, b) => ((a.bucketStart as string) < (b.bucketStart as string) ? -1 : 1));
  const fmt = tickFormatter(range);

  if (data.length === 0) {
    return <div className="flex h-[280px] items-center justify-center text-sm text-text-muted">No data for this range yet.</div>;
  }

  return (
    <div style={{ width: '100%', height: 280 }}>
      <ResponsiveContainer>
        <LineChart data={data} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
          <CartesianGrid stroke={palette.grid} vertical={false} />
          <XAxis
            dataKey="bucketStart"
            tickFormatter={fmt}
            tick={{ fill: palette.textMuted, fontSize: 12 }}
            tickLine={false}
            axisLine={{ stroke: palette.axis }}
            minTickGap={24}
          />
          <YAxis
            tick={{ fill: palette.textMuted, fontSize: 12 }}
            tickLine={false}
            axisLine={false}
            width={52}
            tickFormatter={(v) => formatWatts(Number(v))}
          />
          <Tooltip
            contentStyle={{ background: palette.surface, border: `1px solid ${palette.grid}`, borderRadius: 12, fontSize: 12 }}
            labelFormatter={(v) => new Date(v as string).toLocaleString()}
            formatter={(value, name) => [formatWatts(Number(value)), String(name)]}
          />
          {series.length > 1 && <Legend wrapperStyle={{ fontSize: 12, color: palette.textMuted }} />}
          {series.map((s, i) => (
            <Line
              key={s.key}
              type="monotone"
              dataKey={s.key}
              name={s.label}
              stroke={palette.series[i % palette.series.length]}
              strokeWidth={2}
              dot={false}
              activeDot={{ r: 4 }}
              isAnimationActive={false}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

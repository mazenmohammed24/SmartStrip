import type { ReactNode } from 'react';
import type { MttlStripTelemetry } from '@shared/types';
import { Card, CardBody, CardHeader } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { formatKwh, formatWatts } from '../../lib/formatters';
import { WifiBars } from './WifiBars';

/**
 * Hardware readings that only the strip itself reports: line voltage, current,
 * Wi-Fi signal, per-outlet energy and temperature. Everything here comes straight
 * off the MTTL protocol, so it is absent for other drivers.
 */
export function TelemetryCard({ telemetry }: { telemetry: MttlStripTelemetry | null }) {
  if (!telemetry) return null;

  const offline = telemetry.connectivity !== 'online';

  return (
    <Card>
      <CardHeader>
        <h2 className="text-sm font-semibold text-text">Hardware readings</h2>
        {offline && <Badge tone="danger">Strip offline</Badge>}
      </CardHeader>
      <CardBody className="flex flex-col gap-3">
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
          <Metric label="Voltage" value={telemetry.voltageV !== null ? `${telemetry.voltageV} V` : '—'} />
          <Metric label="Current" value={telemetry.currentA !== null ? `${telemetry.currentA} A` : '—'} />
          <Metric label="Wi-Fi" value={<WifiBars rssi={telemetry.rssi} size={18} />} />
          <Metric label="Lifetime" value={formatKwh(telemetry.totalEnergyKwh)} />
        </div>

        <div className="flex flex-col gap-1.5">
          {telemetry.outlets.map((outlet) => (
            <div key={outlet.relayIndex} className="flex items-center justify-between gap-2 text-sm">
              <span className="text-text-muted">Outlet {outlet.relayIndex + 1}</span>
              <span className="flex items-center gap-2 text-text">
                <span className="tabular-nums">{formatWatts(outlet.watts)}</span>
                <span className="text-xs text-text-muted">{formatKwh(outlet.energyKwh)}</span>
                <span className="w-12 text-right text-xs text-text-muted">{outlet.temperatureC}°C</span>
              </span>
            </div>
          ))}
        </div>

        <p className="text-xs text-text-muted">
          {telemetry.model} · firmware {telemetry.fw} · dialled in from {telemetry.ip || 'unknown address'} · device port{' '}
          {telemetry.driverStripId}
        </p>
      </CardBody>
    </Card>
  );
}

function Metric({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="rounded-xl bg-surface-alt px-3 py-2">
      <p className="text-xs text-text-muted">{label}</p>
      <div className="flex h-5 items-center text-sm font-semibold tabular-nums text-text">{value}</div>
    </div>
  );
}

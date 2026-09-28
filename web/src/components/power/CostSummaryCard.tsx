import { formatCurrency, formatKwh, formatWatts } from '../../lib/formatters';
import { Card, CardBody } from '../ui/Card';

export function CostSummaryCard({
  todayKwh,
  todayCost,
  currency,
  liveWatts,
}: {
  todayKwh: number;
  todayCost: number;
  currency: string;
  liveWatts: number;
}) {
  return (
    <div className="grid grid-cols-3 gap-3">
      <Card>
        <CardBody>
          <p className="text-xs text-text-muted">Live draw</p>
          <p className="mt-1 text-lg font-semibold text-text">{formatWatts(liveWatts)}</p>
        </CardBody>
      </Card>
      <Card>
        <CardBody>
          <p className="text-xs text-text-muted">Today</p>
          <p className="mt-1 text-lg font-semibold text-text">{formatKwh(todayKwh)}</p>
        </CardBody>
      </Card>
      <Card>
        <CardBody>
          <p className="text-xs text-text-muted">Est. cost today</p>
          <p className="mt-1 text-lg font-semibold text-text">{formatCurrency(todayCost, currency)}</p>
        </CardBody>
      </Card>
    </div>
  );
}

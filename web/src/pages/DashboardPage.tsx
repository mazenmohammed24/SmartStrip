import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { StripGrid } from '../components/strips/StripGrid';
import { Spinner } from '../components/ui/Button';
import { EmptyState } from '../components/ui/EmptyState';
import { formatWatts } from '../lib/formatters';
import { useFleetStore } from '../store/fleetStore';

export default function DashboardPage() {
  const strips = useFleetStore((s) => s.strips);
  const loading = useFleetStore((s) => s.loading);
  const loaded = useFleetStore((s) => s.loaded);
  const fetchStrips = useFleetStore((s) => s.fetchStrips);

  useEffect(() => {
    void fetchStrips();
  }, [fetchStrips]);

  if (loading && !loaded) {
    return (
      <div className="flex justify-center py-16">
        <Spinner size={28} />
      </div>
    );
  }

  if (strips.length === 0) {
    return (
      <EmptyState
        icon={<span>📡</span>}
        title="No strips in your fleet yet"
        description="Scan your network to find nearby smart strips and add them."
        action={
          <Link
            to="/scan"
            className="inline-flex items-center justify-center rounded-xl bg-primary px-4 py-2 text-sm font-medium text-primary-fg hover:bg-primary-hover"
          >
            Scan network
          </Link>
        }
      />
    );
  }

  const totalWatts = strips.reduce((a, s) => a + s.totalWatts, 0);
  const onlineCount = strips.filter((s) => s.connectivity === 'online').length;
  const outletsOn = strips.reduce((a, s) => a + s.relays.filter((r) => r.state === 'on').length, 0);

  return (
    <div className="flex flex-col gap-6">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 sm:gap-4">
        <SummaryTile label="Strips" value={String(strips.length)} />
        <SummaryTile label="Online" value={`${onlineCount}/${strips.length}`} />
        <SummaryTile label="Total draw" value={formatWatts(totalWatts)} />
        <SummaryTile label="Outlets on" value={String(outletsOn)} />
      </div>
      <StripGrid strips={strips} />
    </div>
  );
}

function SummaryTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-border bg-surface p-4 shadow-[0_1px_2px_rgb(24_32_27/0.04)]">
      <p className="text-xs font-semibold uppercase tracking-wide text-text-muted">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-text">{value}</p>
    </div>
  );
}

import { useEffect } from 'react';
import type { ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';
import { BarChart3, CalendarClock, ChevronLeft, ChevronRight, SlidersHorizontal } from 'lucide-react';
import { RULE_KINDS } from '../components/rules/ruleMeta';
import { ConnectivityBadge } from '../components/strips/ConnectivityBadge';
import { Spinner } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { useFleetStore } from '../store/fleetStore';
import { useRulesStore } from '../store/rulesStore';

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-2">
      <h2 className="px-1 text-xs font-semibold uppercase tracking-wide text-primary">{title}</h2>
      <Card className="divide-y divide-border overflow-hidden">{children}</Card>
    </section>
  );
}

function Row({ to, icon, title, subtitle, count }: { to: string; icon: ReactNode; title: string; subtitle: string; count?: number }) {
  return (
    <Link to={to} className="flex items-center gap-3 px-4 py-3 transition-colors hover:bg-surface-hover">
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-surface-alt text-primary">{icon}</span>
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-medium text-text">{title}</span>
        <span className="block truncate text-xs text-text-muted">{subtitle}</span>
      </span>
      {count !== undefined && count > 0 && <span className="rounded-full bg-primary px-2 py-0.5 font-mono text-xs font-semibold text-primary-fg">{count}</span>}
      <ChevronRight size={16} className="text-text-muted" />
    </Link>
  );
}

export default function StripSettingsPage() {
  const { stripId } = useParams<{ stripId: string }>();
  const strip = useFleetStore((s) => s.strips.find((x) => x.id === stripId));
  const loaded = useFleetStore((s) => s.loaded);
  const fetchStrips = useFleetStore((s) => s.fetchStrips);
  const rules = useRulesStore((s) => s.rules);
  const fetchRules = useRulesStore((s) => s.fetchRules);

  useEffect(() => {
    if (!loaded) void fetchStrips();
    void fetchRules();
  }, [loaded, fetchStrips, fetchRules]);

  if (!strip) {
    return <div className="flex justify-center py-16 text-text-muted">{loaded ? 'Strip not found.' : <Spinner size={24} />}</div>;
  }

  const mine = rules.filter((r) => r.stripId === strip.id);

  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-center gap-2">
        <Link to={`/strips/${strip.id}`} aria-label="Back" className="rounded-lg p-1.5 text-text-muted hover:bg-surface-hover">
          <ChevronLeft size={20} />
        </Link>
        <div className="min-w-0 flex-1">
          <h1 className="truncate text-lg font-semibold text-text">{strip.name}</h1>
          <p className="text-xs text-text-muted">Strip settings</p>
        </div>
        <ConnectivityBadge connectivity={strip.connectivity} />
      </div>

      <Section title="Automation">
        <Row to={`/automations?stripId=${strip.id}`} icon={<CalendarClock size={18} />} title="Schedules" subtitle="Switch outlets on or off at set times" />
        {RULE_KINDS.map((k) => (
          <Row key={k.type} to={`/strips/${strip.id}/rules/${k.type}`} icon={<k.Icon size={18} />} title={k.title} subtitle={k.blurb} count={mine.filter((r) => r.type === k.type).length} />
        ))}
      </Section>

      <Section title="Energy">
        <Row to={`/strips/${strip.id}/energy`} icon={<BarChart3 size={18} />} title="Consumption" subtitle="Usage, cost and tariff" />
      </Section>

      <Section title="Device">
        <Row to={`/strips/${strip.id}`} icon={<SlidersHorizontal size={18} />} title="Outlets and details" subtitle="Rename, icons, remove from fleet" />
      </Section>
    </div>
  );
}

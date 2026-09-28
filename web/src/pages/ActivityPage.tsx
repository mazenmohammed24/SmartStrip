import { useEffect, useState } from 'react';
import type { EventDTO } from '@shared/types';
import { Badge } from '../components/ui/Badge';
import { EmptyState } from '../components/ui/EmptyState';
import { Select } from '../components/ui/Input';
import { api } from '../lib/apiClient';
import { formatRelativeTime } from '../lib/formatters';
import { onWsMessage } from '../lib/wsClient';
import { useStripNameResolver } from '../hooks/useStripNameResolver';
import { useFleetStore } from '../store/fleetStore';

type Tone = 'neutral' | 'success' | 'warning' | 'danger' | 'info';

const TYPE_TONE: Record<string, Tone> = {
  'relay-on': 'success',
  'relay-off': 'neutral',
  'schedule-fired': 'info',
  overload: 'danger',
  offline: 'warning',
  online: 'success',
  'strip-added': 'info',
  'strip-removed': 'neutral',
};

const TYPE_ICON: Record<string, string> = {
  'relay-on': '🟢',
  'relay-off': '⚪',
  'schedule-fired': '⏱️',
  overload: '⚠️',
  offline: '📴',
  online: '📶',
  'strip-added': '➕',
  'strip-removed': '➖',
};

export default function ActivityPage() {
  const strips = useFleetStore((s) => s.strips);
  const getStripName = useStripNameResolver();
  const [stripFilter, setStripFilter] = useState('all');
  const [events, setEvents] = useState<EventDTO[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    const query = stripFilter === 'all' ? '' : `?stripId=${stripFilter}`;
    void api.get<{ events: EventDTO[] }>(`/events${query}`).then((res) => {
      setEvents(res.events);
      setLoading(false);
    });
  }, [stripFilter]);

  useEffect(
    () =>
      onWsMessage((msg) => {
        if (msg.type !== 'event-logged') return;
        if (stripFilter !== 'all' && msg.event.stripId !== stripFilter) return;
        setEvents((prev) => [msg.event, ...prev]);
      }),
    [stripFilter],
  );

  return (
    <div className="flex flex-col gap-4">
      <Select value={stripFilter} onChange={(e) => setStripFilter(e.target.value)} className="sm:max-w-xs">
        <option value="all">All strips</option>
        {strips.map((s) => (
          <option key={s.id} value={s.id}>
            {s.name}
          </option>
        ))}
      </Select>

      {!loading && events.length === 0 && (
        <EmptyState icon={<span>📜</span>} title="No activity yet" description="Relay toggles, automations, and connectivity changes will show up here." />
      )}

      <div className="flex flex-col divide-y divide-border rounded-2xl border border-border bg-surface">
        {events.map((event) => {
          // Resolve current strip name from driverStripId (handles strip renames)
          const displayStripName = getStripName(event.driverStripId) ?? event.stripName ?? '';

          return (
            <div key={event.id} className="flex items-center gap-3 px-4 py-3">
              <span className="text-lg">{TYPE_ICON[event.type] ?? '•'}</span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm text-text">{event.message}</p>
                <p className="text-xs text-text-muted">
                  {displayStripName} · {formatRelativeTime(event.createdAt)}
                </p>
              </div>
              <Badge tone={TYPE_TONE[event.type] ?? 'neutral'}>{event.type.replace('-', ' ')}</Badge>
            </div>
          );
        })}
      </div>
    </div>
  );
}

import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Home, Power } from 'lucide-react';
import type { StripDTO } from '@shared/types';
import { ConnectivityBadge } from '../components/strips/ConnectivityBadge';
import { Button, Spinner } from '../components/ui/Button';
import { Card, CardBody } from '../components/ui/Card';
import { EmptyState } from '../components/ui/EmptyState';
import { Input, Label } from '../components/ui/Input';
import { Modal } from '../components/ui/Modal';
import { formatWatts } from '../lib/formatters';
import { useFleetStore } from '../store/fleetStore';

const UNASSIGNED = 'Unassigned';

export default function RoomsPage() {
  const strips = useFleetStore((s) => s.strips);
  const loaded = useFleetStore((s) => s.loaded);
  const fetchStrips = useFleetStore((s) => s.fetchStrips);
  const setAllRelays = useFleetStore((s) => s.setAllRelays);
  const setStripLocation = useFleetStore((s) => s.setStripLocation);
  const [moving, setMoving] = useState<StripDTO | null>(null);
  const [draft, setDraft] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    void fetchStrips();
  }, [fetchStrips]);

  const rooms = useMemo(() => {
    const map = new Map<string, StripDTO[]>();
    for (const s of strips) {
      const key = s.location?.trim() || UNASSIGNED;
      map.set(key, [...(map.get(key) ?? []), s]);
    }
    return [...map.entries()].sort(([a], [b]) => (a === UNASSIGNED ? 1 : b === UNASSIGNED ? -1 : a.localeCompare(b)));
  }, [strips]);
  const names = rooms.map(([n]) => n).filter((n) => n !== UNASSIGNED);

  if (!loaded) {
    return (
      <div className="flex justify-center py-16">
        <Spinner size={24} />
      </div>
    );
  }
  if (strips.length === 0) {
    return <EmptyState icon={<Home size={28} />} title="No rooms yet" description="Add a strip, then group it into a room." />;
  }

  const save = async () => {
    if (!moving) return;
    setSaving(true);
    try {
      await setStripLocation(moving.id, draft.trim() || null);
      setMoving(null);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="flex flex-col gap-4">
      <h1 className="px-1 text-lg font-semibold text-text">Rooms</h1>
      {rooms.map(([room, list]) => {
        const watts = list.reduce((a, s) => a + s.totalWatts, 0);
        const online = list.filter((s) => s.connectivity === 'online');
        return (
          <Card key={room}>
            <CardBody className="flex flex-col gap-3">
              <div className="flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <h2 className="truncate text-base font-semibold text-text">{room}</h2>
                  <p className="text-xs text-text-muted">
                    {list.length} {list.length === 1 ? 'strip' : 'strips'} · <span className="font-mono">{formatWatts(watts)}</span>
                  </p>
                </div>
                <Button
                  size="sm"
                  variant="secondary"
                  icon={<Power size={14} />}
                  disabled={online.length === 0}
                  onClick={() => online.forEach((s) => void setAllRelays(s.id, 'off'))}
                >
                  All off
                </Button>
              </div>
              <div className="flex flex-col divide-y divide-border">
                {list.map((s) => (
                  <div key={s.id} className="flex items-center gap-3 py-2">
                    <Link to={`/strips/${s.id}`} className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-text">{s.name}</p>
                      <p className="font-mono text-xs text-text-muted">
                        {formatWatts(s.totalWatts)} · {s.relays.filter((r) => r.state === 'on').length}/{s.relays.length} on
                      </p>
                    </Link>
                    <ConnectivityBadge connectivity={s.connectivity} />
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => {
                        setMoving(s);
                        setDraft(s.location ?? '');
                      }}
                    >
                      Move
                    </Button>
                  </div>
                ))}
              </div>
            </CardBody>
          </Card>
        );
      })}

      <Modal
        open={moving !== null}
        onClose={() => setMoving(null)}
        title={`Room for ${moving?.name ?? ''}`}
        footer={
          <>
            <Button variant="secondary" onClick={() => setMoving(null)}>
              Cancel
            </Button>
            <Button onClick={() => void save()} loading={saving}>
              Save
            </Button>
          </>
        }
      >
        <Label htmlFor="roomName">Room name</Label>
        <Input id="roomName" value={draft} maxLength={64} onChange={(e) => setDraft(e.target.value)} placeholder="e.g. Living room" autoFocus />
        {names.length > 0 && (
          <div className="mt-3 flex flex-wrap gap-1.5">
            {names.map((n) => (
              <button key={n} type="button" onClick={() => setDraft(n)} className="rounded-full border border-border px-3 py-1 text-xs text-text-muted hover:bg-surface-hover">
                {n}
              </button>
            ))}
          </div>
        )}
        <p className="mt-2 text-xs text-text-muted">Leave empty to remove it from a room.</p>
      </Modal>
    </div>
  );
}

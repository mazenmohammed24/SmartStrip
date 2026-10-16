import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import type { ScheduleDTO } from '@shared/types';
import { Button } from '../components/ui/Button';
import { Card, CardBody } from '../components/ui/Card';
import { EmptyState } from '../components/ui/EmptyState';
import { Input, Label, Select } from '../components/ui/Input';
import { Modal } from '../components/ui/Modal';
import { Toggle } from '../components/ui/Toggle';
import { api } from '../lib/apiClient';
import { DAY_NAMES, formatClock } from '../lib/formatters';
import { useFleetStore } from '../store/fleetStore';
import { useSettingsStore } from '../store/settingsStore';

const ALL_DAYS = [0, 1, 2, 3, 4, 5, 6];

interface FormState {
  id?: string;
  name: string;
  stripId: string;
  relayIndex: number | null;
  action: 'on' | 'off';
  time: string;
  daysOfWeek: number[];
}

function emptyForm(defaultStripId: string): FormState {
  return { name: '', stripId: defaultStripId, relayIndex: null, action: 'on', time: '18:00', daysOfWeek: [...ALL_DAYS] };
}

function timeToDisplay(time: string, format: '12h' | '24h'): string {
  const [h, m] = time.split(':').map(Number);
  const d = new Date();
  d.setHours(h, m, 0, 0);
  return formatClock(d.toISOString(), format);
}

export default function AutomationsPage() {
  const strips = useFleetStore((s) => s.strips);
  const [searchParams] = useSearchParams();
  const filterStripId = searchParams.get('stripId');
  const timeFormat = useSettingsStore((s) => s.settings?.timeFormat ?? '24h');
  const [schedules, setSchedules] = useState<ScheduleDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState<FormState | null>(null);
  const [saving, setSaving] = useState(false);

  const load = async () => {
    const res = await api.get<{ schedules: ScheduleDTO[] }>('/schedules');
    setSchedules(res.schedules);
    setLoading(false);
  };

  const fetchStrips = useFleetStore((s) => s.fetchStrips);

  useEffect(() => {
    void load();
    void fetchStrips();
  }, [fetchStrips]);

  const openCreate = () => setForm(emptyForm(filterStripId ?? strips[0]?.id ?? ''));
  const openEdit = (s: ScheduleDTO) =>
    setForm({ id: s.id, name: s.name, stripId: s.stripId, relayIndex: s.relayIndex, action: s.action, time: s.time, daysOfWeek: s.daysOfWeek });

  const save = async () => {
    if (!form) return;
    setSaving(true);
    try {
      const payload = {
        name: form.name,
        stripId: form.stripId,
        relayIndex: form.relayIndex,
        action: form.action,
        time: form.time,
        daysOfWeek: form.daysOfWeek,
      };
      if (form.id) {
        await api.patch(`/schedules/${form.id}`, payload);
      } else {
        await api.post('/schedules', payload);
      }
      setForm(null);
      await load();
    } finally {
      setSaving(false);
    }
  };

  const toggleEnabled = async (s: ScheduleDTO) => {
    await api.patch(`/schedules/${s.id}`, { enabled: !s.enabled });
    await load();
  };

  const remove = async (id: string) => {
    await api.delete(`/schedules/${id}`);
    await load();
  };

  const runNow = async (id: string) => {
    await api.post(`/schedules/${id}/run-now`);
  };

  if (strips.length === 0) {
    return <EmptyState icon={<span>⏱️</span>} title="Add a strip first" description="Automations need at least one strip in your fleet." />;
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-2">
        {filterStripId ? <Link to={`/strips/${filterStripId}/settings`} className="text-sm text-primary hover:underline">← Strip settings</Link> : <span />}
        <Button onClick={openCreate}>New automation</Button>
      </div>

      {!loading && !schedules.some((s) => !filterStripId || s.stripId === filterStripId) && (
        <EmptyState icon={<span>⏱️</span>} title="No automations yet" description="Create time-based rules to turn outlets on or off automatically." />
      )}

      <div className="flex flex-col gap-3">
        {schedules.filter((s) => !filterStripId || s.stripId === filterStripId).map((s) => {
          const strip = strips.find((x) => x.id === s.stripId);
          const target =
            s.relayIndex === null ? 'All outlets' : (strip?.relays.find((r) => r.index === s.relayIndex)?.name ?? `Outlet ${s.relayIndex + 1}`);
          return (
            <Card key={s.id}>
              <CardBody className="flex flex-col gap-3 sm:flex-row sm:items-center">
                <Toggle checked={s.enabled} onChange={() => void toggleEnabled(s)} />
                <button type="button" className="min-w-0 flex-1 text-left" onClick={() => openEdit(s)}>
                  <p className="truncate font-medium text-text">{s.name}</p>
                  <p className="text-xs text-text-muted">
                    {strip?.name ?? 'Unknown strip'} · {target} · turns {s.action} at {timeToDisplay(s.time, timeFormat)} ·{' '}
                    {s.daysOfWeek.length === 7 ? 'Every day' : s.daysOfWeek.map((d) => DAY_NAMES[d]).join(', ')}
                  </p>
                </button>
                <div className="flex gap-2">
                  <Button size="sm" variant="secondary" onClick={() => void runNow(s.id)}>
                    Run now
                  </Button>
                  <Button size="sm" variant="danger" onClick={() => void remove(s.id)}>
                    Delete
                  </Button>
                </div>
              </CardBody>
            </Card>
          );
        })}
      </div>

      <Modal
        open={!!form}
        onClose={() => setForm(null)}
        title={form?.id ? 'Edit automation' : 'New automation'}
        footer={
          <>
            <Button variant="secondary" onClick={() => setForm(null)}>
              Cancel
            </Button>
            <Button onClick={() => void save()} loading={saving}>
              Save
            </Button>
          </>
        }
      >
        {form && (
          <div className="flex flex-col gap-3">
            <div>
              <Label htmlFor="autoName">Name</Label>
              <Input id="autoName" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="e.g. Evening lights" />
            </div>
            <div>
              <Label htmlFor="autoStrip">Strip</Label>
              <Select id="autoStrip" value={form.stripId} onChange={(e) => setForm({ ...form, stripId: e.target.value, relayIndex: null })}>
                {strips.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </Select>
            </div>
            <div>
              <Label htmlFor="autoRelay">Target</Label>
              <Select
                id="autoRelay"
                value={form.relayIndex ?? 'all'}
                onChange={(e) => setForm({ ...form, relayIndex: e.target.value === 'all' ? null : Number(e.target.value) })}
              >
                <option value="all">All outlets</option>
                {strips
                  .find((s) => s.id === form.stripId)
                  ?.relays.map((r) => (
                    <option key={r.index} value={r.index}>
                      {r.name}
                    </option>
                  ))}
              </Select>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label htmlFor="autoAction">Action</Label>
                <Select id="autoAction" value={form.action} onChange={(e) => setForm({ ...form, action: e.target.value as 'on' | 'off' })}>
                  <option value="on">Turn on</option>
                  <option value="off">Turn off</option>
                </Select>
              </div>
              <div>
                <Label htmlFor="autoTime">Time</Label>
                <Input id="autoTime" type="time" value={form.time} onChange={(e) => setForm({ ...form, time: e.target.value })} />
              </div>
            </div>
            <div>
              <Label>Repeat on</Label>
              <div className="flex gap-1.5">
                {DAY_NAMES.map((name, idx) => {
                  const active = form.daysOfWeek.includes(idx);
                  return (
                    <button
                      key={idx}
                      type="button"
                      onClick={() =>
                        setForm({
                          ...form,
                          daysOfWeek: active ? form.daysOfWeek.filter((d) => d !== idx) : [...form.daysOfWeek, idx].sort(),
                        })
                      }
                      className={`h-9 w-9 rounded-full text-xs font-medium ${active ? 'bg-primary text-primary-fg' : 'bg-surface-alt text-text-muted'}`}
                    >
                      {name[0]}
                    </button>
                  );
                })}
              </div>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}

import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Settings2 } from 'lucide-react';
import { useFleetStore } from '../../store/fleetStore';
import { Button } from '../ui/Button';
import { Input, Label } from '../ui/Input';
import { Modal } from '../ui/Modal';
import { DeviceIcon, IconGrid } from './IconPicker';

export function StripEditModal({ stripId, onClose }: { stripId: string | null; onClose: () => void }) {
  const strip = useFleetStore((s) => s.strips.find((x) => x.id === stripId));
  const renameStrip = useFleetStore((s) => s.renameStrip);
  const setStripLocation = useFleetStore((s) => s.setStripLocation);
  const setStripIcon = useFleetStore((s) => s.setStripIcon);
  const [name, setName] = useState('');
  const [location, setLocation] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (strip) {
      setName(strip.name);
      setLocation(strip.location ?? '');
      setError(null);
    }
    // Only reseed when a different strip is opened, not on every live update.
  }, [stripId]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!strip) return null;

  const dirty = name.trim() !== strip.name || location.trim() !== (strip.location ?? '');

  const save = async () => {
    const nextName = name.trim();
    if (!nextName) return;
    setSaving(true);
    setError(null);
    try {
      if (nextName !== strip.name) await renameStrip(strip.id, nextName);
      if (location.trim() !== (strip.location ?? '')) await setStripLocation(strip.id, location.trim() || null);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      size="lg"
      title={
        <span className="flex items-center gap-2">
          <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary text-primary-fg">
            <DeviceIcon icon={strip.icon} size={18} />
          </span>
          Edit strip
        </span>
      }
      footer={
        <>
          <Link to={`/strips/${strip.id}/settings`} className="mr-auto inline-flex items-center gap-1.5 text-xs font-medium text-primary hover:underline">
            <Settings2 size={14} />
            Automations
          </Link>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={() => void save()} loading={saving} disabled={!dirty || !name.trim()}>
            Save
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-3">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div>
            <Label htmlFor="strip-edit-name">Name</Label>
            <Input id="strip-edit-name" value={name} maxLength={64} onChange={(e) => setName(e.target.value)} />
          </div>
          <div>
            <Label htmlFor="strip-edit-location">Room</Label>
            <Input id="strip-edit-location" value={location} maxLength={64} placeholder="e.g. Living room" onChange={(e) => setLocation(e.target.value)} />
          </div>
        </div>
        {error && <p className="rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger">{error}</p>}
        <div>
          <Label>Icon</Label>
          <IconGrid value={strip.icon} maxHeight="34vh" onSelect={(icon) => void setStripIcon(strip.id, icon)} />
        </div>
      </div>
    </Modal>
  );
}

import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { AllOnAllOffButtons } from '../components/strips/AllOnAllOffButtons';
import { ConnectivityBadge } from '../components/strips/ConnectivityBadge';
import { RelayToggle } from '../components/strips/RelayToggle';
import { DeviceIcon, IconPicker } from '../components/strips/IconPicker';
import { Button } from '../components/ui/Button';
import { Card, CardBody } from '../components/ui/Card';
import { Input, Label } from '../components/ui/Input';
import { Modal } from '../components/ui/Modal';
import { formatWatts } from '../lib/formatters';
import { useFleetStore } from '../store/fleetStore';

export default function StripDetailPage() {
  const { stripId } = useParams<{ stripId: string }>();
  const navigate = useNavigate();
  const strips = useFleetStore((s) => s.strips);
  const loaded = useFleetStore((s) => s.loaded);
  const fetchStrips = useFleetStore((s) => s.fetchStrips);
  const setRelay = useFleetStore((s) => s.setRelay);
  const setAllRelays = useFleetStore((s) => s.setAllRelays);
  const renameStrip = useFleetStore((s) => s.renameStrip);
  const renameRelay = useFleetStore((s) => s.renameRelay);
  const setRelayIcon = useFleetStore((s) => s.setRelayIcon);
  const setStripIcon = useFleetStore((s) => s.setStripIcon);
  const removeStrip = useFleetStore((s) => s.removeStrip);

  const [editingStrip, setEditingStrip] = useState(false);
  const [nameDraft, setNameDraft] = useState('');
  const [editingRelay, setEditingRelay] = useState<number | null>(null);
  const [relayNameDraft, setRelayNameDraft] = useState('');
  const [pickingIconFor, setPickingIconFor] = useState<number | 'strip' | null>(null);
  const [confirmRemove, setConfirmRemove] = useState(false);

  useEffect(() => {
    if (!loaded) void fetchStrips();
  }, [loaded, fetchStrips]);

  const strip = strips.find((s) => s.id === stripId);

  if (!strip) {
    return (
      <div className="flex flex-col items-center gap-3 py-16 text-center">
        <p className="text-text-muted">{loaded ? 'Strip not found.' : 'Loading…'}</p>
        {loaded && (
          <Link to="/" className="text-sm text-primary hover:underline">
            Back to dashboard
          </Link>
        )}
      </div>
    );
  }

  const offline = strip.connectivity !== 'online';

  return (
    <div className="flex flex-col gap-5">
      <Card>
        <CardBody className="flex flex-col gap-4">
          <div className="flex items-start justify-between gap-3">
            <div className="flex min-w-0 items-start gap-2">
              <button type="button" onClick={() => setPickingIconFor('strip')} title="Change strip icon" className="rounded-lg p-1 text-3xl hover:bg-surface-hover">
                <DeviceIcon icon={strip.icon} size={28} />
              </button>
              <div className="min-w-0">
                <button
                  type="button"
                  onClick={() => {
                    setNameDraft(strip.name);
                    setEditingStrip(true);
                  }}
                  className="truncate text-left text-xl font-semibold text-text hover:underline"
                >
                  {strip.name}
                </button>
                <p className="text-sm text-text-muted">
                  {strip.model} {strip.ipAddress ? `· ${strip.ipAddress}` : ''} {strip.location ? `· ${strip.location}` : ''}
                </p>
              </div>
            </div>
            <ConnectivityBadge connectivity={strip.connectivity} />
          </div>

          <div className="flex items-end justify-between gap-3">
            <div>
              <p className="text-3xl font-semibold text-text">{formatWatts(strip.totalWatts)}</p>
              <Link to={`/power?stripId=${strip.id}`} className="text-sm text-primary hover:underline">
                View power history
              </Link>
            </div>
            <AllOnAllOffButtons
              onAllOn={() => void setAllRelays(strip.id, 'on')}
              onAllOff={() => void setAllRelays(strip.id, 'off')}
              disabled={offline}
            />
          </div>
        </CardBody>
      </Card>

      <div className="flex flex-col gap-2">
        <h2 className="px-1 text-sm font-semibold text-text-muted">Outlets</h2>
        {strip.relays.map((relay) => (
          <RelayToggle
            key={relay.index}
            relay={relay}
            disabled={offline}
            onToggle={(checked) => void setRelay(strip.id, relay.index, checked ? 'on' : 'off')}
            onRename={() => {
              setEditingRelay(relay.index);
              setRelayNameDraft(relay.name);
            }}
            onIconClick={() => setPickingIconFor(relay.index)}
          />
        ))}
      </div>

      <Card>
        <CardBody className="flex items-center justify-between">
          <div>
            <p className="text-sm font-medium text-text">Remove from fleet</p>
            <p className="text-xs text-text-muted">Stops control and monitoring for this strip.</p>
          </div>
          <Button variant="danger" size="sm" onClick={() => setConfirmRemove(true)}>
            Remove
          </Button>
        </CardBody>
      </Card>

      <Modal
        open={editingStrip}
        onClose={() => setEditingStrip(false)}
        title="Rename strip"
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditingStrip(false)}>
              Cancel
            </Button>
            <Button
              onClick={async () => {
                await renameStrip(strip.id, nameDraft);
                setEditingStrip(false);
              }}
            >
              Save
            </Button>
          </>
        }
      >
        <Label htmlFor="stripNameEdit">Name</Label>
        <Input id="stripNameEdit" value={nameDraft} onChange={(e) => setNameDraft(e.target.value)} autoFocus />
      </Modal>

      <Modal
        open={editingRelay !== null}
        onClose={() => setEditingRelay(null)}
        title="Edit outlet"
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditingRelay(null)}>
              Cancel
            </Button>
            <Button
              onClick={async () => {
                if (editingRelay !== null) {
                  await renameRelay(strip.id, editingRelay, relayNameDraft);
                }
                setEditingRelay(null);
              }}
            >
              Save
            </Button>
          </>
        }
      >
        <Label htmlFor="relayNameEdit">Name</Label>
        <Input id="relayNameEdit" value={relayNameDraft} onChange={(e) => setRelayNameDraft(e.target.value)} autoFocus />
      </Modal>

      <IconPicker
        open={pickingIconFor !== null}
        value={pickingIconFor === 'strip' ? strip.icon : strip.relays.find((relay) => relay.index === pickingIconFor)?.icon ?? 'idle'}
        onClose={() => setPickingIconFor(null)}
        onSelect={async (icon) => {
          if (pickingIconFor === 'strip') await setStripIcon(strip.id, icon);
          else if (typeof pickingIconFor === 'number') await setRelayIcon(strip.id, pickingIconFor, icon);
          setPickingIconFor(null);
        }}
      />

      <Modal
        open={confirmRemove}
        onClose={() => setConfirmRemove(false)}
        title="Remove this strip?"
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirmRemove(false)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              onClick={async () => {
                await removeStrip(strip.id);
                navigate('/');
              }}
            >
              Remove
            </Button>
          </>
        }
      >
        <p className="text-sm text-text-muted">
          "{strip.name}" will be removed from your fleet. It will reappear next time you scan the network.
        </p>
      </Modal>
    </div>
  );
}

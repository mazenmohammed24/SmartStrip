import { useEffect, useRef, useState } from 'react';
import type { DiscoveredStripDTO } from '@shared/types';
import { Button } from '../components/ui/Button';
import { Card, CardBody } from '../components/ui/Card';
import { EmptyState } from '../components/ui/EmptyState';
import { Input, Label } from '../components/ui/Input';
import { Modal } from '../components/ui/Modal';
import { ProvisionCard } from '../components/strips/ProvisionCard';
import { api } from '../lib/apiClient';
import { onWsMessage } from '../lib/wsClient';
import { useFleetStore } from '../store/fleetStore';
import { useMttlStore } from '../store/mttlStore';

interface ScanRecord {
  status: 'pending' | 'done' | 'error';
  percent: number;
  found: number;
  discovered: DiscoveredStripDTO[];
}

export default function ScanPage() {
  return <ScanPanel />;
}

export function ScanPanel({ onAdopted }: { onAdopted?: () => void }) {
  const strips = useFleetStore((s) => s.strips);
  const adoptStrip = useFleetStore((s) => s.adoptStrip);
  const fetchMttlStatus = useMttlStore((s) => s.fetchStatus);
  const mttlStatus = useMttlStore((s) => s.status);
  const mttlUnsupported = useMttlStore((s) => s.unsupported);
  const [scanning, setScanning] = useState(false);
  const [progress, setProgress] = useState(0);
  const [discovered, setDiscovered] = useState<DiscoveredStripDTO[]>([]);
  const [adopting, setAdopting] = useState<DiscoveredStripDTO | null>(null);
  const [nameDraft, setNameDraft] = useState('');
  const [locationDraft, setLocationDraft] = useState('');
  const [saving, setSaving] = useState(false);
  const scanIdRef = useRef<string | null>(null);

  useEffect(() => {
    void fetchMttlStatus();
  }, [fetchMttlStatus]);

  useEffect(
    () =>
      onWsMessage((msg) => {
        if (msg.type === 'scan-progress' && msg.scanId === scanIdRef.current) {
          setProgress(msg.percent);
        }
        if (msg.type === 'scan-result' && msg.scanId === scanIdRef.current) {
          setDiscovered(msg.discovered);
          setScanning(false);
        }
      }),
    [],
  );

  const startScan = async () => {
    setScanning(true);
    setProgress(0);
    setDiscovered([]);
    const { scanId } = await api.post<{ scanId: string }>('/strips/scan');
    scanIdRef.current = scanId;

    const poll = async () => {
      if (scanIdRef.current !== scanId) return;
      const record = await api.get<ScanRecord>(`/strips/scan/${scanId}`);
      setProgress(record.percent);
      if (record.status === 'done') {
        setDiscovered(record.discovered);
        setScanning(false);
      } else if (record.status === 'error') {
        setScanning(false);
      } else {
        setTimeout(() => void poll(), 1000);
      }
    };
    setTimeout(() => void poll(), 1500);
  };

  const alreadyAdopted = new Set(strips.map((s) => s.driverStripId));

  const openAdopt = (d: DiscoveredStripDTO) => {
    setAdopting(d);
    setNameDraft(d.suggestedName);
    setLocationDraft('');
  };

  const confirmAdopt = async () => {
    if (!adopting) return;
    setSaving(true);
    try {
      await adoptStrip({ ...adopting, name: nameDraft, location: locationDraft || null });
      setAdopting(null);
      onAdopted?.();
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardBody className="flex flex-col items-center gap-3 py-8 text-center">
          <span className="text-4xl">📡</span>
          <p className="max-w-sm text-sm text-text-muted">
            {mttlStatus
              ? 'A strip appears here once it has dialled this server. Nothing is broadcast to scan for, so run a scan to pick up any strip that has reconnected.'
              : 'Scan your local network for smart strips broadcasting nearby.'}
          </p>
          <Button onClick={() => void startScan()} loading={scanning}>
            {scanning ? 'Scanning…' : 'Scan for strips'}
          </Button>
          {scanning && (
            <div className="h-2 w-full max-w-xs overflow-hidden rounded-full bg-surface-alt">
              <div className="h-full bg-primary transition-all" style={{ width: `${progress}%` }} />
            </div>
          )}
        </CardBody>
      </Card>

      {!scanning && discovered.length === 0 && (
        <EmptyState
          icon={<span>🔍</span>}
          title="No strip has checked in yet"
          description={
            mttlStatus
              ? 'Provision a strip below. Once it joins your Wi-Fi it will dial this server and show up here within a minute.'
              : 'Run a scan to discover strips on your network.'
          }
        />
      )}

      {/* Only meaningful for the direct MTTL-W01 driver, so it stays hidden otherwise. */}
      {mttlStatus && !mttlUnsupported && <ProvisionCard />}

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {discovered.map((d) => {
          const adopted = alreadyAdopted.has(d.driverStripId);
          return (
            <Card key={d.driverStripId}>
              <CardBody className="flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate font-medium text-text">{d.suggestedName}</p>
                  <p className="truncate text-xs text-text-muted">
                    {d.model} · {d.ipAddress ?? 'address unknown'} · {d.relayCount} outlets
                    {d.signalStrength !== undefined ? ` · signal ${d.signalStrength}%` : ''}
                  </p>
                </div>
                <Button size="sm" variant={adopted ? 'secondary' : 'primary'} disabled={adopted} onClick={() => openAdopt(d)}>
                  {adopted ? 'Added' : 'Add'}
                </Button>
              </CardBody>
            </Card>
          );
        })}
      </div>

      <Modal
        open={!!adopting}
        onClose={() => setAdopting(null)}
        title="Add to fleet"
        footer={
          <>
            <Button variant="secondary" onClick={() => setAdopting(null)}>
              Cancel
            </Button>
            <Button onClick={() => void confirmAdopt()} loading={saving}>
              Add strip
            </Button>
          </>
        }
      >
        <div className="flex flex-col gap-3">
          <div>
            <Label htmlFor="stripName">Name</Label>
            <Input id="stripName" value={nameDraft} onChange={(e) => setNameDraft(e.target.value)} />
          </div>
          <div>
            <Label htmlFor="stripLocation">Location (optional)</Label>
            <Input
              id="stripLocation"
              value={locationDraft}
              onChange={(e) => setLocationDraft(e.target.value)}
              placeholder="e.g. Kitchen counter"
            />
          </div>
        </div>
      </Modal>
    </div>
  );
}

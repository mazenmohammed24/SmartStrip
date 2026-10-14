import { useEffect, useState } from 'react';
import { Button } from '../components/ui/Button';
import { Card, CardBody } from '../components/ui/Card';
import { Input, Label, Select } from '../components/ui/Input';
import { api } from '../lib/apiClient';
import { useSettingsStore } from '../store/settingsStore';

const ACCENT_PRESETS = ['#0f766e', '#16803c', '#176f91', '#7c3aed', '#c2410c', '#be123c'];

export default function SettingsPage() {
  const settings = useSettingsStore((s) => s.settings);
  const fetchSettings = useSettingsStore((s) => s.fetchSettings);
  const updateSettings = useSettingsStore((s) => s.updateSettings);

  const [pushTestMsg, setPushTestMsg] = useState<string | null>(null);

  useEffect(() => {
    void fetchSettings();
  }, [fetchSettings]);

  if (!settings) return null;

const sendTestPush = async () => {
    setPushTestMsg(null);
    try {
      await api.post('/push/test');
      setPushTestMsg('Test push sent.');
    } catch (err) {
      setPushTestMsg(err instanceof Error ? err.message : 'Failed to send test push');
    }
  };

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardBody className="flex flex-col gap-4">
          <h2 className="text-sm font-semibold text-text">Appearance</h2>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label htmlFor="theme">Theme</Label>
              <Select id="theme" value={settings.theme} onChange={(e) => void updateSettings({ theme: e.target.value as typeof settings.theme })}>
                <option value="system">System</option>
                <option value="light">Light</option>
                <option value="dark">Dark</option>
              </Select>
            </div>
            <div>
              <Label htmlFor="timeFormat">Time format</Label>
              <Select
                id="timeFormat"
                value={settings.timeFormat}
                onChange={(e) => void updateSettings({ timeFormat: e.target.value as typeof settings.timeFormat })}
              >
                <option value="24h">24-hour</option>
                <option value="12h">12-hour</option>
              </Select>
            </div>
          </div>
          <div>
            <Label htmlFor="accentColor">Main color</Label>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              {ACCENT_PRESETS.map((color) => (
                <button
                  key={color}
                  type="button"
                  onClick={() => void updateSettings({ accentColor: color })}
                  className={`h-9 w-9 rounded-full border-2 ${settings.accentColor === color ? 'border-text' : 'border-border'}`}
                  style={{ backgroundColor: color }}
                  aria-label={`Use ${color} as main color`}
                />
              ))}
              <input
                id="accentColor"
                type="color"
                value={settings.accentColor}
                onChange={(e) => void updateSettings({ accentColor: e.target.value })}
                className="h-9 w-12 rounded-lg border border-border bg-surface p-1"
              />
            </div>
          </div>
        </CardBody>
      </Card>

      <Card>
        <CardBody className="flex flex-col gap-4">
          <h2 className="text-sm font-semibold text-text">Energy &amp; cost</h2>
          <div className="grid grid-cols-3 gap-3">
            <div>
              <Label htmlFor="energyUnit">Unit</Label>
              <Select
                id="energyUnit"
                value={settings.energyUnit}
                onChange={(e) => void updateSettings({ energyUnit: e.target.value as typeof settings.energyUnit })}
              >
                <option value="kWh">kWh</option>
                <option value="Wh">Wh</option>
              </Select>
            </div>
            <div>
              <Label htmlFor="currency">Currency</Label>
              <Input id="currency" value={settings.currency} onChange={(e) => void updateSettings({ currency: e.target.value.toUpperCase() })} maxLength={3} />
            </div>
            <div>
              <Label htmlFor="rate">Rate / kWh</Label>
              <Input
                id="rate"
                type="number"
                step="0.01"
                min="0"
                value={settings.ratePerKwh}
                onChange={(e) => void updateSettings({ ratePerKwh: Number(e.target.value) })}
              />
            </div>
          </div>
          <div>
            <Label htmlFor="overload">Overload threshold (W)</Label>
            <Input
              id="overload"
              type="number"
              min="100"
              step="50"
              value={settings.overloadThresholdWatts}
              onChange={(e) => void updateSettings({ overloadThresholdWatts: Number(e.target.value) })}
              className="max-w-[160px]"
            />
          </div>
        </CardBody>
      </Card>

      <Card>
        <CardBody className="flex flex-col gap-3">
          <h2 className="text-sm font-semibold text-text">Notifications</h2>
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm text-text-muted">Send a test push notification to verify setup.</p>
            <Button size="sm" variant="secondary" onClick={() => void sendTestPush()}>
              Send test
            </Button>
          </div>
          {pushTestMsg && <p className="text-xs text-text-muted">{pushTestMsg}</p>}
        </CardBody>
      </Card>



<Card>
        <CardBody>
          <h2 className="mb-1 text-sm font-semibold text-text">About</h2>
          <p className="text-xs text-text-muted">SmartStrip Portal · driver: mock (simulated strips) · MTTL-W01 compatible layout</p>
        </CardBody>
      </Card>
    </div>
  );
}

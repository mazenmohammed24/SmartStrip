import { type FormEvent, useState } from 'react';
import { Button } from '../ui/Button';
import { Card, CardBody, CardHeader } from '../ui/Card';
import { Input, Label } from '../ui/Input';
import { useMttlStore } from '../../store/mttlStore';

/**
 * Provisioning form for a real MTTL-W01 strip.
 *
 * The strip stores one server address and one set of Wi-Fi credentials, written
 * through its own setup access point. This has to be run from a machine joined to
 * the strip's "TONLY_TAP_*" network, so the instructions are part of the form
 * rather than hidden in a README.
 */
export function ProvisionCard() {
  const status = useMttlStore((s) => s.status);
  const provisioning = useMttlStore((s) => s.provisioning);
  const error = useMttlStore((s) => s.error);
  const message = useMttlStore((s) => s.provisionMessage);
  const provision = useMttlStore((s) => s.provision);

  // Left empty to accept the server's suggestion. The fallback chain lives server-side
  // so the form default can never disagree with what the server would actually tell
  // a strip to dial.
  const [ipOverride, setIpOverride] = useState('');
  const ip = ipOverride || status?.advertisedIp || '';
  const [ssid, setSsid] = useState('');
  const [password, setPassword] = useState('');

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    try {
      await provision({ serverIp: ip.trim(), ssid: ssid.trim(), password });
    } catch {
      // Surfaced through the store's error field.
    }
  };

  return (
    <Card>
      <CardHeader>
        <h2 className="text-sm font-semibold text-text">Add a new strip</h2>
      </CardHeader>
      <CardBody>
        <p className="rounded-xl bg-warning-bg px-3 py-2 text-xs text-warning">
          This form provisions from the machine running the server, which must be joined to the strip&apos;s setup network. If the
          server is hosted elsewhere, run <code className="text-text">npm run mttl-provision</code> from a laptop joined to
          <code className="text-text"> TONLY_TAP_*</code> instead.
        </p>

        <ol className="mb-4 flex list-decimal flex-col gap-1 pl-5 text-sm text-text-muted">
          <li>Hold the strip's main button for about 10 seconds, until the LED blinks fast.</li>
          <li>
            Join the Wi-Fi network <code className="text-text">TONLY_TAP_XXXXXXX</code> from the machine running this server (password:{' '}
            <code className="text-text">LGU_XXXXXXX</code>, the same 7 characters as in the name).
          </li>
          <li>Fill in your home Wi-Fi below, then submit. Reconnect the machine afterwards.</li>
        </ol>

        <form onSubmit={submit} className="flex flex-col gap-3">
          <div>
            <Label htmlFor="provisionIp">This server&apos;s address for the strip to dial</Label>
            <Input
              id="provisionIp"
              value={ip}
              onChange={(e) => setIpOverride(e.target.value)}
              placeholder="192.168.1.10 or your public IPv4"
              required
            />
            <p className="mt-1 text-xs text-text-muted">
              The strip connects out to this address on TCP {status?.devicePort ?? 10086}. On a home network use its LAN IP; if the
              server is hosted remotely, use the public IP. It must be the address the strip can actually reach.
            </p>
          </div>
          <div>
            <Label htmlFor="provisionSsid">Home Wi-Fi name (SSID)</Label>
            <Input id="provisionSsid" value={ssid} onChange={(e) => setSsid(e.target.value)} required />
            <p className="mt-1 text-xs text-text-muted">No colon (:) in the name - the strip&apos;s protocol cannot carry one.</p>
          </div>
          <div>
            <Label htmlFor="provisionPassword">Home Wi-Fi password</Label>
            <Input id="provisionPassword" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </div>

          {error && <p className="text-sm text-danger">{error}</p>}
          {message && <p className="text-sm text-success">{message}</p>}

          <Button type="submit" loading={provisioning} className="self-start">
            Provision strip
          </Button>
          <p className="text-xs text-text-muted">
            The strip keeps exactly one server address. Re-provision it to move it to a different host or network.
          </p>
        </form>
      </CardBody>
    </Card>
  );
}

# SmartStrip Portal

A responsive web portal for controlling smart power strips (LG U+ / Jinheung **MTTL-W01**,
4-relay strip) with the feature set of the Voltra app: fleet management, per-outlet control,
live power monitoring, activity history, automations/schedules, and notifications (in-app +
Web Push).

Real hardware is supported two ways: directly, over the strip's own TCP protocol
(`DRIVER=mttl`, no cloud and no port forwarding), or through Voltra's cloud API
(`DRIVER=voltra`). `DRIVER=mock` simulates strips for UI work.

## Drivers

The project ships three drivers, selected with the `DRIVER` env var:

| `DRIVER`  | What it talks to                                                    |
| --------- | ------------------------------------------------------------------- |
| `mock`    | Nothing. Simulated strips, for UI work with no hardware.             |
| `mttl`    | Real LG U+ / Jinheung **MTTL-W01** strips, over the strip's own protocol. |
| `voltra`  | TONLY MTTL-W01 strips through Voltra's cloud API.                    |

### `mttl` — direct control, no cloud

This driver speaks the strip's own `up:` text protocol over TCP, the same one
[powerk](https://github.com/ahmedtohamy1/powerk) uses. No cloud account, no paid
app, no MQTT, no Home Assistant, no firmware flashing, and **no port forwarding on
your home router**.

The reason is the hardware's design: provisioning writes *your server's IP* into the
strip's flash, and from then on the strip dials **out** to that address on TCP 10086
and holds the session open. This server answers it.

```
web UI / app ──► http://SERVER:4001 ──► SmartStrip server ◄── TCP 10086 ── strip
```

Because the strip initiates every connection, the same code works unchanged on a
machine inside your home network and on a public VPS, and the two exposure profiles
differ only in which ports you open:

- **10086/tcp** — the strip's own protocol. Safe to leave reachable from the
  internet: control commands only ever travel server → strip, so a stranger who
  finds this port cannot switch anything. It does need to accept the whole internet
  if your home IP is dynamic.
- **4001/tcp** — the web UI and API. This is the port that can actually switch your
  outlets, so it must not be reachable from anywhere you do not trust: there is no
  login, and anything that can reach this port can control the strips.

```env
DRIVER=mttl
# Set when the strip must reach a specific address (a VPS public IP) and automatic
# LAN-IP detection would guess wrong. Also shown as the default in the provisioning form.
MTTL_ADVERTISE_IP=203.0.113.10
```

#### Provisioning a strip (once, per strip)

Provisioning tells the strip which Wi-Fi to join and which server to dial. It happens
over the strip's own setup access point, so it is done from a machine joined to the
strip's `TONLY_TAP_XXXXXXX` network (password `LGU_XXXXXXX`, the same 7 characters as
in the network name; no internet on that network is normal).

1. Start the server and note the address it prints.
2. Hold the strip's main button ~10 s, until the LED blinks fast.
3. Join `TONLY_TAP_XXXXXXX` from the machine running the command.
4. Provision it, from a machine joined to that `TONLY_TAP_*` network:

   ```bash
   npm run mttl-provision --prefix server -- --ip 192.168.1.10 --ssid "YOURWIFI" --password "YOURWIFIPW"
   ```

   The web UI also has a provisioning form, but it runs **on the server host**, so it
   only works when the server itself is on the strip's setup network. If the server is
   hosted on a VPS, use the CLI from a laptop that has joined `TONLY_TAP_*` instead.

5. Reconnect that machine to your normal network. The strip connects within a minute
   and shows up in the UI, ready to adopt.

The strip stores exactly **one** server address, so re-provision it to move it to a
different host or network. The protocol has no escaping, so an SSID or password
containing `:` is rejected rather than silently mangled.

Windows firewall, if the strip cannot reach the server:

```bat
netsh advfirewall firewall add rule name="SmartStrip device" dir=in action=allow protocol=TCP localport=10086
netsh advfirewall firewall add rule name="SmartStrip web" dir=in action=allow protocol=TCP localport=4001
```

Give the server machine a **fixed IP or DHCP reservation** if it is on your LAN — the
strip remembers the address you provisioned.

#### Verifying without hardware

```bash
npm run mttl-selftest --prefix server
```

Runs the parser and a full command round-trip against a stand-in strip that implements
the same protocol, then exits. No hardware and no elevated ports needed.

### `voltra` — control through Voltra's cloud

To enable the real driver, set these values in `server/.env`:

```env
DRIVER=voltra
VOLTRA_ACCESS_TOKEN=your-voltra-access-token
# Optional; defaults to https://toi.ommeq.com
VOLTRA_BASE_URL=https://toi.ommeq.com
```

Keep the access token out of source control. The driver discovers the strips visible to that Voltra
account and sends each outlet command to the official authenticated API.

### Automatic Voltra Token Refresh

Since Voltra access tokens expire after 24 hours, store both the Voltra access token and refresh token to enable automatic token refresh. The server will:
1. Detect when a token expires (401/403 response)
2. Automatically refresh it using your stored refresh token
3. Retry requests with the new token

**To set up automatic token refresh:**

Use the Voltra remote API card in Settings, or make a request to `POST /api/settings/voltra-tokens`:

```bash
curl -X POST http://localhost:4001/api/settings/voltra-tokens \
  -H "Content-Type: application/json" \
  -d '{
    "accessToken": "your-voltra-access-token",
    "refreshToken": "your-voltra-refresh-token"
  }'
```

Once stored, the token manager will:
- Refresh the token automatically 30 minutes before it expires
- Retry immediately if a 401 occurs
- Log all refresh attempts for troubleshooting

The tokens are stored in `server/data/main.json` (same database as other app data). **For production**, encrypt them using your secret key.

## Authentication

There is **none**. Every API route is open to anything that can reach the port. Keep
4001 on localhost, behind the Local Server Manager's approval gate, or on a trusted
network — do not expose it to the internet.

## Architecture

```
shared/types.ts   - DTOs shared between server and web (type-only, erased at build)
server/           - Express + WebSocket + lowdb (pure JS, no native compilation)
web/              - React + Vite + TypeScript + Tailwind + Zustand + Recharts
```

### The hardware seam

`server/src/drivers/StripDriver.ts` defines the interface every strip driver implements
(`scanForStrips`, `adoptStrip`, `setRelay`, `setAllRelays`, `getStatus`, plus an event emitter for
status/connectivity/relay-changed/overload). `server/src/drivers/DriverRegistry.ts` selects the
implementation via the `DRIVER` env var (defaults to `mock`). No route or service talks to
hardware directly - only through this interface. To add another hardware target, implement
`StripDriver` and register it there; nothing else needs to change.

### Driver-specific endpoints

A driver may expose extra routes for things its hardware reports that the generic
`StripDTO` has no room for. The MTTL driver's live voltage, current, Wi-Fi RSSI, per-outlet
energy and temperature are served from `/api/mttl/*` and rendered by the telemetry card on
the strip detail page. These routes answer `409` when another driver is active, so the UI can
show an honest "not applicable" state instead of an empty panel.

## Getting started

```bash
npm run install:all   # installs server + web dependencies
npm run dev           # runs server (http://localhost:4001) + web (http://localhost:5173)
```

Open the web app when it starts - there is no login step.

Note: the server defaults to port **4001**, not 3001 - on a machine already running something
else on 3001 (a router dashboard, another dev server, etc.), 4001 avoids the collision. Change it
via `PORT` in `server/.env` if needed.

## Push notifications (optional)

Web Push needs a VAPID key pair. Generate one and put it in `server/.env`:

```bash
npm run generate-vapid-keys --prefix server
```

Without this, everything else works normally - only the "Enable" button on the Notifications page
will report that push isn't configured.

## Production

```bash
npm run build
npm start
```

In production, the Express server serves the built web app directly (single process/port),
suitable for running continuously on a home-LAN machine or Raspberry Pi.

### Hosting it somewhere you reach from anywhere

Nothing about the app is LAN-bound, so hosting it on a VPS with a public IP is a
straightforward swap: copy the repo, `npm run install:all && npm run build`, then run it
under a process manager. Two things matter for the `mttl` driver specifically.

The strip must be able to reach the server, so **10086/tcp has to be open to the internet**,
not just to your own address — the strip dials from a dynamic home IP. The web port
(4001) is the one that can actually switch your outlets, so treat it accordingly:

- Bind 4001 to localhost only, or put it behind a reverse proxy that requires
  authentication. There is no login of its own, so anything that reaches the port can
  switch your outlets.
- Narrow 4001 to your own IP where the provider allows it, or leave it closed and reach the UI
  through a VPN such as Tailscale. With Tailscale on both ends you can keep 4001 bound to the
  tunnel interface and nothing is exposed publicly at all.

As a systemd unit on Ubuntu:

```ini
[Unit]
Description=SmartStrip
After=network-online.target
Wants=network-online.target

[Service]
WorkingDirectory=/home/ubuntu/SmartStrip
ExecStart=/usr/bin/npm start
Restart=always
RestartSec=3
Environment=NODE_ENV=production
User=ubuntu

[Install]
WantedBy=multi-user.target
```

Run `ufw allow 10086/tcp` and open the web port the way you decided above, then re-provision
each strip with `--ip <your-public-IPv4>` (or set `MTTL_ADVERTISE_IP` so the form defaults to
it — but remember the form itself must run on a machine joined to the strip's setup network,
so on a VPS you will use the CLI).

Create `server/.env` before the first start. The unit below does not invent configuration, and
without it `DRIVER` falls back to `mock`:

```bash
cp server/.env.example server/.env
# then set at minimum:
#   DRIVER=mttl
#   MTTL_ADVERTISE_IP=<your-public-IPv4>
```

Two details to be aware of when moving an existing install to a different driver: rows in
`server/data/main.json` record the `driverName` they were adopted under, and the MTTL driver
only adopts rows whose `driverName` is `mttl`. Strips adopted under `voltra` will therefore
show as unreachable and their controls will not work until you re-adopt them, which is the
correct outcome rather than a silent mis-address — but it is a visible change, so re-scan and
re-add them rather than expecting the old rows to keep working.

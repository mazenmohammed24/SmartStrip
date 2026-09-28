# SmartStrip Portal

A responsive web portal for controlling smart power strips (modeled on the LG MTTL-W01, 4-relay
strip) with the feature set of the Voltra app: fleet management, per-outlet control, network
discovery, live power monitoring, activity history, automations/schedules, and notifications
(in-app + Web Push).

## Drivers

The project defaults to **simulated** strips. It can also control already-registered TONLY
MTTL-W01 strips through Voltra's cloud API, so it does not need to automate a browser or use an
undocumented LAN protocol. Voltra access is still subject to the account, subscription, child-lock,
and command-rate limits enforced by the service.

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
  -H "Cookie: auth=<your-jwt-cookie>" \
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

The system uses **username/password** authentication. Admin account is set up on first login.

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
hardware directly - only through this interface. To add real hardware support later (e.g. once a
local protocol or an ESP-01 I2C-bridge is available), implement `StripDriver` and register it
here; nothing else needs to change.

## Getting started

```bash
npm run install:all   # installs server + web dependencies
npm run dev           # runs server (http://localhost:4001) + web (http://localhost:5173)
```

On first run, visit the web app and complete the one-time admin account setup.

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

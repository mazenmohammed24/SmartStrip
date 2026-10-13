# Portal Development Instructions

## Server Lifecycle — Always Use the ServerManager

All process lifecycle actions for this portal **must** go through the
[ServerManager](../ServerManager) API. Never start, stop, restart, or rebuild
this portal manually (e.g. running `npm start` / `node server.js` directly, or
killing the process by hand).

### Why

The ServerManager is the single front-end for every portal. It:
- Owns the process (spawns, supervises, auto-restarts on crash)
- Proxies every portal under one origin (`http://localhost:3000/<PortalName>`)
- Tracks status, uptime, and logs shown to users
- Issues CSRF tokens for its own API

Bypassing it leaves the dashboard out of sync and can cause port conflicts,
double-processes, or stale builds being served.

### How to perform each action

| Action   | ServerManager API endpoint                         |
|----------|----------------------------------------------------|
| Start    | `POST /manager/api/apps/<PortalName>/start`        |
| Stop     | `POST /manager/api/apps/<PortalName>/stop`         |
| Restart  | `POST /manager/api/apps/<PortalName>/restart`      |
| Rebuild  | `POST /manager/api/apps/<PortalName>/rebuild`      |
| Status   | `GET  /manager/api/apps/<PortalName>`              |

All mutating endpoints require an `x-csrf-token` header. Obtain the token from
any preceding GET or from the `csrfToken` field in the previous POST response.

### In-portal UI

Any UI control inside a portal that triggers stop/start/restart/rebuild **must**
call the ServerManager API above — not shell out, not spawn a child process, and
not call a portal-internal endpoint. The topnav `ServerControls` component in
MediaDL is the reference implementation.

### Local development exception

Running `npm run dev` (or equivalent) in a **separate terminal for development
only** is acceptable, but the dev server must use a different port than the one
registered in `apps.js` so it does not conflict with the managed instance. The
ServerManager dashboard should remain the only path to the production/served
instance.

import type { Server as HttpServer } from 'node:http';
import { WebSocketServer } from 'ws';
import { authCookie, verifyToken } from '../auth/jwt.js';
import { createLogger } from '../utils/logger.js';
import { registerBroadcastClients } from './broadcast.js';

const log = createLogger('wsServer');

function parseCookies(header: string | undefined): Record<string, string> {
  const out: Record<string, string> = {};
  if (!header) return out;
  for (const part of header.split(';')) {
    const idx = part.indexOf('=');
    if (idx === -1) continue;
    out[part.slice(0, idx).trim()] = decodeURIComponent(part.slice(idx + 1).trim());
  }
  return out;
}

export function attachWebSocketServer(server: HttpServer): WebSocketServer {
  const wss = new WebSocketServer({
    server,
    path: '/ws',
    verifyClient: (info, callback) => {
      const cookies = parseCookies(info.req.headers.cookie);
      const accessToken = cookies[authCookie.accessTokenName];
      const refreshToken = cookies[authCookie.refreshTokenName];

      // Try access token first
      let payload = accessToken ? verifyToken(accessToken) : null;

      // Fall back to refresh token if access token invalid/missing
      if (!payload) {
        payload = refreshToken ? verifyToken(refreshToken) : null;
      }

      if (!payload) {
        callback(false, 401, 'Unauthorized');
        return;
      }
      callback(true);
    },
  });

  registerBroadcastClients(wss.clients as unknown as Set<{ send(data: string): void; readyState: number }>);

  wss.on('connection', (ws) => {
    log.info('client connected');
    ws.on('close', () => log.info('client disconnected'));
    ws.on('error', (err) => log.error('ws error', err));
  });

  return wss;
}

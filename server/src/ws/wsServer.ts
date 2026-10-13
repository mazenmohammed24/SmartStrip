import type { Server as HttpServer } from 'node:http';
import { WebSocketServer } from 'ws';
import { createLogger } from '../utils/logger.js';
import { registerBroadcastClients } from './broadcast.js';

const log = createLogger('wsServer');

export function attachWebSocketServer(server: HttpServer): WebSocketServer {
  const wss = new WebSocketServer({
    server,
    path: '/ws',
  });

  registerBroadcastClients(wss.clients as unknown as Set<{ send(data: string): void; readyState: number }>);

  wss.on('connection', (ws) => {
    log.info('client connected');
    ws.on('close', () => log.info('client disconnected'));
    ws.on('error', (err) => log.error('ws error', err));
  });

  return wss;
}

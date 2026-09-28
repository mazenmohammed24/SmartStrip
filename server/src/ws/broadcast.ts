import type { ServerWsMessage } from '../../../shared/types.js';
import { createLogger } from '../utils/logger.js';

const log = createLogger('ws:broadcast');

type Client = { send(data: string): void; readyState: number };

let clients: Set<Client> = new Set();

export function registerBroadcastClients(set: Set<Client>): void {
  clients = set;
}

export function broadcast(message: ServerWsMessage): void {
  const payload = JSON.stringify(message);
  let sent = 0;
  for (const client of clients) {
    if (client.readyState === 1 /* OPEN */) {
      client.send(payload);
      sent++;
    }
  }
  log.debug(`broadcast ${message.type} to ${sent} client(s)`);
}

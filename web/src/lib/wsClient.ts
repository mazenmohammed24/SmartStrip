import type { ServerWsMessage } from '@shared/types';

type Listener = (message: ServerWsMessage) => void;

let socket: WebSocket | null = null;
let reconnectAttempt = 0;
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
let manuallyClosed = false;
const listeners = new Set<Listener>();
const statusListeners = new Set<(status: ConnectionStatus) => void>();

export type ConnectionStatus = 'connecting' | 'open' | 'closed';
let currentStatus: ConnectionStatus = 'closed';

function setStatus(status: ConnectionStatus): void {
  currentStatus = status;
  statusListeners.forEach((l) => l(status));
}

export function getConnectionStatus(): ConnectionStatus {
  return currentStatus;
}

export function onConnectionStatus(listener: (status: ConnectionStatus) => void): () => void {
  statusListeners.add(listener);
  return () => statusListeners.delete(listener);
}

export function connectWs(): void {
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) return;
  manuallyClosed = false;
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  // BASE_URL is '/' normally and '/SmartStrip/' when hosted at a sub-path.
  const base = import.meta.env.BASE_URL.replace(/\/$/, '');
  setStatus('connecting');
  socket = new WebSocket(`${protocol}//${window.location.host}${base}/ws`);

  socket.addEventListener('open', () => {
    reconnectAttempt = 0;
    setStatus('open');
  });

  socket.addEventListener('message', (event) => {
    try {
      const message = JSON.parse(event.data) as ServerWsMessage;
      listeners.forEach((l) => l(message));
    } catch {
      // ignore malformed frames
    }
  });

  socket.addEventListener('close', () => {
    setStatus('closed');
    if (!manuallyClosed) scheduleReconnect();
  });

  socket.addEventListener('error', () => {
    socket?.close();
  });
}

function scheduleReconnect(): void {
  if (reconnectTimer) return;
  const delay = Math.min(1000 * 2 ** reconnectAttempt, 15000);
  reconnectAttempt++;
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    connectWs();
  }, delay);
}

export function disconnectWs(): void {
  manuallyClosed = true;
  socket?.close();
}

export function onWsMessage(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

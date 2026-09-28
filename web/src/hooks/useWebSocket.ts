import { useEffect } from 'react';
import { connectWs, disconnectWs } from '../lib/wsClient';
import { wireWsDispatcher } from '../lib/wsDispatcher';
import { useAuthStore } from '../store/authStore';

export function useWebSocketConnection(): void {
  const status = useAuthStore((s) => s.status);

  useEffect(() => {
    if (status !== 'authenticated') return;
    connectWs();
    const unsubscribe = wireWsDispatcher();
    return () => {
      unsubscribe();
      disconnectWs();
    };
  }, [status]);
}

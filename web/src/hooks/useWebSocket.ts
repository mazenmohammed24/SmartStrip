import { useEffect } from 'react';
import { connectWs, disconnectWs } from '../lib/wsClient';
import { wireWsDispatcher } from '../lib/wsDispatcher';

export function useWebSocketConnection(): void {
  useEffect(() => {
    connectWs();
    const unsubscribe = wireWsDispatcher();
    return () => {
      unsubscribe();
      disconnectWs();
    };
  }, []);
}

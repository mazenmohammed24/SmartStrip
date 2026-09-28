import { useCallback } from 'react';
import { useFleetStore } from '../store/fleetStore';

/**
 * Hook to resolve strip names from driverStripId (MAC address).
 * This allows notifications and events to show the current strip name
 * even if the strip has been renamed since the event was created.
 */
export function useStripNameResolver() {
    const strips = useFleetStore((s) => s.strips);

    return useCallback(
        (driverStripId: string | null): string | null => {
            if (!driverStripId) return null;
            const strip = strips.find((s) => s.driverStripId === driverStripId);
            return strip?.name ?? null;
        },
        [strips],
    );
}

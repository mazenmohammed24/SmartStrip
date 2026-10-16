import { useFleetStore } from '../store/fleetStore';
import { useNotificationStore } from '../store/notificationStore';
import { useRulesStore } from '../store/rulesStore';
import { useSettingsStore } from '../store/settingsStore';
import { onWsMessage } from './wsClient';

/**
 * Wires WebSocket pushes into the always-on-screen global state (fleet + notification bell).
 * Messages only a specific page cares about (scan-progress/result, event-logged, schedule-fired)
 * are consumed directly by that page via its own onWsMessage subscription instead.
 */
export function wireWsDispatcher(): () => void {
  return onWsMessage((message) => {
    const fleet = useFleetStore.getState();
    const notifications = useNotificationStore.getState();

    switch (message.type) {
      case 'relay-state':
        fleet.applyRelayState(message.stripId, message.relayIndex, message.state, message.changedAt);
        break;
      case 'relay-timer':
        fleet.applyRelayTimer(
          message.stripId,
          message.relayIndex,
          message.restoreAt ? { mode: message.mode, state: message.state, restoreState: message.restoreState, restoreAt: message.restoreAt } : null,
        );
        break;
      case 'power-tick':
        fleet.applyPowerTick(message.stripId, message.relays, message.totalWatts, message.telemetry);
        break;
      case 'strip-connectivity':
        fleet.applyConnectivity(message.stripId, message.connectivity);
        break;
      case 'strip-added':
        fleet.applyStripAdded(message.strip);
        break;
      case 'strip-updated':
        fleet.applyStripUpdated(message.strip);
        break;
      case 'strip-removed':
        fleet.applyStripRemoved(message.stripId);
        break;
      case 'rules-changed':
        void useRulesStore.getState().fetchRules();
        break;
      case 'settings-changed':
        useSettingsStore.setState({ settings: message.settings });
        break;
      case 'notification':
        notifications.addNotification(message.notification);
        break;
      default:
        break;
    }
  });
}

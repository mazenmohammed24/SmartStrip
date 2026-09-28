import { useEffect, useState } from 'react';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { EmptyState } from '../components/ui/EmptyState';
import { formatRelativeTime } from '../lib/formatters';
import { getPushSubscriptionState, subscribeToPush, unsubscribeFromPush } from '../lib/pushClient';
import { useStripNameResolver } from '../hooks/useStripNameResolver';
import { useNotificationStore } from '../store/notificationStore';

type Severity = 'info' | 'warning' | 'critical';
const SEVERITY_TONE: Record<Severity, 'info' | 'warning' | 'danger'> = { info: 'info', warning: 'warning', critical: 'danger' };

export default function NotificationsPage() {
  const notifications = useNotificationStore((s) => s.notifications);
  const fetchNotifications = useNotificationStore((s) => s.fetchNotifications);
  const markRead = useNotificationStore((s) => s.markRead);
  const markAllRead = useNotificationStore((s) => s.markAllRead);
  const remove = useNotificationStore((s) => s.remove);
  const getStripName = useStripNameResolver();
  const [pushState, setPushState] = useState<'unsupported' | 'subscribed' | 'unsubscribed' | 'loading'>('loading');
  const [pushError, setPushError] = useState<string | null>(null);

  useEffect(() => {
    void fetchNotifications();
  }, [fetchNotifications]);

  useEffect(() => {
    void getPushSubscriptionState().then(setPushState);
  }, []);

  const togglePush = async () => {
    setPushError(null);
    try {
      if (pushState === 'subscribed') {
        await unsubscribeFromPush();
        setPushState('unsubscribed');
      } else {
        await subscribeToPush();
        setPushState('subscribed');
      }
    } catch (err) {
      setPushError(err instanceof Error ? err.message : 'Failed to update push subscription');
    }
  };

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-3 rounded-2xl border border-border bg-surface p-4">
        <div className="min-w-0">
          <p className="text-sm font-medium text-text">Push notifications</p>
          <p className="text-xs text-text-muted">Get OS notifications even when this app is closed.</p>
          {pushError && <p className="mt-1 text-xs text-danger">{pushError}</p>}
        </div>
        {pushState !== 'unsupported' && (
          <Button size="sm" variant={pushState === 'subscribed' ? 'secondary' : 'primary'} onClick={() => void togglePush()} loading={pushState === 'loading'}>
            {pushState === 'subscribed' ? 'Disable' : 'Enable'}
          </Button>
        )}
      </div>

      {notifications.length > 0 && (
        <div className="flex justify-end">
          <Button size="sm" variant="ghost" onClick={() => void markAllRead()}>
            Mark all read
          </Button>
        </div>
      )}

      {notifications.length === 0 && (
        <EmptyState icon={<span>🔔</span>} title="No notifications yet" description="Automations, overloads, and connectivity changes will appear here." />
      )}

      <div className="flex flex-col gap-2">
        {notifications.map((n) => {
          // Resolve current strip name from driverStripId (handles strip renames)
          const currentStripName = getStripName(n.relatedStripMac);
          // Replace strip name in title if it was renamed
          let displayTitle = n.title;
          if (currentStripName && (n.title.includes('offline') || n.title.includes('online') || n.title.includes('Overload'))) {
            // For connectivity and overload notifications, update with current name
            if (n.title.includes('offline')) {
              displayTitle = `${currentStripName} went offline`;
            } else if (n.title.includes('back online')) {
              displayTitle = `${currentStripName} is back online`;
            } else if (n.title.includes('Overload')) {
              displayTitle = `Overload protection triggered - ${currentStripName}`;
            }
          }

          return (
            <div
              key={n.id}
              onClick={() => !n.readAt && void markRead(n.id)}
              className={`flex items-start gap-3 rounded-2xl border border-border p-4 ${n.readAt ? 'bg-surface' : 'bg-primary/5'}`}
            >
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="truncate text-sm font-medium text-text">{displayTitle}</p>
                  <Badge tone={SEVERITY_TONE[n.severity]}>{n.severity}</Badge>
                </div>
                <p className="text-sm text-text-muted">{n.body}</p>
                <p className="mt-1 text-xs text-text-muted">{formatRelativeTime(n.createdAt)}</p>
              </div>
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  void remove(n.id);
                }}
                className="text-text-muted hover:text-danger"
                aria-label="Delete notification"
              >
                ✕
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}

import { Bell } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { formatRelativeTime } from '../../lib/formatters';
import { useNotificationStore } from '../../store/notificationStore';

export function NotificationBell() {
  const unread = useNotificationStore((s) => s.unreadCount());
  const allNotifications = useNotificationStore((s) => s.notifications);
  const loaded = useNotificationStore((s) => s.loaded);
  const fetchNotifications = useNotificationStore((s) => s.fetchNotifications);
  const markAllRead = useNotificationStore((s) => s.markAllRead);
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const notifications = allNotifications.slice(0, 6);

  useEffect(() => {
    if (open && !loaded) void fetchNotifications();
  }, [open, loaded, fetchNotifications]);

  useEffect(() => {
    if (!open) return;
    const onPointer = (event: PointerEvent) => {
      if (!ref.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('pointerdown', onPointer);
    return () => document.removeEventListener('pointerdown', onPointer);
  }, [open]);

  return (
    <div ref={ref} className="relative">
      <button type="button" onClick={() => setOpen((value) => !value)} className="relative rounded-full p-2 text-text hover:bg-surface-hover" aria-label="Notifications">
        <Bell size={21} strokeWidth={1.8} />
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-bold text-white">
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>
      {open && (
        <div className="absolute right-0 top-11 z-50 w-[min(22rem,calc(100vw-2rem))] overflow-hidden rounded-xl border border-border bg-surface shadow-[0_18px_50px_rgb(24_32_27/0.18)]">
          <div className="flex items-center justify-between border-b border-border px-4 py-3">
            <div>
              <p className="text-sm font-semibold text-text">Notifications</p>
              <p className="text-xs text-text-muted">{unread} unread</p>
            </div>
            <button type="button" onClick={() => void markAllRead()} className="text-xs font-medium text-primary hover:underline">Mark all read</button>
          </div>
          <div className="max-h-80 overflow-y-auto">
            {notifications.length === 0 ? (
              <p className="px-4 py-6 text-sm text-text-muted">No notifications yet.</p>
            ) : (
              notifications.map((notification) => (
                <div key={notification.id} className={`border-b border-border px-4 py-3 last:border-b-0 ${notification.readAt ? '' : 'bg-success-bg/45'}`}>
                  <p className="text-sm font-medium text-text">{notification.title}</p>
                  <p className="mt-0.5 line-clamp-2 text-xs text-text-muted">{notification.body}</p>
                  <p className="mt-1 text-[11px] text-text-muted">{formatRelativeTime(notification.createdAt)}</p>
                </div>
              ))
            )}
          </div>
          <Link to="/notifications" onClick={() => setOpen(false)} className="block border-t border-border px-4 py-3 text-center text-sm font-medium text-primary hover:bg-surface-hover">
            Open notification center
          </Link>
        </div>
      )}
    </div>
  );
}

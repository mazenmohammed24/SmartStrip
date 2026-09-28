import { MoreHorizontal } from 'lucide-react';
import { NavLink } from 'react-router-dom';
import { useNotificationStore } from '../../store/notificationStore';
import { useUiStore } from '../../store/uiStore';
import { PRIMARY_NAV } from './navConfig';

export function BottomTabBar() {
  const setMoreSheetOpen = useUiStore((s) => s.setMoreSheetOpen);
  const unread = useNotificationStore((s) => s.unreadCount());

  return (
    <nav className="pb-safe fixed inset-x-0 bottom-0 z-30 flex border-t border-border bg-surface">
      {PRIMARY_NAV.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.to === '/'}
          className={({ isActive }) =>
            `relative flex flex-1 flex-col items-center gap-0.5 py-2 text-[11px] ${isActive ? 'text-primary' : 'text-text-muted'}`
          }
        >
          <item.Icon size={21} strokeWidth={1.8} />
          {item.label}
          {item.to === '/notifications' && unread > 0 && <span className="absolute right-[24%] top-1 h-2 w-2 rounded-full bg-danger" />}
        </NavLink>
      ))}
      <button
        type="button"
        onClick={() => setMoreSheetOpen(true)}
        className="flex flex-1 flex-col items-center gap-0.5 py-2 text-[11px] text-text-muted"
      >
        <MoreHorizontal size={22} strokeWidth={1.8} />
        More
      </button>
    </nav>
  );
}

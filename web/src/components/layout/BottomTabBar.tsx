import { MoreHorizontal } from 'lucide-react';
import { NavLink } from 'react-router-dom';
import { useNotificationStore } from '../../store/notificationStore';
import { useUiStore } from '../../store/uiStore';
import { PRIMARY_NAV } from './navConfig';

export function BottomTabBar() {
  const setMoreSheetOpen = useUiStore((s) => s.setMoreSheetOpen);
  const unread = useNotificationStore((s) => s.unreadCount());

  return (
    <nav className="pb-safe fixed inset-x-3 bottom-3 z-30 flex rounded-3xl border border-white/20 bg-surface/70 px-1 shadow-[0_8px_30px_rgb(0,0,0,0.18)] backdrop-blur-xl backdrop-saturate-150">
      {PRIMARY_NAV.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.to === '/'}
          className={({ isActive }) =>
            `relative flex flex-1 flex-col items-center gap-0.5 py-2 text-[10px] font-medium transition-colors ${isActive ? 'text-primary' : 'text-text-muted'}`
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
        className="flex flex-1 flex-col items-center gap-0.5 py-2 text-[10px] font-medium text-text-muted"
      >
        <MoreHorizontal size={22} strokeWidth={1.8} />
        More
      </button>
    </nav>
  );
}


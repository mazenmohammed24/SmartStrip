import { ChevronsLeft, LogOut, Menu } from 'lucide-react';
import { NavLink } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import { useNotificationStore } from '../../store/notificationStore';
import { useUiStore } from '../../store/uiStore';
import { ALL_NAV } from './navConfig';

export function SidebarRail() {
  const expanded = useUiStore((s) => s.railExpanded);
  const toggle = useUiStore((s) => s.toggleRail);
  const unread = useNotificationStore((s) => s.unreadCount());
  const logout = useAuthStore((s) => s.logout);

  return (
    <aside className={`sticky top-0 flex h-screen shrink-0 flex-col border-r border-border bg-surface transition-all ${expanded ? 'w-56' : 'w-16'}`}>
      <button
        type="button"
        onClick={toggle}
        className="flex items-center gap-2 px-4 py-4 text-text-muted hover:text-text"
        aria-label="Toggle navigation"
      >
        {expanded ? <ChevronsLeft size={20} strokeWidth={1.8} /> : <Menu size={20} strokeWidth={1.8} />}
        {expanded && <span className="text-sm font-semibold text-text">SmartStrip</span>}
      </button>
      <nav className="flex flex-1 flex-col gap-1 px-2">
        {ALL_NAV.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            title={item.label}
            className={({ isActive }) =>
              `relative flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm ${isActive ? 'bg-primary/10 text-primary' : 'text-text-muted hover:bg-surface-hover hover:text-text'}`
            }
          >
            <item.Icon size={18} strokeWidth={1.8} />
            {expanded && <span className="truncate">{item.label}</span>}
            {item.to === '/notifications' && unread > 0 && (
              <span className={`h-2 w-2 rounded-full bg-danger ${expanded ? 'ml-auto' : 'absolute right-2 top-2'}`} />
            )}
          </NavLink>
        ))}
      </nav>
      <button
        type="button"
        onClick={() => void logout()}
        className="m-2 flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm text-text-muted hover:bg-surface-hover"
        title="Log out"
      >
        <LogOut size={18} strokeWidth={1.8} />
        {expanded && 'Log out'}
      </button>
    </aside>
  );
}

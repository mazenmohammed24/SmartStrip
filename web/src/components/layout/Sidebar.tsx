import type { ReactNode } from 'react';
import { Zap } from 'lucide-react';
import { NavLink } from 'react-router-dom';
import { useNotificationStore } from '../../store/notificationStore';
import { PRIMARY_NAV, SECONDARY_NAV, type NavItem } from './navConfig';

function NavGroup({ label, items, unread }: { label: string; items: NavItem[]; unread: number }): ReactNode {
  return (
    <div className="mb-4">
      <p className="mb-1 px-3 text-xs font-semibold uppercase tracking-wide text-text-muted">{label}</p>
      {items.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.to === '/'}
          className={({ isActive }) =>
            `flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm ${isActive ? 'bg-primary/10 font-medium text-primary' : 'text-text-muted hover:bg-surface-hover hover:text-text'}`
          }
        >
          <item.Icon size={18} strokeWidth={1.8} />
          {item.label}
          {item.to === '/notifications' && unread > 0 && <span className="ml-auto h-2 w-2 rounded-full bg-danger" />}
        </NavLink>
      ))}
    </div>
  );
}

export function Sidebar() {
  const unread = useNotificationStore((s) => s.unreadCount());

  return (
    <aside className="sticky top-0 flex h-screen w-60 shrink-0 flex-col border-r border-border bg-surface px-3 py-4">
      <div className="mb-6 flex items-center gap-2 px-3">
        <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary text-primary-fg shadow-sm"><Zap size={19} /></span>
        <span className="text-base font-semibold text-text">SmartStrip</span>
      </div>
      <NavGroup label="Fleet" items={[PRIMARY_NAV[0], SECONDARY_NAV[1]]} unread={unread} />
      <NavGroup label="Insights" items={[PRIMARY_NAV[1], SECONDARY_NAV[0]]} unread={unread} />
      <NavGroup label="Control" items={[PRIMARY_NAV[2], PRIMARY_NAV[3], SECONDARY_NAV[2]]} unread={unread} />
    </aside>
  );
}

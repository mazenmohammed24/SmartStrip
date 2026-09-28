import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useBreakpoint } from '../../hooks/useBreakpoint';
import { useFleetStore } from '../../store/fleetStore';
import { ALL_NAV } from './navConfig';
import { BottomTabBar } from './BottomTabBar';
import { MoreSheet } from './MoreSheet';
import { Sidebar } from './Sidebar';
import { SidebarRail } from './SidebarRail';
import { TopBar } from './TopBar';

export function AppShell() {
  const breakpoint = useBreakpoint();
  const location = useLocation();
  const navigate = useNavigate();
  const strips = useFleetStore((s) => s.strips);

  const current = ALL_NAV.find((n) => (n.to === '/' ? location.pathname === '/' : location.pathname.startsWith(n.to)));
  const stripMatch = location.pathname.match(/^\/strips\/([^/]+)/);
  const matchedStrip = stripMatch ? strips.find((s) => s.id === stripMatch[1]) : null;
  const title = matchedStrip?.name ?? current?.label ?? 'SmartStrip';
  const onBack = !current ? () => navigate(-1) : undefined;

  if (breakpoint === 'mobile') {
    return (
      <div className="flex min-h-screen flex-col bg-bg">
        <TopBar title={title} onBack={onBack} />
        <main className="app-enter flex-1 overflow-y-auto px-4 pb-24 pt-4">
          <Outlet />
        </main>
        <BottomTabBar />
        <MoreSheet />
      </div>
    );
  }

  if (breakpoint === 'tablet') {
    return (
      <div className="flex min-h-screen bg-bg">
        <SidebarRail />
        <div className="flex min-w-0 flex-1 flex-col">
          <TopBar title={title} onBack={onBack} />
          <main className="app-enter flex-1 overflow-y-auto p-6">
            <Outlet />
          </main>
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen bg-bg">
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar title={title} onBack={onBack} />
        <main className="app-enter flex-1 overflow-y-auto p-8">
          <div className="mx-auto max-w-6xl">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}

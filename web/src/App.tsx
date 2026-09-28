import { lazy, Suspense, useEffect } from 'react';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { AppShell } from './components/layout/AppShell';
import { RequireAuth } from './components/layout/RequireAuth';
import { Spinner } from './components/ui/Button';
import { useThemeEffect } from './hooks/useTheme';
import { useWebSocketConnection } from './hooks/useWebSocket';
import { useAuthStore } from './store/authStore';
import { useNotificationStore } from './store/notificationStore';
import { useSettingsStore } from './store/settingsStore';

const ActivityPage = lazy(() => import('./pages/ActivityPage'));
const AutomationsPage = lazy(() => import('./pages/AutomationsPage'));
const DashboardPage = lazy(() => import('./pages/DashboardPage'));
const LoginPage = lazy(() => import('./pages/LoginPage'));
const NotFoundPage = lazy(() => import('./pages/NotFoundPage'));
const NotificationsPage = lazy(() => import('./pages/NotificationsPage'));
const PowerPage = lazy(() => import('./pages/PowerPage'));
const ScanPage = lazy(() => import('./pages/ScanPage'));
const SettingsPage = lazy(() => import('./pages/SettingsPage'));
const SetupPage = lazy(() => import('./pages/SetupPage'));
const StripDetailPage = lazy(() => import('./pages/StripDetailPage'));

function PageFallback() {
  return (
    <div className="flex justify-center py-16">
      <Spinner size={24} />
    </div>
  );
}

function AppBootstrap() {
  const init = useAuthStore((s) => s.init);
  const status = useAuthStore((s) => s.status);
  const fetchSettings = useSettingsStore((s) => s.fetchSettings);
  const fetchNotifications = useNotificationStore((s) => s.fetchNotifications);

  useThemeEffect();
  useWebSocketConnection();

  useEffect(() => {
    void init();
  }, [init]);

  useEffect(() => {
    if (status === 'authenticated') {
      void fetchSettings();
      void fetchNotifications();
    }
  }, [status, fetchSettings, fetchNotifications]);

  return (
    <Suspense fallback={<PageFallback />}>
      <Routes>
        <Route path="/setup" element={<SetupPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route
          element={
            <RequireAuth>
              <AppShell />
            </RequireAuth>
          }
        >
          <Route path="/" element={<DashboardPage />} />
          <Route path="/strips/:stripId" element={<StripDetailPage />} />
          <Route path="/scan" element={<ScanPage />} />
          <Route path="/power" element={<PowerPage />} />
          <Route path="/activity" element={<ActivityPage />} />
          <Route path="/automations" element={<AutomationsPage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  );
}

export default function App() {
  // '/' normally, '/SmartStrip' when hosted at a sub-path behind a reverse
  // proxy, so the client-side routes match the URLs the browser actually uses.
  const basename = import.meta.env.BASE_URL.replace(/\/$/, '');
  return (
    <BrowserRouter basename={basename}>
      <AppBootstrap />
    </BrowserRouter>
  );
}

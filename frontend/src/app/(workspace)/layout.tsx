'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { MobileNav, Sidebar } from '@/components/shell/Sidebar';
import { Header } from '@/components/shell/Header';
import { useAuthStore } from '@/stores/authStore';
import { useTenantStore } from '@/stores/tenantStore';
import { getStoredRoles, TENANT_STORAGE_KEY, WORKSPACE_STORAGE_KEY, THEME_STORAGE_KEY } from '@/lib/auth';
import { useUIStore } from '@/stores/uiStore';
import { ToastProvider } from '@/components/ui/Toast';
import { ErrorBoundary } from '@/components/shared/ErrorBoundary';
import { Skeleton } from '@/components/ui/Skeleton';
import { get } from '@/lib/api-client';
import { showToast } from '@/stores/toastStore';
import { ensureActiveContext } from '@/lib/context-bootstrap';
import { getUserPreferences } from '@/lib/user-preferences-api';

/**
 * Workspace layout — provides the app shell with sidebar + header + workspace area.
 * All authenticated module pages are rendered inside this layout.
 */
export default function WorkspaceLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const router = useRouter();
  const [hydrated, setHydrated] = useState(false);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const login = useAuthStore((state) => state.login);
  const logout = useAuthStore((state) => state.logout);
  const setCurrentTenant = useTenantStore((state) => state.setCurrentTenant);
  const setTheme = useUIStore((state) => state.setTheme);
  const setUiMode = useUIStore((state) => state.setUiMode);
  const setDensity = useUIStore((state) => state.setDensity);
  const setSidebarCollapsed = useUIStore((state) => state.setSidebarCollapsed);
  const hydrationStartedRef = useRef(false);

  useEffect(() => {
    if (hydrationStartedRef.current) {
      return;
    }
    hydrationStartedRef.current = true;

    let cancelled = false;

    const hydrateSession = async () => {
      if (typeof window === 'undefined') {
        if (!cancelled) {
          setHydrated(true);
        }
        return;
      }

      const storedRoles = getStoredRoles();
      const storedUserId = localStorage.getItem('legent_user_id');
      let contextResolved = false;
      let sessionUser = false;

      try {
        const session = await get<{
          status: string;
          userId: string;
          tenantId: string;
          roles: string[];
          workspaceId?: string | null;
          environmentId?: string | null;
        }>('/auth/session');

        if (!cancelled && session?.status === 'success' && session.userId) {
          sessionUser = true;
          const roles = Array.isArray(session.roles) ? session.roles : storedRoles;
          login(session.userId, roles);
          localStorage.setItem('legent_user_id', session.userId);
          localStorage.setItem('legent_roles', JSON.stringify(roles));
          localStorage.setItem(TENANT_STORAGE_KEY, session.tenantId);
          setCurrentTenant({
            id: session.tenantId,
            name: session.tenantId,
            slug: session.tenantId,
            status: 'ACTIVE',
            plan: 'STARTER',
          });
          try {
            const activeContext = await ensureActiveContext({
              preferredTenantId: session.tenantId,
              preferredWorkspaceId: session.workspaceId ?? null,
              preferredEnvironmentId: session.environmentId ?? null,
            });
            contextResolved = Boolean(activeContext?.workspaceId);
          } catch (contextError) {
            console.error('Context bootstrap failed:', contextError);
          }
          if (contextResolved) {
            try {
              const prefs = await getUserPreferences();
              if (prefs?.theme === 'dark' || prefs?.theme === 'light') {
                setTheme(prefs.theme);
              }
              if (prefs?.uiMode === 'BASIC' || prefs?.uiMode === 'ADVANCED') {
                setUiMode(prefs.uiMode);
              }
              if (prefs?.density) {
                setDensity(prefs.density);
              }
              if (typeof prefs?.sidebarCollapsed === 'boolean') {
                setSidebarCollapsed(prefs.sidebarCollapsed);
              }
            } catch (preferenceError) {
              console.error('Preference hydration failed:', preferenceError);
            }
          }
        }
      } catch (error) {
        console.error('Session hydration failed:', error);
      }

      if (!sessionUser && !cancelled && (isAuthenticated || storedUserId)) {
        logout();
        localStorage.removeItem('legent_user_id');
        localStorage.removeItem('legent_roles');
        localStorage.removeItem(TENANT_STORAGE_KEY);
        localStorage.removeItem(WORKSPACE_STORAGE_KEY);
        localStorage.removeItem('legent_environment_id');
        showToast.error('Session expired', 'Please log in again to continue.', 5000);
      }

      const storedTheme = localStorage.getItem(THEME_STORAGE_KEY);
      if (storedTheme === 'dark' || storedTheme === 'light') {
        setTheme(storedTheme as 'light' | 'dark');
      }

      if (!cancelled) {
        const hasSessionUser = sessionUser && Boolean(localStorage.getItem('legent_user_id'));
        const activeWorkspace = localStorage.getItem(WORKSPACE_STORAGE_KEY);
        if (hasSessionUser && (!contextResolved || !activeWorkspace)) {
          logout();
          showToast.error('Workspace required', 'No workspace context available. Please sign in again.', 5000);
        }
        setHydrated(true);
      }
    };

    hydrateSession();

    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, login, logout, setCurrentTenant, setTheme, setUiMode, setDensity, setSidebarCollapsed]);

  useEffect(() => {
    if (hydrated && !isAuthenticated) {
      router.replace('/login');
    }
  }, [hydrated, isAuthenticated, router]);

  if (!hydrated) {
    return (
      <div className="flex h-screen overflow-hidden">
        <div className="w-64 bg-surface-primary border-r border-border-default" />
        <div className="flex flex-1 flex-col overflow-hidden">
          <div className="h-14 bg-surface-primary border-b border-border-default" />
          <main className="flex-1 overflow-auto bg-surface-secondary p-6">
            <div className="space-y-4">
              <Skeleton variant="text" width="30%" height={32} />
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <Skeleton variant="rounded" width="100%" height={120} />
                <Skeleton variant="rounded" width="100%" height={120} />
                <Skeleton variant="rounded" width="100%" height={120} />
                <Skeleton variant="rounded" width="100%" height={120} />
              </div>
              <Skeleton variant="rounded" width="100%" height={400} />
            </div>
          </main>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    // AUDIT-020: Show loading/redirecting state instead of blank screen
    return (
      <div className="flex h-screen items-center justify-center bg-surface-secondary">
        <div className="text-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-brand-500 mx-auto mb-4" />
          <p className="text-content-secondary">Redirecting to login...</p>
        </div>
      </div>
    );
  }

  return (
    <ToastProvider>
      <div className="flex h-screen overflow-hidden">
        <Sidebar />
        <div className="flex flex-1 flex-col overflow-hidden">
          <Header />
          <main className="app-surface flex-1 overflow-auto p-4 pb-24 md:p-6">
            <ErrorBoundary>
              <div className="mx-auto w-full max-w-[1480px] animate-fade-in">{children}</div>
            </ErrorBoundary>
          </main>
        </div>
        <MobileNav />
      </div>
    </ToastProvider>
  );
}

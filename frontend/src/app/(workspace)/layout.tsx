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
      <div className="flex h-screen overflow-hidden bg-surface-secondary">
        <div className="hidden w-64 border-r border-border-default bg-surface-primary/92 p-4 md:block">
          <div className="mb-8 flex items-center gap-3">
            <span className="grid h-9 w-9 place-items-center rounded-xl bg-gradient-to-br from-brand-500 to-fuchsia-600 text-sm font-semibold text-white shadow-[0_0_28px_rgba(147,51,234,0.35)]">L</span>
            <div className="space-y-2">
              <Skeleton variant="text" width={96} height={14} />
              <Skeleton variant="text" width={72} height={10} />
            </div>
          </div>
          <div className="space-y-3">
            {Array.from({ length: 8 }).map((_, index) => (
              <Skeleton key={index} variant="rounded" width="100%" height={40} />
            ))}
          </div>
        </div>
        <div className="flex flex-1 flex-col overflow-hidden">
          <div className="h-14 border-b border-border-default bg-surface-primary/88 px-6 py-3">
            <Skeleton variant="rounded" width="34%" height={30} className="mx-auto" />
          </div>
          <main className="app-surface flex-1 overflow-auto p-4 md:p-6">
            <div className="mx-auto w-full max-w-[1480px] space-y-5">
              <Skeleton variant="rounded" width="100%" height={150} />
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
                {Array.from({ length: 4 }).map((_, index) => (
                  <Skeleton key={index} variant="rounded" width="100%" height={120} />
                ))}
              </div>
              <Skeleton variant="rounded" width="100%" height={420} />
            </div>
          </main>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    // AUDIT-020: Show loading/redirecting state instead of blank screen
    return (
      <div className="app-surface flex h-screen items-center justify-center p-6">
        <div className="rounded-xl border border-border-default bg-surface-elevated/90 p-8 text-center shadow-[0_24px_70px_rgba(76,29,149,0.14)] backdrop-blur-xl">
          <div className="mx-auto mb-4 h-9 w-9 animate-spin rounded-full border-2 border-brand-500/20 border-b-brand-600" />
          <p className="font-semibold text-content-primary">Redirecting to login</p>
          <p className="mt-1 text-sm text-content-secondary">Session context is being refreshed.</p>
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

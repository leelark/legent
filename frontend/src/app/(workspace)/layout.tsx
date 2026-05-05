'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Sidebar } from '@/components/shell/Sidebar';
import { Header } from '@/components/shell/Header';
import { useAuthStore } from '@/stores/authStore';
import { useTenantStore } from '@/stores/tenantStore';
import { getStoredRoles, getStoredTenantId, TENANT_STORAGE_KEY, THEME_STORAGE_KEY } from '@/lib/auth';
import { useUIStore } from '@/stores/uiStore';
import { ToastProvider } from '@/components/ui/Toast';
import { ErrorBoundary } from '@/components/shared/ErrorBoundary';
import { Skeleton } from '@/components/ui/Skeleton';
import { get } from '@/lib/api-client';
import { showToast } from '@/stores/toastStore';

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
  const setCurrentTenant = useTenantStore((state) => state.setCurrentTenant);
  const setTheme = useUIStore((state) => state.setTheme);

  useEffect(() => {
    let cancelled = false;

    const hydrateSession = async () => {
      if (typeof window === 'undefined') {
        if (!cancelled) {
          setHydrated(true);
        }
        return;
      }

      const storedTenantId = getStoredTenantId();
      const storedRoles = getStoredRoles();
      const storedUserId = localStorage.getItem('legent_user_id');

      if (!isAuthenticated) {
        try {
          const session = await get<{ status: string; userId: string; tenantId: string; roles: string[] }>('/auth/session');
          if (!cancelled && session?.status === 'success' && session.userId) {
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
          } else if (!cancelled && storedUserId && storedRoles.length > 0) {
            login(storedUserId, storedRoles);
          }
        } catch (error) {
          // LEGENT-HIGH-006: Log error and show user feedback for session hydration failures
          console.error('Session hydration failed:', error);
          if (!cancelled) {
            localStorage.removeItem('legent_user_id');
            localStorage.removeItem('legent_roles');
            showToast.error('Session expired', 'Please log in again to continue.', 5000);
          }
        }
      }

      if (storedTenantId) {
        setCurrentTenant({
          id: storedTenantId,
          name: storedTenantId,
          slug: storedTenantId,
          status: 'ACTIVE',
          plan: 'STARTER',
        });
      }

      const storedTheme = localStorage.getItem(THEME_STORAGE_KEY);
      if (storedTheme === 'dark' || storedTheme === 'light') {
        setTheme(storedTheme as 'light' | 'dark');
      }

      if (!cancelled) {
        setHydrated(true);
      }
    };

    hydrateSession();

    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, login, setCurrentTenant, setTheme]);

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
          <main className="flex-1 overflow-auto bg-surface-secondary p-6">
            <ErrorBoundary>
              <div className="animate-fade-in">{children}</div>
            </ErrorBoundary>
          </main>
        </div>
      </div>
    </ToastProvider>
  );
}

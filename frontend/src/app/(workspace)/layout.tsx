'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Sidebar } from '@/components/shell/Sidebar';
import { Header } from '@/components/shell/Header';
import { useAuthStore } from '@/stores/authStore';
import { useTenantStore } from '@/stores/tenantStore';
import { parseJwtClaims, getStoredRoles, getStoredTenantId, TOKEN_STORAGE_KEY, THEME_STORAGE_KEY } from '@/lib/auth';
import { useUIStore } from '@/stores/uiStore';

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
    if (typeof window === 'undefined') {
      setHydrated(true);
      return;
    }

    const token = localStorage.getItem(TOKEN_STORAGE_KEY);
    const storedTenantId = getStoredTenantId();
    const storedRoles = getStoredRoles();
    const storedUserId = localStorage.getItem('legent_user_id');

    if (token && !isAuthenticated) {
      const claims = parseJwtClaims(token);
      const roles = storedRoles.length > 0 ? storedRoles : claims?.roles ?? [];
      const userId = storedUserId ?? claims?.sub ?? 'anonymous';
      login(userId, token, roles);
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

    const storedTheme = typeof window !== 'undefined'
      ? localStorage.getItem(THEME_STORAGE_KEY)
      : null;
    if (storedTheme === 'dark' || storedTheme === 'light') {
      setTheme(storedTheme as 'light' | 'dark');
    }

    setHydrated(true);
  }, [isAuthenticated, login, setCurrentTenant, setTheme]);

  useEffect(() => {
    if (hydrated && !isAuthenticated) {
      router.replace('/login');
    }
  }, [hydrated, isAuthenticated, router]);

  if (!hydrated || !isAuthenticated) {
    return null;
  }

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-auto bg-surface-secondary p-6">
          <div className="animate-fade-in">{children}</div>
        </main>
      </div>
    </div>
  );
}

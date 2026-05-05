'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  MagnifyingGlass,
  Bell,
  Moon,
  Sun,
  Buildings,
  SignOut,
} from '@phosphor-icons/react';
import { useUIStore } from '@/stores/uiStore';
import { useAuth } from '@/hooks/useAuth';
import { useTenant } from '@/hooks/useTenant';
import { get, post } from '@/lib/api-client';

export function Header() {
  const router = useRouter();
  const { theme, toggleTheme } = useUIStore();
  const { logout, isAuthenticated } = useAuth();
  const { tenantName, switchTenant } = useTenant();
  const [contexts, setContexts] = useState<Array<{ tenantId: string; workspaceId?: string | null }>>([]);
  const [selectedContext, setSelectedContext] = useState<string>('');

  const handleLogout = async () => {
    try {
      await post('/auth/logout');
    } catch {
      // Continue with client-side logout even if backend call fails.
    } finally {
      logout();
      if (typeof window !== 'undefined') {
        window.location.href = '/login';
      } else {
        router.push('/login');
      }
    }
  };

  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }
    let active = true;
    get<Array<{ tenantId: string; workspaceId?: string | null }>>('/auth/contexts')
      .then((items) => {
        if (!active) {
          return;
        }
        setContexts(items ?? []);
        const currentTenant = typeof window !== 'undefined' ? localStorage.getItem('legent_tenant_id') : null;
        const matched = (items ?? []).find((ctx) => ctx.tenantId === currentTenant) ?? items?.[0];
        if (matched) {
          const value = `${matched.tenantId}::${matched.workspaceId ?? ''}`;
          setSelectedContext(value);
        }
      })
      .catch(() => {
        if (active) {
          setContexts([]);
        }
      });

    return () => {
      active = false;
    };
  }, [isAuthenticated]);

  const handleContextChange = async (value: string) => {
    setSelectedContext(value);
    const [tenantId, workspaceIdRaw] = value.split('::');
    const workspaceId = workspaceIdRaw || null;
    try {
      await post('/auth/context/switch', {
        tenantId,
        workspaceId,
      });
      if (typeof window !== 'undefined') {
        localStorage.setItem('legent_tenant_id', tenantId);
        if (workspaceId) {
          localStorage.setItem('legent_workspace_id', workspaceId);
        } else {
          localStorage.removeItem('legent_workspace_id');
        }
      }
      switchTenant({
        id: tenantId,
        name: tenantId,
        slug: tenantId,
        status: 'ACTIVE',
        plan: 'STARTER',
      });
      router.refresh();
    } catch {
      // no-op
    }
  };

  return (
    <header className="flex h-14 items-center justify-between border-b border-border-default bg-surface-primary px-6">
      <div className="flex items-center gap-2">
        <span className="text-sm font-medium text-content-secondary">
          {isAuthenticated ? tenantName : 'Email Studio'}
        </span>
      </div>

      <div className="hidden md:flex items-center w-96">
        <div className="relative w-full">
          <MagnifyingGlass
            size={16}
            className="absolute left-3 top-1/2 -translate-y-1/2 text-content-muted"
          />
          <input
            type="text"
            placeholder="Search emails, campaigns, subscribers..."
            className="w-full rounded-lg border border-border-default bg-surface-secondary py-2 pl-9 pr-4
                       text-sm text-content-primary placeholder-content-muted
                       focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/30
                       transition-all duration-150"
          />
          <kbd className="absolute right-3 top-1/2 -translate-y-1/2 rounded border border-border-default
                         bg-surface-primary px-1.5 py-0.5 text-2xs text-content-muted font-mono">
            ⌘K
          </kbd>
        </div>
      </div>

      <div className="flex items-center gap-1">
        <div
          className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm text-content-secondary
                     hover:bg-surface-secondary transition-colors"
        >
          <Buildings size={16} />
          {contexts.length > 0 ? (
            <select
              value={selectedContext}
              onChange={(event) => handleContextChange(event.target.value)}
              className="hidden rounded-lg border border-border-default bg-surface-primary px-2 py-1 text-xs text-content-primary lg:inline"
            >
              {contexts.map((ctx) => {
                const value = `${ctx.tenantId}::${ctx.workspaceId ?? ''}`;
                const label = ctx.workspaceId ? `${ctx.tenantId} / ${ctx.workspaceId}` : ctx.tenantId;
                return (
                  <option key={value} value={value}>
                    {label}
                  </option>
                );
              })}
            </select>
          ) : (
            <span className="hidden lg:inline">{tenantName}</span>
          )}
        </div>

        <button
          className="relative rounded-lg p-2 text-content-secondary hover:bg-surface-secondary transition-colors"
          aria-label="Notifications"
        >
          <Bell size={18} />
          <span className="absolute right-1.5 top-1.5 h-2 w-2 rounded-full bg-danger" />
        </button>

        <button
          onClick={toggleTheme}
          className="rounded-lg p-2 text-content-secondary hover:bg-surface-secondary transition-colors"
          aria-label="Toggle theme"
        >
          {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
        </button>

        <button
          onClick={handleLogout}
          className="ml-2 flex h-8 w-8 items-center justify-center rounded-full
                          bg-surface-secondary text-content-secondary hover:bg-surface-primary transition-colors"
          aria-label="Logout"
        >
          <SignOut size={18} />
        </button>
      </div>
    </header>
  );
}

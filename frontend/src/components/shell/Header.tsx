'use client';

import { useEffect, useMemo, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import Link from 'next/link';
import { Bell, Check, LogOut, Moon, Search, Sun, X } from 'lucide-react';
import { useUIStore } from '@/stores/uiStore';
import { useAuth } from '@/hooks/useAuth';
import { useTenant } from '@/hooks/useTenant';
import { get, post } from '@/lib/api-client';
import { updateUserPreferences } from '@/lib/user-preferences-api';
import {
  TENANT_STORAGE_KEY,
  WORKSPACE_STORAGE_KEY,
  ENVIRONMENT_STORAGE_KEY,
} from '@/lib/auth';
import { clsx } from 'clsx';

type ContextItem = { tenantId: string; workspaceId?: string | null };
type SearchResult = { id?: string; title?: string; name?: string; entityType?: string; type?: string; payload?: unknown };
type NotificationItem = { id: string; title?: string; message?: string; type?: string; createdAt?: string };

const moduleFromPath: Record<string, string> = {
  email: 'Email Studio',
  audience: 'Audience',
  campaigns: 'Campaign Studio',
  automation: 'Automation',
  automations: 'Automation',
  deliverability: 'Delivery Studio',
  analytics: 'Analytics',
  tracking: 'Tracking',
  admin: 'Admin',
  settings: 'Settings',
};

export function Header() {
  const router = useRouter();
  const pathname = usePathname();
  const { theme, toggleTheme, uiMode, toggleUiMode } = useUIStore();
  const { logout, isAuthenticated } = useAuth();
  const { tenantName, switchTenant } = useTenant();
  const [contexts, setContexts] = useState<ContextItem[]>([]);
  const [selectedContext, setSelectedContext] = useState('');
  const [searchOpen, setSearchOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [searching, setSearching] = useState(false);
  const [searchResults, setSearchResults] = useState<SearchResult[]>([]);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);

  const activeModule = useMemo(() => {
    const key = pathname.split('/').filter(Boolean)[1] ?? 'email';
    return moduleFromPath[key] ?? 'Workspace';
  }, [pathname]);

  const handleLogout = async () => {
    try {
      await post('/auth/logout');
    } catch {
      // Continue client logout even when backend call fails.
    } finally {
      logout();
      window.location.href = '/login';
    }
  };

  const handleToggleMode = async () => {
    const next = uiMode === 'BASIC' ? 'ADVANCED' : 'BASIC';
    toggleUiMode();
    try {
      await updateUserPreferences({ uiMode: next });
    } catch {
      // Keep local mode for continuity.
    }
  };

  const handleToggleTheme = async () => {
    const next = theme === 'dark' ? 'light' : 'dark';
    toggleTheme();
    try {
      await updateUserPreferences({ theme: next });
    } catch {
      // Keep local theme for continuity.
    }
  };

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        setSearchOpen(true);
      }
      if (event.key === 'Escape') {
        setSearchOpen(false);
        setNotificationsOpen(false);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  useEffect(() => {
    if (!isAuthenticated) return;
    let active = true;
    get<ContextItem[]>('/auth/contexts')
      .then((items) => {
        if (!active) return;
        const next = items ?? [];
        setContexts(next);
        const currentTenant = localStorage.getItem(TENANT_STORAGE_KEY);
        const matched = next.find((ctx) => ctx.tenantId === currentTenant) ?? next[0];
        if (matched) {
          const value = `${matched.tenantId}::${matched.workspaceId ?? ''}`;
          setSelectedContext(value);
          localStorage.setItem(TENANT_STORAGE_KEY, matched.tenantId);
          if (matched.workspaceId) localStorage.setItem(WORKSPACE_STORAGE_KEY, matched.workspaceId);
        }
      })
      .catch(() => {
        if (active) setContexts([]);
      });

    get<NotificationItem[]>('/platform/notifications')
      .then((items) => {
        if (active) setNotifications(Array.isArray(items) ? items : []);
      })
      .catch(() => {
        if (active) setNotifications([]);
      });

    return () => {
      active = false;
    };
  }, [isAuthenticated]);

  useEffect(() => {
    if (!searchOpen || query.trim().length < 2) {
      setSearchResults([]);
      return;
    }
    let active = true;
    setSearching(true);
    const id = window.setTimeout(() => {
      get<SearchResult[]>('/platform/search', { params: { q: query.trim() } })
        .then((items) => {
          if (active) setSearchResults(Array.isArray(items) ? items : []);
        })
        .catch(() => {
          if (active) setSearchResults([]);
        })
        .finally(() => {
          if (active) setSearching(false);
        });
    }, 220);
    return () => {
      active = false;
      window.clearTimeout(id);
    };
  }, [query, searchOpen]);

  const handleContextChange = async (value: string) => {
    setSelectedContext(value);
    const [tenantId, workspaceIdRaw] = value.split('::');
    const workspaceId = workspaceIdRaw || null;
    await post('/auth/context/switch', { tenantId, workspaceId });
    localStorage.setItem(TENANT_STORAGE_KEY, tenantId);
    if (workspaceId) localStorage.setItem(WORKSPACE_STORAGE_KEY, workspaceId);
    else localStorage.removeItem(WORKSPACE_STORAGE_KEY);
    localStorage.removeItem(ENVIRONMENT_STORAGE_KEY);
    switchTenant({ id: tenantId, name: tenantId, slug: tenantId, status: 'ACTIVE', plan: 'STARTER' });
    router.refresh();
  };

  const markRead = async (id: string) => {
    setNotifications((items) => items.filter((item) => item.id !== id));
    try {
      await post(`/platform/notifications/${id}/read`);
    } catch {
      // Next refresh reconciles.
    }
  };

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center justify-between border-b border-border-default bg-surface-primary/90 px-4 backdrop-blur-xl md:px-6">
      <div className="min-w-0">
        <p className="truncate text-sm font-semibold text-content-primary">{activeModule}</p>
        <p className="hidden truncate text-xs text-content-secondary sm:block">{tenantName || 'Workspace'}</p>
      </div>

      <button
        onClick={() => setSearchOpen(true)}
        className="mx-3 hidden h-9 w-full max-w-md items-center gap-2 rounded-lg border border-border-default bg-surface-secondary px-3 text-left text-sm text-content-muted transition hover:border-border-strong hover:text-content-primary md:flex"
      >
        <Search size={16} />
        <span className="flex-1 truncate">Search campaigns, subscribers, templates...</span>
        <kbd className="rounded border border-border-default bg-surface-primary px-1.5 py-0.5 text-[10px] font-medium">Ctrl K</kbd>
      </button>

      <div className="flex items-center gap-1">
        {contexts.length > 0 && (
          <select
            value={selectedContext}
            onChange={(event) => void handleContextChange(event.target.value)}
            className="hidden max-w-56 rounded-lg border border-border-default bg-surface-secondary px-2 py-2 text-xs text-content-primary lg:block"
            aria-label="Workspace context"
          >
            {contexts.map((ctx) => {
              const value = `${ctx.tenantId}::${ctx.workspaceId ?? ''}`;
              return (
                <option key={value} value={value}>
                  {ctx.workspaceId ? `${ctx.tenantId} / ${ctx.workspaceId}` : ctx.tenantId}
                </option>
              );
            })}
          </select>
        )}

        <button className="rounded-lg p-2 text-content-secondary hover:bg-surface-secondary hover:text-content-primary md:hidden" onClick={() => setSearchOpen(true)} aria-label="Search">
          <Search size={18} />
        </button>
        <button
          onClick={() => setNotificationsOpen((open) => !open)}
          className="relative rounded-lg p-2 text-content-secondary hover:bg-surface-secondary hover:text-content-primary"
          aria-label="Notifications"
        >
          <Bell size={18} />
          {notifications.length > 0 && <span className="absolute right-1.5 top-1.5 h-2 w-2 rounded-full bg-danger" />}
        </button>
        <button
          onClick={handleToggleMode}
          className={clsx(
            'rounded-lg border border-border-default px-3 py-2 text-xs font-semibold transition-colors',
            uiMode === 'ADVANCED' ? 'bg-brand-900/20 text-brand-300' : 'text-content-secondary hover:bg-surface-secondary'
          )}
          aria-label="Toggle Basic or Advanced mode"
        >
          {uiMode === 'BASIC' ? 'Basic' : 'Advanced'}
        </button>
        <button onClick={handleToggleTheme} className="rounded-lg p-2 text-content-secondary hover:bg-surface-secondary hover:text-content-primary" aria-label="Toggle theme">
          {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
        </button>
        <button onClick={handleLogout} className="rounded-lg p-2 text-content-secondary hover:bg-surface-secondary hover:text-content-primary" aria-label="Logout">
          <LogOut size={18} />
        </button>
      </div>

      {notificationsOpen && (
        <div className="absolute right-4 top-14 z-50 w-[min(360px,calc(100vw-2rem))] rounded-lg border border-border-default bg-surface-elevated p-2 shadow-2xl">
          <div className="flex items-center justify-between px-2 py-1">
            <p className="text-sm font-semibold">Notifications</p>
            <button onClick={() => setNotificationsOpen(false)} className="rounded p-1 text-content-secondary hover:bg-surface-secondary" aria-label="Close notifications">
              <X size={15} />
            </button>
          </div>
          {notifications.length === 0 ? (
            <p className="px-2 py-6 text-center text-sm text-content-secondary">No unread notifications</p>
          ) : (
            <div className="max-h-80 space-y-1 overflow-auto">
              {notifications.map((item) => (
                <div key={item.id} className="rounded-lg border border-border-default bg-surface-secondary p-3">
                  <div className="flex gap-3">
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold">{item.title || item.type || 'Notification'}</p>
                      {item.message && <p className="mt-1 text-xs text-content-secondary">{item.message}</p>}
                    </div>
                    <button onClick={() => void markRead(item.id)} className="h-7 w-7 rounded-lg text-content-secondary hover:bg-surface-elevated" aria-label="Mark as read">
                      <Check size={15} className="mx-auto" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {searchOpen && (
        <div className="fixed inset-0 z-50 bg-black/45 p-4 backdrop-blur-sm" onClick={() => setSearchOpen(false)}>
          <div className="mx-auto mt-20 w-full max-w-2xl rounded-lg border border-border-default bg-surface-elevated shadow-2xl" onClick={(event) => event.stopPropagation()}>
            <div className="flex items-center gap-2 border-b border-border-default px-4 py-3">
              <Search size={18} className="text-content-muted" />
              <input
                autoFocus
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search workspace"
                className="h-9 flex-1 bg-transparent text-sm text-content-primary outline-none placeholder:text-content-muted"
              />
              <button onClick={() => setSearchOpen(false)} className="rounded-lg p-2 text-content-secondary hover:bg-surface-secondary" aria-label="Close search">
                <X size={17} />
              </button>
            </div>
            <div className="max-h-[420px] overflow-auto p-2">
              {query.trim().length < 2 ? (
                <p className="px-3 py-8 text-center text-sm text-content-secondary">Type at least 2 characters</p>
              ) : searching ? (
                <p className="px-3 py-8 text-center text-sm text-content-secondary">Searching...</p>
              ) : searchResults.length === 0 ? (
                <p className="px-3 py-8 text-center text-sm text-content-secondary">No results found</p>
              ) : (
                searchResults.map((result, index) => (
                  <Link
                    key={result.id || index}
                    href="/app/admin"
                    onClick={() => setSearchOpen(false)}
                    className="block rounded-lg px-3 py-3 transition hover:bg-surface-secondary"
                  >
                    <p className="text-sm font-semibold">{result.title || result.name || result.id || 'Search result'}</p>
                    <p className="mt-1 text-xs text-content-secondary">{result.entityType || result.type || 'Workspace object'}</p>
                  </Link>
                ))
              )}
            </div>
          </div>
        </div>
      )}
    </header>
  );
}

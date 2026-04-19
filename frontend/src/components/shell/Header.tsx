'use client';

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

export function Header() {
  const router = useRouter();
  const { theme, toggleTheme } = useUIStore();
  const { logout, isAuthenticated } = useAuth();
  const { tenantName } = useTenant();

  const handleLogout = () => {
    logout();
    if (typeof window !== 'undefined') {
      window.location.href = '/login';
    } else {
      router.push('/login');
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
        <button
          className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm text-content-secondary
                     hover:bg-surface-secondary transition-colors"
        >
          <Buildings size={16} />
          <span className="hidden lg:inline">{tenantName}</span>
        </button>

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

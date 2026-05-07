'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { clsx } from 'clsx';
import {
  BarChart3,
  Bot,
  ChevronLeft,
  ChevronRight,
  Gauge,
  LayoutTemplate,
  Megaphone,
  RadioTower,
  Settings,
  ShieldCheck,
  Users,
} from 'lucide-react';
import { useUIStore } from '@/stores/uiStore';
import { useAuth } from '@/hooks/useAuth';
import { updateUserPreferences } from '@/lib/user-preferences-api';

const NAV_ITEMS = [
  { label: 'Dashboard', href: '/app/email', icon: Gauge },
  { label: 'Audience', href: '/app/audience', icon: Users },
  { label: 'Templates', href: '/app/email/templates', icon: LayoutTemplate },
  { label: 'Campaigns', href: '/app/campaigns', icon: Megaphone },
  { label: 'Automation', href: '/app/automation', icon: Bot },
  { label: 'Delivery', href: '/app/deliverability', icon: RadioTower },
  { label: 'Analytics', href: '/app/analytics', icon: BarChart3 },
  { label: 'Admin', href: '/app/admin', icon: ShieldCheck, admin: true },
  { label: 'Settings', href: '/app/settings/platform', icon: Settings, advanced: true },
];

export function Sidebar() {
  const pathname = usePathname();
  const { sidebarCollapsed, setSidebarCollapsed } = useUIStore();
  const { isAdmin } = useAuth();

  const toggleSidebar = async () => {
    const next = !sidebarCollapsed;
    setSidebarCollapsed(next);
    try {
      await updateUserPreferences({ sidebarCollapsed: next });
    } catch {
      // Local state stays authoritative for current session.
    }
  };

  return (
    <aside
      className={clsx(
        'hidden shrink-0 flex-col border-r border-border-default bg-surface-primary/95 transition-all duration-200 md:flex',
        sidebarCollapsed ? 'w-16' : 'w-64'
      )}
    >
      <Brand collapsed={sidebarCollapsed} />
      <nav className="flex-1 space-y-1 px-3 py-4">
        {NAV_ITEMS.map((item) => {
          if (item.admin && !isAdmin()) return null;
          const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
          const Icon = item.icon;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={clsx('sidebar-item', active && 'active')}
              title={sidebarCollapsed ? item.label : undefined}
              data-advanced={item.advanced ? 'true' : undefined}
            >
              <Icon size={18} className="shrink-0" />
              {!sidebarCollapsed && <span className="truncate">{item.label}</span>}
            </Link>
          );
        })}
      </nav>
      <button
        onClick={toggleSidebar}
        className="flex items-center justify-center border-t border-border-default p-3 text-content-secondary transition-colors hover:bg-surface-secondary hover:text-content-primary"
        aria-label={sidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
      >
        {sidebarCollapsed ? <ChevronRight size={16} /> : <ChevronLeft size={16} />}
      </button>
    </aside>
  );
}

export function MobileNav() {
  const pathname = usePathname();
  const { isAdmin } = useAuth();
  const items = NAV_ITEMS.filter((item) => !item.admin || isAdmin()).slice(0, 5);

  return (
    <nav className="fixed inset-x-0 bottom-0 z-40 grid grid-cols-5 border-t border-border-default bg-surface-primary/95 px-2 py-2 backdrop-blur-xl md:hidden">
      {items.map((item) => {
        const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
        const Icon = item.icon;
        return (
          <Link
            key={item.href}
            href={item.href}
            className={clsx(
              'flex min-w-0 flex-col items-center gap-1 rounded-lg px-1 py-1.5 text-[11px] font-medium transition-colors',
              active ? 'bg-brand-50 text-accent dark:bg-brand-900/20 dark:text-brand-300' : 'text-content-secondary'
            )}
          >
            <Icon size={18} />
            <span className="max-w-full truncate">{item.label}</span>
          </Link>
        );
      })}
    </nav>
  );
}

function Brand({ collapsed }: { collapsed: boolean }) {
  return (
    <Link href="/app/email" className="flex h-14 items-center gap-3 border-b border-border-default px-4">
      <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-brand-500 to-brand-800 text-sm font-bold text-white shadow-sm">
        L
      </span>
      {!collapsed && (
        <span className="min-w-0">
          <span className="block truncate text-sm font-semibold text-content-primary">Legent</span>
          <span className="block truncate text-[11px] font-medium text-content-muted">Email Studio</span>
        </span>
      )}
    </Link>
  );
}

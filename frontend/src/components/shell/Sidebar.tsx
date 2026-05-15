'use client';

import { useState } from 'react';
import Image from 'next/image';
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
  MoreHorizontal,
  RadioTower,
  Rocket,
  Settings,
  ShieldCheck,
  Users,
} from 'lucide-react';
import { useUIStore } from '@/stores/uiStore';
import { useAuth } from '@/hooks/useAuth';
import { updateUserPreferences } from '@/lib/user-preferences-api';

const NAV_ITEMS = [
  { label: 'Dashboard', href: '/app/email', icon: Gauge },
  { label: 'Launch', href: '/app/launch', icon: Rocket },
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
        'hidden shrink-0 flex-col border-r border-border-default bg-surface-primary/92 shadow-[12px_0_40px_rgba(0,0,0,0.12)] backdrop-blur-xl transition-all duration-200 md:flex',
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
              aria-current={active ? 'page' : undefined}
            >
              <Icon size={18} className="shrink-0" />
              {!sidebarCollapsed && <span className="truncate">{item.label}</span>}
            </Link>
          );
        })}
      </nav>
      <button
        type="button"
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
  const [overflowOpen, setOverflowOpen] = useState(false);
  const items = NAV_ITEMS.filter((item) => !item.admin || isAdmin());
  const primaryItems = items.slice(0, 4);
  const overflowItems = items.slice(4);
  const overflowActive = overflowItems.some((item) => pathname === item.href || pathname.startsWith(`${item.href}/`));

  return (
    <nav className="fixed inset-x-0 bottom-0 z-40 border-t border-border-default bg-surface-primary/95 px-2 py-2 shadow-[0_-16px_40px_rgba(0,0,0,0.18)] backdrop-blur-xl md:hidden" aria-label="Mobile workspace navigation">
      {overflowOpen && overflowItems.length > 0 && (
        <div className="mb-2 grid grid-cols-2 gap-1 rounded-xl border border-border-default bg-surface-elevated p-2 shadow-2xl">
          {overflowItems.map((item) => {
            const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
            const Icon = item.icon;
            return (
              <Link
                key={item.href}
                href={item.href}
                onClick={() => setOverflowOpen(false)}
                className={clsx(
                  'flex min-w-0 items-center gap-2 rounded-lg px-3 py-2 text-xs font-semibold',
                  active ? 'bg-brand-50 text-accent dark:bg-brand-900/20 dark:text-brand-300' : 'text-content-secondary'
                )}
                data-advanced={item.advanced ? 'true' : undefined}
                aria-current={active ? 'page' : undefined}
              >
                <Icon size={16} className="shrink-0" />
                <span className="truncate">{item.label}</span>
              </Link>
            );
          })}
        </div>
      )}

      <div className="grid grid-cols-5 gap-1">
        {primaryItems.map((item) => {
          const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
          const Icon = item.icon;
          return (
            <Link
              key={item.href}
              href={item.href}
              onClick={() => setOverflowOpen(false)}
              className={clsx(
                'flex min-w-0 flex-col items-center gap-1 rounded-lg px-1 py-1.5 text-[11px] font-medium',
                active ? 'bg-brand-50 text-accent dark:bg-brand-900/20 dark:text-brand-300' : 'text-content-secondary'
              )}
              aria-current={active ? 'page' : undefined}
            >
              <Icon size={18} />
              <span className="max-w-full truncate">{item.label}</span>
            </Link>
          );
        })}
        <button
          type="button"
          onClick={() => setOverflowOpen((open) => !open)}
          className={clsx(
            'flex min-w-0 flex-col items-center gap-1 rounded-lg px-1 py-1.5 text-[11px] font-medium',
            overflowActive || overflowOpen ? 'bg-brand-50 text-accent dark:bg-brand-900/20 dark:text-brand-300' : 'text-content-secondary'
          )}
          aria-expanded={overflowOpen}
          aria-label="More navigation"
        >
          <MoreHorizontal size={18} />
          <span className="max-w-full truncate">More</span>
        </button>
      </div>
    </nav>
  );
}

function Brand({ collapsed }: { collapsed: boolean }) {
  return (
    <Link href="/app/email" className="flex h-14 items-center gap-3 border-b border-border-default px-4">
      <Image src="/legent-logo.svg" alt="" width={34} height={34} className="h-8 w-8 shrink-0 rounded-xl shadow-[0_0_28px_rgba(147,51,234,0.35)]" priority />
      {!collapsed && (
        <span className="min-w-0">
          <span className="block truncate text-sm font-semibold text-content-primary">Legent</span>
          <span className="block truncate text-[11px] font-medium text-content-muted">Email Studio</span>
        </span>
      )}
    </Link>
  );
}

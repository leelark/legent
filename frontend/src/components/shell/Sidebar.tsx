'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { clsx } from 'clsx';
import {
  EnvelopeSimple,
  Users,
  Megaphone,
  Lightning,
  ChartLine,
  Shield,
  GearSix,
  CaretLeft,
  CaretRight,
} from '@phosphor-icons/react';
import { useUIStore } from '@/stores/uiStore';
import { useAuth } from '@/hooks/useAuth';

const NAV_ITEMS = [
  { label: 'Email',          href: '/email',          icon: EnvelopeSimple },
  { label: 'Audience',       href: '/audience',       icon: Users },
  { label: 'Campaigns',      href: '/campaigns',      icon: Megaphone },
  { label: 'Automation',     href: '/automation',     icon: Lightning },
  { label: 'Tracking',       href: '/tracking',       icon: ChartLine },
  { label: 'Deliverability', href: '/deliverability', icon: Shield },
  { label: 'Admin',          href: '/admin',          icon: GearSix, admin: true },
];

export function Sidebar() {
  const pathname = usePathname();
  const { sidebarCollapsed, toggleSidebar } = useUIStore();
  const { isAdmin } = useAuth();

  return (
    <aside
      className={clsx(
        'flex flex-col border-r border-border-default bg-surface-primary transition-all duration-200',
        sidebarCollapsed ? 'w-16' : 'w-64'
      )}
    >
      {/* Logo */}
      <div className="flex h-14 items-center gap-3 border-b border-border-default px-4">
        <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-brand-500 to-brand-700 text-sm font-bold text-white shadow-sm">
          L
        </div>
        {!sidebarCollapsed && (
          <span className="text-base font-semibold text-content-primary animate-fade-in">
            Legent
          </span>
        )}
      </div>

      {/* Navigation */}
      <nav className="flex-1 space-y-1 px-3 py-4">
        {NAV_ITEMS.map((item) => {
          const isActive = pathname.startsWith(item.href);
          const Icon = item.icon;

          return (
            <Link
              key={item.href}
              href={item.href}
              className={clsx('sidebar-item', isActive && 'active')}
              title={sidebarCollapsed ? item.label : undefined}
            >
              <Icon
                size={20}
                weight={isActive ? 'fill' : 'regular'}
                className="flex-shrink-0"
              />
              {!sidebarCollapsed && (
                <span className="animate-fade-in">{item.label}</span>
              )}
            </Link>
          );
        })}
      </nav>

      {/* Collapse toggle */}
      <button
        onClick={toggleSidebar}
        className="flex items-center justify-center border-t border-border-default p-3
                   text-content-secondary hover:text-content-primary transition-colors"
        aria-label={sidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
      >
        {sidebarCollapsed ? <CaretRight size={16} /> : <CaretLeft size={16} />}
      </button>
    </aside>
  );
}

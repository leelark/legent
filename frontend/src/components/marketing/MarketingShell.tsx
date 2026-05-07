'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { clsx } from 'clsx';
import { Button } from '@/components/ui/Button';
import { useMemo, useState } from 'react';
import { Menu, X } from 'lucide-react';

const NAV_LINKS = [
  { href: '/', label: 'Home' },
  { href: '/features', label: 'Features' },
  { href: '/modules', label: 'Modules' },
  { href: '/pricing', label: 'Pricing' },
  { href: '/about', label: 'About' },
  { href: '/contact', label: 'Contact' },
  { href: '/blog', label: 'Blog' },
];

export function MarketingShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const year = useMemo(() => new Date().getFullYear(), []);
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <div className="min-h-screen bg-surface-primary text-content-primary">
      <header className="sticky top-0 z-40 border-b border-border-default/70 bg-surface-primary/90 backdrop-blur-xl">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6">
          <Link href="/" className="flex items-center gap-3" onClick={() => setMobileOpen(false)}>
            <span className="inline-flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-br from-brand-500 to-brand-800 text-white shadow-elevated font-semibold">
              L
            </span>
            <span className="text-base font-semibold tracking-normal">Legent</span>
          </Link>
          <nav className="hidden items-center gap-5 lg:flex">
            {NAV_LINKS.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className={clsx(
                  'text-sm font-medium transition-colors',
                  pathname === item.href ? 'text-brand-600 dark:text-brand-400' : 'text-content-secondary hover:text-content-primary'
                )}
              >
                {item.label}
              </Link>
            ))}
          </nav>
          <div className="hidden items-center gap-2 sm:flex">
            <Link href="/login"><Button variant="ghost" size="sm">Login</Button></Link>
            <Link href="/signup"><Button size="sm">Start Free</Button></Link>
          </div>
          <button
            className="rounded-lg p-2 text-content-secondary hover:bg-surface-secondary lg:hidden"
            onClick={() => setMobileOpen((open) => !open)}
            aria-label="Toggle navigation"
          >
            {mobileOpen ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>
        {mobileOpen && (
          <div className="border-t border-border-default bg-surface-primary px-4 py-3 lg:hidden">
            <nav className="grid gap-1">
              {NAV_LINKS.map((item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  onClick={() => setMobileOpen(false)}
                  className={clsx(
                    'rounded-lg px-3 py-2 text-sm font-medium',
                    pathname === item.href ? 'bg-brand-50 text-accent dark:bg-brand-900/20 dark:text-brand-300' : 'text-content-secondary'
                  )}
                >
                  {item.label}
                </Link>
              ))}
            </nav>
            <div className="mt-3 grid grid-cols-2 gap-2 sm:hidden">
              <Link href="/login"><Button variant="secondary" size="sm" className="w-full">Login</Button></Link>
              <Link href="/signup"><Button size="sm" className="w-full">Start Free</Button></Link>
            </div>
          </div>
        )}
      </header>
      <main>{children}</main>
      <footer className="border-t border-border-default bg-surface-secondary/70">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 px-6 py-10 md:flex-row md:items-center md:justify-between">
          <p className="text-sm text-content-secondary">(c) {year} Legent. Enterprise email operations platform.</p>
          <div className="flex flex-wrap items-center gap-4 text-sm text-content-secondary">
            <Link href="/contact" className="hover:text-content-primary">Contact</Link>
            <Link href="/pricing" className="hover:text-content-primary">Pricing</Link>
            <Link href="/blog" className="hover:text-content-primary">Blog</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}

'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { clsx } from 'clsx';
import { Button } from '@/components/ui/Button';
import { useMemo } from 'react';

const NAV_LINKS = [
  { href: '/', label: 'Home' },
  { href: '/features', label: 'Features' },
  { href: '/modules', label: 'Modules' },
  { href: '/pricing', label: 'Pricing' },
  { href: '/about', label: 'About' },
  { href: '/contact', label: 'Contact' },
  { href: '/blog', label: 'Blog' },
];

export function MarketingShell({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const year = useMemo(() => new Date().getFullYear(), []);

  return (
    <div className="min-h-screen bg-surface-primary text-content-primary">
      <header className="sticky top-0 z-40 border-b border-border-default/60 bg-surface-primary/90 backdrop-blur-xl">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
          <Link href="/" className="flex items-center gap-3">
            <span className="inline-flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-brand-500 via-brand-600 to-brand-800 text-white shadow-elevated font-semibold">
              L
            </span>
            <span className="text-lg font-semibold tracking-tight">Legent</span>
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
          <div className="flex items-center gap-2">
            <Link href="/login">
              <Button variant="ghost" size="sm">
                Login
              </Button>
            </Link>
            <Link href="/signup">
              <Button size="sm">Start Free</Button>
            </Link>
          </div>
        </div>
      </header>
      <main>{children}</main>
      <footer className="border-t border-border-default bg-surface-secondary/60">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 px-6 py-10 md:flex-row md:items-center md:justify-between">
          <p className="text-sm text-content-secondary">(c) {year} Legent. Built for high-performance revenue teams.</p>
          <div className="flex items-center gap-4 text-sm text-content-secondary">
            <Link href="/contact" className="hover:text-content-primary">Contact</Link>
            <Link href="/pricing" className="hover:text-content-primary">Pricing</Link>
            <Link href="/blog" className="hover:text-content-primary">Blog</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}


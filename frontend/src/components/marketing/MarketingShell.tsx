'use client';

import Link from 'next/link';
import Image from 'next/image';
import { usePathname } from 'next/navigation';
import { clsx } from 'clsx';
import { Button } from '@/components/ui/Button';
import { useEffect, useMemo, useState } from 'react';
import { ArrowRight, Menu, Moon, Sun, X } from 'lucide-react';

const NAV_LINKS = [
  { href: '/', label: 'Home' },
  { href: '/features', label: 'Features' },
  { href: '/modules', label: 'Modules' },
  { href: '/pricing', label: 'Pricing' },
  { href: '/about', label: 'About' },
  { href: '/contact', label: 'Contact' },
  { href: '/blog', label: 'Blog' },
];

type PublicTheme = 'dark' | 'light';

const PUBLIC_THEME_KEY = 'legent_public_theme';

export function MarketingShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const year = useMemo(() => new Date().getFullYear(), []);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [theme, setTheme] = useState<PublicTheme>('light');

  useEffect(() => {
    const stored = window.localStorage.getItem(PUBLIC_THEME_KEY);
    setTheme(stored === 'dark' ? 'dark' : 'light');
  }, []);

  const toggleTheme = () => {
    setTheme((current) => {
      const next = current === 'dark' ? 'light' : 'dark';
      window.localStorage.setItem(PUBLIC_THEME_KEY, next);
      return next;
    });
  };

  return (
    <div className={clsx('public-site min-h-screen overflow-hidden', theme === 'light' ? 'public-light' : 'public-dark')}>
      <div className="pointer-events-none fixed inset-0 z-0">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_20%_8%,rgba(168,85,247,0.18),transparent_34%),radial-gradient(circle_at_78%_2%,rgba(236,72,153,0.12),transparent_30%)]" />
        <div className="premium-purple-grid absolute inset-0 opacity-40" />
      </div>

      <header className="public-border sticky top-0 z-40 border-b bg-[var(--public-nav)] backdrop-blur-2xl">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6">
          <Link href="/" className="flex items-center gap-3" onClick={() => setMobileOpen(false)}>
            <Image src="/legent-logo.svg" alt="" width={40} height={40} className="h-10 w-10 rounded-xl shadow-[0_0_32px_rgba(109,40,217,0.28)]" priority />
            <span className="leading-tight">
              <span className="public-heading block text-base font-semibold tracking-normal">Legent</span>
              <span className="public-muted block text-[10px] font-medium uppercase tracking-[0.2em]">Email Ops</span>
            </span>
          </Link>
          <nav className="hidden items-center gap-1 lg:flex">
            {NAV_LINKS.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className={clsx(
                  'rounded-full px-3 py-2 text-sm font-medium transition-colors',
                  pathname === item.href
                    ? 'bg-[var(--public-panel-strong)] text-[var(--public-text)]'
                    : 'public-muted hover:bg-[var(--public-panel)] hover:text-[var(--public-text)]'
                )}
              >
                {item.label}
              </Link>
            ))}
          </nav>
          <div className="hidden items-center gap-2 sm:flex">
            <button
              type="button"
              onClick={toggleTheme}
              aria-label="Toggle public theme"
              className="public-border rounded-xl border bg-[var(--public-panel)] p-2 text-[var(--public-text)] transition hover:-translate-y-0.5 hover:bg-[var(--public-panel-strong)] focus:outline-none focus:ring-2 focus:ring-violet-400"
            >
              {theme === 'dark' ? <Sun size={17} /> : <Moon size={17} />}
            </button>
            <Link href="/login"><Button variant="ghost" size="sm" className="public-button-ghost">Login</Button></Link>
            <Link href="/signup"><Button size="sm" className="public-button-primary" icon={<ArrowRight size={15} />}>Start Free</Button></Link>
          </div>
          <button
            className="rounded-lg p-2 text-[var(--public-text)] hover:bg-[var(--public-panel)] lg:hidden"
            onClick={() => setMobileOpen((open) => !open)}
            aria-label="Toggle navigation"
            aria-expanded={mobileOpen}
          >
            {mobileOpen ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>
        {mobileOpen && (
          <div className="public-border border-t bg-[var(--public-nav)] px-4 py-3 lg:hidden">
            <nav className="grid gap-1">
              {NAV_LINKS.map((item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  onClick={() => setMobileOpen(false)}
                  className={clsx(
                    'rounded-xl px-3 py-2 text-sm font-medium',
                    pathname === item.href ? 'bg-[var(--public-panel-strong)] text-[var(--public-text)]' : 'public-muted hover:bg-[var(--public-panel)]'
                  )}
                >
                  {item.label}
                </Link>
              ))}
            </nav>
            <div className="mt-3 grid grid-cols-2 gap-2 sm:hidden">
              <button
                type="button"
                onClick={toggleTheme}
                aria-label="Toggle public theme"
                className="public-border inline-flex h-10 items-center justify-center rounded-xl border bg-[var(--public-panel)] text-[var(--public-text)]"
              >
                {theme === 'dark' ? <Sun size={17} /> : <Moon size={17} />}
              </button>
              <Link href="/login"><Button variant="secondary" size="sm" className="public-button-secondary w-full">Login</Button></Link>
              <Link href="/signup" className="col-span-2"><Button size="sm" className="public-button-primary w-full">Start Free</Button></Link>
            </div>
          </div>
        )}
      </header>

      <main className="relative z-10">{children}</main>

      <footer className="public-border relative z-10 border-t bg-[var(--public-nav)]">
        <div className="mx-auto grid max-w-7xl gap-8 px-6 py-10 md:grid-cols-[1fr_1.2fr] md:items-start">
          <div>
            <p className="public-heading text-sm font-semibold">Legent Email Operations</p>
            <p className="public-muted mt-1 max-w-lg text-sm">
              Premium email operations for teams that need beauty, speed, governance, and inbox control.
            </p>
          </div>
          <div className="public-muted grid gap-6 text-sm sm:grid-cols-3">
            <div className="grid gap-2">
              <p className="public-heading font-semibold">Platform</p>
              <Link href="/features" className="hover:text-[var(--public-text)]">Features</Link>
              <Link href="/modules" className="hover:text-[var(--public-text)]">Modules</Link>
              <Link href="/pricing" className="hover:text-[var(--public-text)]">Pricing INR</Link>
            </div>
            <div className="grid gap-2">
              <p className="public-heading font-semibold">Company</p>
              <Link href="/about" className="hover:text-[var(--public-text)]">About</Link>
              <Link href="/contact" className="hover:text-[var(--public-text)]">Contact</Link>
              <Link href="/blog" className="hover:text-[var(--public-text)]">Blog</Link>
            </div>
            <div className="grid gap-2">
              <p className="public-heading font-semibold">Access</p>
              <Link href="/login" className="hover:text-[var(--public-text)]">Login</Link>
              <Link href="/signup" className="hover:text-[var(--public-text)]">Start Free</Link>
              <span>(c) {year}</span>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}

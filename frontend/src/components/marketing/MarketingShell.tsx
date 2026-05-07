'use client';

import Image from 'next/image';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { clsx } from 'clsx';
import { ArrowRight, Menu, Moon, Sun, X } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

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

  useEffect(() => {
    setMobileOpen(false);
  }, [pathname]);

  const toggleTheme = () => {
    setTheme((current) => {
      const next = current === 'dark' ? 'light' : 'dark';
      window.localStorage.setItem(PUBLIC_THEME_KEY, next);
      return next;
    });
  };

  return (
    <div className={clsx('public-site min-h-screen overflow-x-hidden', theme === 'light' ? 'public-light' : 'public-dark')}>
      <a
        href="#public-main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[80] focus:rounded-xl focus:bg-[var(--public-text)] focus:px-4 focus:py-2 focus:text-[var(--public-bg)]"
      >
        Skip to content
      </a>
      <div className="pointer-events-none fixed inset-0 z-0">
        <div className="public-atmosphere absolute inset-0" />
        <div className="premium-purple-grid absolute inset-0 opacity-30" />
      </div>

      <header className="public-border sticky top-0 z-50 border-b bg-[var(--public-nav)] backdrop-blur-2xl">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6">
          <Link href="/" className="group flex items-center gap-3 rounded-xl focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)]">
            <Image src="/legent-logo.svg" alt="" width={40} height={40} className="h-10 w-10 rounded-xl shadow-[0_0_32px_rgba(109,40,217,0.24)]" priority />
            <span className="leading-tight">
              <span className="public-heading block text-base font-semibold tracking-normal">Legent</span>
              <span className="public-muted block text-[10px] font-medium uppercase tracking-[0.2em]">Email Ops</span>
            </span>
          </Link>

          <nav aria-label="Primary navigation" className="hidden items-center gap-1 lg:flex">
            {NAV_LINKS.map((item) => {
              const active = pathname === item.href || (item.href !== '/' && pathname.startsWith(item.href));
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={clsx(
                    'rounded-full px-3 py-2 text-sm font-medium transition duration-200 focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)]',
                    active
                      ? 'bg-[var(--public-panel-strong)] text-[var(--public-text)] shadow-sm'
                      : 'public-muted hover:-translate-y-0.5 hover:bg-[var(--public-panel)] hover:text-[var(--public-text)]'
                  )}
                >
                  {item.label}
                </Link>
              );
            })}
          </nav>

          <div className="hidden items-center gap-2 sm:flex">
            <ThemeButton theme={theme} toggleTheme={toggleTheme} />
            <Link href="/login" className="public-button-ghost rounded-xl px-4 py-2 text-sm font-semibold transition hover:-translate-y-0.5 focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)]">
              Login
            </Link>
            <Link href="/signup" className="public-button-primary inline-flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-semibold transition hover:-translate-y-0.5 focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)]">
              Start Free <ArrowRight size={15} />
            </Link>
          </div>

          <button
            className="rounded-xl p-2 text-[var(--public-text)] transition hover:bg-[var(--public-panel)] focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)] lg:hidden"
            onClick={() => setMobileOpen((open) => !open)}
            aria-label="Toggle navigation"
            aria-expanded={mobileOpen}
            aria-controls="public-mobile-menu"
          >
            {mobileOpen ? <X size={21} /> : <Menu size={21} />}
          </button>
        </div>

        {mobileOpen ? (
          <div id="public-mobile-menu" className="public-border border-t bg-[var(--public-nav)] px-4 py-4 lg:hidden">
            <nav aria-label="Mobile navigation" className="grid gap-1">
              {NAV_LINKS.map((item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  className={clsx(
                    'rounded-xl px-3 py-2 text-sm font-medium transition focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)]',
                    pathname === item.href ? 'bg-[var(--public-panel-strong)] text-[var(--public-text)]' : 'public-muted hover:bg-[var(--public-panel)]'
                  )}
                >
                  {item.label}
                </Link>
              ))}
            </nav>
            <div className="mt-3 grid grid-cols-2 gap-2 sm:hidden">
              <ThemeButton theme={theme} toggleTheme={toggleTheme} />
              <Link href="/login" className="public-button-secondary inline-flex h-10 items-center justify-center rounded-xl text-sm font-semibold">
                Login
              </Link>
              <Link href="/signup" className="public-button-primary col-span-2 inline-flex h-10 items-center justify-center rounded-xl text-sm font-semibold">
                Start Free
              </Link>
            </div>
          </div>
        ) : null}
      </header>

      <main id="public-main" className="relative z-10">
        {children}
      </main>

      <footer className="public-border relative z-10 border-t bg-[var(--public-nav)]">
        <div className="mx-auto grid max-w-7xl gap-8 px-6 py-12 md:grid-cols-2 md:items-start">
          <div>
            <p className="public-heading text-lg font-semibold">Legent Email Operations</p>
            <p className="public-muted mt-2 max-w-xl text-sm leading-6">
              A premium command system for lifecycle teams that need orchestration, governance, inbox safety, and measurable growth in one workspace.
            </p>
          </div>
          <div className="public-muted grid gap-6 text-sm sm:grid-cols-3">
            <FooterColumn title="Platform" links={[['Features', '/features'], ['Modules', '/modules'], ['Pricing INR', '/pricing']]} />
            <FooterColumn title="Company" links={[['About', '/about'], ['Contact', '/contact'], ['Blog', '/blog']]} />
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

function ThemeButton({ theme, toggleTheme }: { theme: PublicTheme; toggleTheme: () => void }) {
  return (
    <button
      type="button"
      onClick={toggleTheme}
      aria-label="Toggle public theme"
      className="public-border inline-flex h-10 items-center justify-center rounded-xl border bg-[var(--public-panel)] px-3 text-[var(--public-text)] transition hover:-translate-y-0.5 hover:bg-[var(--public-panel-strong)] focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)]"
    >
      {theme === 'dark' ? <Sun size={17} /> : <Moon size={17} />}
    </button>
  );
}

function FooterColumn({ title, links }: { title: string; links: Array<[string, string]> }) {
  return (
    <div className="grid gap-2">
      <p className="public-heading font-semibold">{title}</p>
      {links.map(([label, href]) => (
        <Link key={href} href={href} className="hover:text-[var(--public-text)]">
          {label}
        </Link>
      ))}
    </div>
  );
}

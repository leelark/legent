'use client';

import { clsx } from 'clsx';
import type { ReactNode } from 'react';

export function PageHeader({
  eyebrow,
  title,
  description,
  action,
  className,
}: {
  eyebrow?: string;
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={clsx('flex flex-col gap-4 md:flex-row md:items-end md:justify-between', className)}>
      <div className="min-w-0">
        {eyebrow ? <p className="text-xs font-semibold uppercase tracking-[0.22em] text-brand-300">{eyebrow}</p> : null}
        <h1 className="mt-1 text-balance text-2xl font-semibold tracking-normal text-content-primary md:text-3xl">{title}</h1>
        {description ? <p className="mt-2 max-w-3xl text-sm leading-6 text-content-secondary">{description}</p> : null}
      </div>
      {action ? <div className="flex shrink-0 flex-wrap items-center gap-2">{action}</div> : null}
    </div>
  );
}

export function MetricCard({
  label,
  value,
  helper,
  icon,
  accent = 'brand',
}: {
  label: string;
  value: ReactNode;
  helper?: string;
  icon?: ReactNode;
  accent?: 'brand' | 'success' | 'warning' | 'danger';
}) {
  const accentClass = {
    brand: 'border-brand-500/20 bg-brand-500/10 text-brand-300',
    success: 'border-success/20 bg-success/10 text-success',
    warning: 'border-warning/20 bg-warning/10 text-warning',
    danger: 'border-danger/20 bg-danger/10 text-danger',
  }[accent];

  return (
    <div className="rounded-xl border border-border-default bg-surface-elevated/95 p-5 shadow-[0_18px_45px_rgba(76,29,149,0.08),inset_0_1px_0_rgba(255,255,255,0.78)] backdrop-blur-xl dark:shadow-[0_18px_48px_rgba(0,0,0,0.28),inset_0_1px_0_rgba(255,255,255,0.05)]">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-content-secondary">{label}</p>
          <p className="mt-2 truncate text-3xl font-semibold text-content-primary">{value}</p>
          {helper ? <p className="mt-1 text-xs text-content-muted">{helper}</p> : null}
        </div>
        {icon ? <div className={clsx('rounded-xl border p-2', accentClass)}>{icon}</div> : null}
      </div>
    </div>
  );
}

export function Panel({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <section className={clsx('rounded-xl border border-border-default bg-surface-elevated/95 p-5 shadow-[0_18px_45px_rgba(76,29,149,0.08),inset_0_1px_0_rgba(255,255,255,0.78)] backdrop-blur-xl dark:shadow-[0_18px_48px_rgba(0,0,0,0.28),inset_0_1px_0_rgba(255,255,255,0.05)]', className)}>
      {children}
    </section>
  );
}

export function ActionBar({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={clsx('flex flex-col gap-3 rounded-xl border border-border-default bg-surface-elevated/90 p-3 shadow-[0_14px_36px_rgba(76,29,149,0.07)] backdrop-blur-xl sm:flex-row sm:items-center sm:justify-between', className)}>
      {children}
    </div>
  );
}

export function ResponsiveTableShell({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={clsx('overflow-x-auto rounded-xl border border-border-default bg-surface-elevated/95 shadow-[0_18px_45px_rgba(76,29,149,0.08)] backdrop-blur-xl', className)}>
      {children}
    </div>
  );
}

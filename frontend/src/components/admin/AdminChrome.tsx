'use client';

import { clsx } from 'clsx';
import type { ReactNode } from 'react';
import { Card, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Skeleton } from '@/components/ui/Skeleton';

type AdminPanelProps = {
  title: string;
  subtitle?: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
};

export function AdminPanel({ title, subtitle, action, children, className }: AdminPanelProps) {
  return (
    <Card className={clsx('overflow-hidden p-0', className)}>
      <div className="border-b border-border-default bg-surface-elevated/70 p-5">
        <CardHeader title={title} subtitle={subtitle} action={action} className="mb-0" />
      </div>
      <div className="p-5">{children}</div>
    </Card>
  );
}

type AdminMetricCardProps = {
  label: string;
  value: string | number;
  description?: string;
  tone?: 'brand' | 'success' | 'warning' | 'danger' | 'info' | 'neutral';
};

const metricTones: Record<NonNullable<AdminMetricCardProps['tone']>, string> = {
  brand: 'from-brand-50 to-fuchsia-50 text-brand-700 dark:from-brand-900/30 dark:to-fuchsia-900/20 dark:text-brand-300',
  success: 'from-emerald-50 to-teal-50 text-emerald-700 dark:from-emerald-900/20 dark:to-teal-900/10 dark:text-emerald-300',
  warning: 'from-amber-50 to-orange-50 text-amber-700 dark:from-amber-900/20 dark:to-orange-900/10 dark:text-amber-300',
  danger: 'from-red-50 to-rose-50 text-red-700 dark:from-red-900/20 dark:to-rose-900/10 dark:text-red-300',
  info: 'from-blue-50 to-indigo-50 text-blue-700 dark:from-blue-900/20 dark:to-indigo-900/10 dark:text-blue-300',
  neutral: 'from-surface-secondary to-surface-elevated text-content-primary',
};

export function AdminMetricCard({ label, value, description, tone = 'neutral' }: AdminMetricCardProps) {
  return (
    <div className="rounded-xl border border-border-default bg-surface-primary p-4 shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:border-brand-300/60 hover:shadow-lg">
      <div className={clsx('mb-3 inline-flex rounded-lg bg-gradient-to-br px-2.5 py-1 text-xs font-semibold', metricTones[tone])}>
        {label}
      </div>
      <p className="text-2xl font-semibold text-content-primary">{value}</p>
      {description ? <p className="mt-1 text-xs text-content-secondary">{description}</p> : null}
    </div>
  );
}

export function StatusPill({ status }: { status?: string | null }) {
  const normalized = (status || 'UNKNOWN').toUpperCase();
  const variant: 'default' | 'success' | 'warning' | 'danger' | 'info' | 'brand' =
    normalized.includes('FAIL') || normalized.includes('ERROR') || normalized.includes('CLOSED')
      ? 'danger'
      : normalized.includes('PENDING') || normalized.includes('REVIEW') || normalized.includes('PARTIAL')
        ? 'warning'
        : normalized.includes('ACTIVE') || normalized.includes('SUCCESS') || normalized.includes('COMPLETE') || normalized.includes('CONTACTED')
          ? 'success'
          : normalized.includes('RECEIVED')
            ? 'info'
            : 'default';

  return <Badge variant={variant}>{normalized.replace(/_/g, ' ')}</Badge>;
}

export function AdminEmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="rounded-xl border border-dashed border-border-default bg-surface-secondary/70 px-4 py-10 text-center">
      <p className="text-sm font-semibold text-content-primary">{title}</p>
      {description ? <p className="mx-auto mt-1 max-w-md text-sm text-content-secondary">{description}</p> : null}
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  );
}

export function AdminTableShell({ children }: { children: ReactNode }) {
  return (
    <div className="overflow-hidden rounded-xl border border-border-default bg-surface-primary">
      <div className="overflow-x-auto">{children}</div>
    </div>
  );
}

export function AdminSkeletonRows({ rows = 4 }: { rows?: number }) {
  return (
    <div className="space-y-3">
      {Array.from({ length: rows }).map((_, index) => (
        <Skeleton key={index} variant="rounded" height={56} width="100%" />
      ))}
    </div>
  );
}

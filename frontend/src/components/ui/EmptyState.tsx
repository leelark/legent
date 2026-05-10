'use client';

import { clsx } from 'clsx';
import { Warning, MagnifyingGlass, FolderOpen } from '@phosphor-icons/react';
import type { ReactNode } from 'react';

type EmptyStateType = 'empty' | 'search' | 'error';

interface EmptyStateProps {
  type?: EmptyStateType;
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
}

const icons: Record<EmptyStateType, typeof Warning> = {
  empty: FolderOpen,
  search: MagnifyingGlass,
  error: Warning,
};

export function EmptyState({
  type = 'empty',
  title,
  description,
  action,
  className,
}: EmptyStateProps) {
  const Icon = icons[type];

  return (
    <div
      className={clsx(
        'animate-fade-in flex flex-col items-center justify-center rounded-xl border border-dashed border-border-default bg-surface-secondary/55 px-4 py-12 text-center shadow-inner',
        className
      )}
    >
      <div className="mb-4 rounded-xl border border-border-default bg-surface-elevated/90 p-4 shadow-sm">
        <Icon size={32} weight="duotone" className={clsx(type === 'error' ? 'text-danger' : 'text-brand-600 dark:text-brand-300')} />
      </div>
      <h3 className="text-base font-semibold text-content-primary">{title}</h3>
      {description && (
        <p className="mt-1.5 max-w-md text-sm leading-6 text-content-secondary">
          {description}
        </p>
      )}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

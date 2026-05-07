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
        'flex flex-col items-center justify-center px-4 py-16 text-center animate-fade-in',
        className
      )}
    >
      <div className="mb-4 rounded-lg border border-border-default bg-surface-secondary p-4">
        <Icon size={32} weight="duotone" className="text-content-muted" />
      </div>
      <h3 className="text-base font-semibold text-content-primary">{title}</h3>
      {description && (
        <p className="mt-1.5 max-w-sm text-sm text-content-secondary">
          {description}
        </p>
      )}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

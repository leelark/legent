'use client';

import { clsx } from 'clsx';
import type { HTMLAttributes, ReactNode } from 'react';

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
  className?: string;
  hover?: boolean;
}

export function Card({ children, className, hover = false, onClick, ...props }: CardProps) {
  return (
    <div
      className={clsx(
        'rounded-lg border border-border-default bg-surface-elevated p-5 shadow-soft',
        'transition-all duration-200',
        hover && 'cursor-pointer hover:-translate-y-0.5 hover:border-brand-200 hover:shadow-elevated dark:hover:border-brand-800',
        className
      )}
      onClick={onClick}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
      {...props}
    >
      {children}
    </div>
  );
}

interface CardHeaderProps {
  title?: string;
  subtitle?: string;
  action?: ReactNode;
  children?: ReactNode;
  className?: string;
}

export function CardHeader({ title, subtitle, action, children, className }: CardHeaderProps) {
  if (children) {
    return (
      <div className={clsx('flex flex-col space-y-1.5', className)}>
        {children}
      </div>
    );
  }

  return (
    <div className={clsx('flex items-start justify-between mb-4', className)}>
      <div>
        <h3 className="text-base font-semibold text-content-primary">{title}</h3>
        {subtitle && (
          <p className="mt-0.5 text-sm text-content-secondary">{subtitle}</p>
        )}
      </div>
      {action && <div>{action}</div>}
    </div>
  );
}

export function CardTitle({ className, children }: { className?: string; children?: ReactNode }) {
  return (
    <h3 className={clsx('text-base font-semibold leading-tight tracking-normal text-content-primary', className)}>
      {children}
    </h3>
  );
}

export function CardDescription({ className, children }: { className?: string; children?: ReactNode }) {
  return (
    <p className={clsx('text-sm text-content-secondary', className)}>
      {children}
    </p>
  );
}

export function CardContent({ className, children }: { className?: string; children?: ReactNode }) {
  return (
    <div className={clsx('pt-0', className)}>
      {children}
    </div>
  );
}

export function CardFooter({ className, children }: { className?: string; children?: ReactNode }) {
  return (
    <div className={clsx('flex items-center pt-0', className)}>
      {children}
    </div>
  );
}

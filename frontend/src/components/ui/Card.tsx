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
        'rounded-xl border border-border-default bg-surface-elevated/95 bg-[linear-gradient(145deg,rgba(255,255,255,0.80),rgba(255,255,255,0.54))] p-5 shadow-[0_18px_45px_rgba(76,29,149,0.08),inset_0_1px_0_rgba(255,255,255,0.78)] backdrop-blur-xl dark:bg-[linear-gradient(145deg,rgba(26,15,44,0.96),rgba(18,9,31,0.78))]',
        'transition-all duration-200 dark:shadow-[0_18px_48px_rgba(0,0,0,0.28),inset_0_1px_0_rgba(255,255,255,0.05)]',
        hover && 'cursor-pointer hover:-translate-y-0.5 hover:border-brand-300/70 hover:shadow-[0_22px_56px_rgba(76,29,149,0.14)] dark:hover:border-brand-500/50',
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
    <div className={clsx('mb-4 flex items-start justify-between gap-4', className)}>
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

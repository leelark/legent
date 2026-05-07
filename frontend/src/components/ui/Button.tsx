'use client';

import { clsx } from 'clsx';
import type { ButtonHTMLAttributes, ReactNode } from 'react';

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'outline';
type ButtonSize = 'sm' | 'md' | 'lg' | 'icon';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  icon?: ReactNode;
  children: ReactNode;
}

const variantStyles: Record<ButtonVariant, string> = {
  primary:
    'bg-gradient-to-r from-brand-700 via-brand-600 to-fuchsia-500 text-white shadow-[0_12px_32px_rgba(126,34,206,0.24)] hover:from-brand-800 hover:via-brand-700 hover:to-fuchsia-600 active:from-brand-900',
  secondary:
    'border border-border-default bg-surface-elevated/80 text-content-primary shadow-sm hover:border-border-strong hover:bg-surface-secondary active:bg-surface-elevated',
  ghost:
    'text-content-secondary hover:bg-surface-secondary hover:text-content-primary',
  danger:
    'bg-danger text-white hover:bg-red-600 active:bg-red-700',
  outline:
    'bg-transparent border-2 border-brand-500 text-brand-600 hover:bg-brand-50 active:bg-brand-100',
};

const sizeStyles: Record<ButtonSize, string> = {
  sm: 'px-3 py-1.5 text-xs gap-1.5',
  md: 'px-4 py-2 text-sm gap-2',
  lg: 'px-5 py-2.5 text-base gap-2.5',
  icon: 'h-9 w-9 p-2',
};

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  icon,
  children,
  className,
  disabled,
  ...props
}: ButtonProps) {
  const buttonType = props.type ?? 'button';

  return (
    <button
      className={clsx(
        'inline-flex min-w-0 items-center justify-center whitespace-nowrap rounded-xl font-semibold',
        'transition-all duration-150 hover:-translate-y-px focus:outline-none focus:ring-2 focus:ring-accent/40 focus:ring-offset-1 focus:ring-offset-surface-primary',
        'disabled:cursor-not-allowed disabled:opacity-50',
        variantStyles[variant],
        sizeStyles[size],
        className
      )}
      disabled={disabled || loading}
      type={buttonType}
      {...props}
    >
      {loading ? (
        <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor"
            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
      ) : icon ? (
        <span className="flex-shrink-0">{icon}</span>
      ) : null}
      <span className="truncate">{children}</span>
    </button>
  );
}

'use client';

import { clsx } from 'clsx';
import { forwardRef, type InputHTMLAttributes } from 'react';

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  hint?: string;
  fullWidth?: boolean;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ label, error, hint, className, id, ...props }, ref) => {
    const inputId = id || label?.toLowerCase().replace(/\s+/g, '-');

    return (
      <div className="space-y-1.5">
        {label && (
          <label
            htmlFor={inputId}
            className="block text-sm font-medium text-content-primary"
          >
            {label}
          </label>
        )}
        <input
          ref={ref}
          id={inputId}
          className={clsx(
            'w-full rounded-lg border bg-surface-primary px-3 py-2 text-sm text-content-primary',
            'placeholder-content-muted transition-all duration-150',
            'focus:outline-none focus:ring-2 focus:ring-offset-1',
            error
              ? 'border-danger focus:border-danger focus:ring-danger/30'
              : 'border-border-default focus:border-accent focus:ring-accent/30',
            className
          )}
          {...props}
        />
        {error && (
          <p className="text-xs text-danger animate-slide-up">{error}</p>
        )}
        {hint && !error && (
          <p className="text-xs text-content-muted">{hint}</p>
        )}
      </div>
    );
  }
);

Input.displayName = 'Input';

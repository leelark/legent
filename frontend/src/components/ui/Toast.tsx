'use client';

import { useEffect, useState, useCallback, createContext, useContext, type ReactNode } from 'react';
import { clsx } from 'clsx';
import { X, CheckCircle, Warning, Info, WarningCircle } from '@phosphor-icons/react';

// ── Types ──
type ToastType = 'success' | 'error' | 'warning' | 'info';

interface Toast {
  id: string;
  type: ToastType;
  title: string;
  message?: string;
  duration?: number;
}

interface ToastContextType {
  addToast: (toast: Omit<Toast, 'id'>) => void;
  removeToast: (id: string) => void;
}

// ── Context ──
const ToastContext = createContext<ToastContextType | null>(null);

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used within ToastProvider');
  return ctx;
}

// ── Provider ──
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const addToast = useCallback((toast: Omit<Toast, 'id'>) => {
    const id = `toast-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
    setToasts((prev) => [...prev, { ...toast, id }]);
  }, []);

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  return (
    <ToastContext.Provider value={{ addToast, removeToast }}>
      {children}
      {/* Toast Container */}
      <div className="fixed bottom-4 right-4 z-[100] flex flex-col gap-2 w-96">
        {toasts.map((toast) => (
          <ToastItem key={toast.id} toast={toast} onDismiss={removeToast} />
        ))}
      </div>
    </ToastContext.Provider>
  );
}

// ── Toast Item ──
const iconMap: Record<ToastType, typeof CheckCircle> = {
  success: CheckCircle,
  error: WarningCircle,
  warning: Warning,
  info: Info,
};

const styleMap: Record<ToastType, string> = {
  success: 'border-emerald-200 dark:border-emerald-800 bg-emerald-50 dark:bg-emerald-900/30',
  error: 'border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/30',
  warning: 'border-amber-200 dark:border-amber-800 bg-amber-50 dark:bg-amber-900/30',
  info: 'border-blue-200 dark:border-blue-800 bg-blue-50 dark:bg-blue-900/30',
};

const iconColorMap: Record<ToastType, string> = {
  success: 'text-emerald-600 dark:text-emerald-400',
  error: 'text-red-600 dark:text-red-400',
  warning: 'text-amber-600 dark:text-amber-400',
  info: 'text-blue-600 dark:text-blue-400',
};

function ToastItem({ toast, onDismiss }: { toast: Toast; onDismiss: (id: string) => void }) {
  const Icon = iconMap[toast.type];

  useEffect(() => {
    const timer = setTimeout(() => onDismiss(toast.id), toast.duration ?? 5000);
    return () => clearTimeout(timer);
  }, [toast.id, toast.duration, onDismiss]);

  return (
    <div
      className={clsx(
        'flex items-start gap-3 rounded-xl border p-4 shadow-elevated animate-slide-up',
        styleMap[toast.type]
      )}
      role="alert"
    >
      <Icon size={20} weight="fill" className={clsx('flex-shrink-0 mt-0.5', iconColorMap[toast.type])} />
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-content-primary">{toast.title}</p>
        {toast.message && (
          <p className="mt-0.5 text-xs text-content-secondary">{toast.message}</p>
        )}
      </div>
      <button
        onClick={() => onDismiss(toast.id)}
        className="flex-shrink-0 rounded p-0.5 text-content-muted hover:text-content-primary transition-colors"
      >
        <X size={14} />
      </button>
    </div>
  );
}

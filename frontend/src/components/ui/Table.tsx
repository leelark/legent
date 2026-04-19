'use client';

import { clsx } from 'clsx';
import { Spinner } from './LoadingOverlay';
import { EmptyState } from './EmptyState';

interface Column<T> {
  key: string;
  header: string;
  width?: string;
  render?: (row: T) => React.ReactNode;
}

interface TableProps<T> {
  columns?: Column<T>[];
  data?: T[];
  loading?: boolean;
  emptyMessage?: string;
  onRowClick?: (row: T) => void;
  rowKey?: (row: T) => string;
}

export function Table<T>({
  columns,
  data,
  loading = false,
  emptyMessage = 'No data found',
  onRowClick,
  rowKey,
  children,
  className,
}: TableProps<T> & { children?: React.ReactNode; className?: string }) {
  if (children) {
    return (
      <div className="overflow-x-auto rounded-lg border border-border-default">
        <table className={clsx('w-full text-sm', className)}>
          {children}
        </table>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Spinner size="md" />
      </div>
    );
  }

  if (data && data.length === 0) {
    return <EmptyState type="empty" title={emptyMessage} />;
  }

  return (
    <div className="overflow-x-auto rounded-lg border border-border-default">
      <table className={clsx('w-full text-sm', className)}>
        <thead>
          <tr className="border-b border-border-default bg-surface-secondary">
            {columns?.map((col: any) => (
              <th
                key={col.key}
                className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-content-secondary"
                style={{ width: col.width }}
              >
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-border-default">
          {data?.map((row: any) => (
            <tr
              key={rowKey!(row)}
              className={clsx(
                'transition-colors',
                onRowClick
                  ? 'cursor-pointer hover:bg-surface-secondary'
                  : ''
              )}
              onClick={() => onRowClick?.(row)}
            >
              {columns?.map((col: any) => (
                <td
                  key={col.key}
                  className="px-4 py-3 text-content-primary"
                >
                  {col.render
                    ? col.render(row)
                    : String((row as any)[col.key] ?? '')}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function TableHeader({ className, children }: { className?: string; children?: React.ReactNode }) {
  return <thead className={clsx('bg-surface-secondary border-b border-border-default', className)}>{children}</thead>;
}

export function TableBody({ className, children }: { className?: string; children?: React.ReactNode }) {
  return <tbody className={clsx('divide-y divide-border-default', className)}>{children}</tbody>;
}

export function TableFooter({ className, children }: { className?: string; children?: React.ReactNode }) {
  return <tfoot className={clsx('bg-surface-secondary font-medium', className)}>{children}</tfoot>;
}

export function TableRow({ className, children, onClick }: { className?: string; children?: React.ReactNode; onClick?: () => void }) {
  return (
    <tr 
      className={clsx('border-b border-border-default transition-colors hover:bg-surface-secondary/50', className)}
      onClick={onClick}
    >
      {children}
    </tr>
  );
}

export function TableHead({ className, children }: { className?: string; children?: React.ReactNode }) {
  return (
    <th className={clsx('px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-content-secondary align-middle', className)}>
      {children}
    </th>
  );
}

export function TableCell({ className, children }: { className?: string; children?: React.ReactNode }) {
  return (
    <td className={clsx('px-4 py-3 align-middle text-content-primary', className)}>
      {children}
    </td>
  );
}

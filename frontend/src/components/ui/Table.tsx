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
  selectable?: boolean;
  selectedRowKeys?: string[];
  onSelectionChange?: (keys: string[]) => void;
}

export function Table<T>({
  columns,
  data,
  loading = false,
  emptyMessage = 'No data found',
  onRowClick,
  rowKey,
  selectable = false,
  selectedRowKeys = [],
  onSelectionChange,
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

  const resolveRowKey = (row: T) => {
    if (rowKey) {
      return rowKey(row);
    }
    const fallback = (row as any)?.id;
    return typeof fallback === 'string' ? fallback : String(fallback ?? '');
  };

  const allRowKeys = (data || []).map(resolveRowKey).filter(Boolean);
  const allSelected = selectable && allRowKeys.length > 0 && allRowKeys.every((key) => selectedRowKeys.includes(key));

  const toggleAll = (checked: boolean) => {
    if (!onSelectionChange) {
      return;
    }
    onSelectionChange(checked ? allRowKeys : []);
  };

  const toggleOne = (rowId: string, checked: boolean) => {
    if (!onSelectionChange) {
      return;
    }
    if (checked) {
      onSelectionChange(Array.from(new Set([...selectedRowKeys, rowId])));
      return;
    }
    onSelectionChange(selectedRowKeys.filter((key) => key !== rowId));
  };

  return (
    <div className="overflow-x-auto rounded-lg border border-border-default">
      <table className={clsx('w-full text-sm', className)}>
        <thead>
          <tr className="border-b border-border-default bg-surface-secondary">
            {selectable && (
              <th className="w-10 px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-content-secondary">
                <input
                  type="checkbox"
                  checked={allSelected}
                  onChange={(e) => toggleAll(e.target.checked)}
                  aria-label="Select all rows"
                />
              </th>
            )}
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
            (() => {
              const currentRowKey = resolveRowKey(row);
              return (
                <tr
                  key={currentRowKey}
                  className={clsx(
                    'transition-colors',
                    onRowClick
                      ? 'cursor-pointer hover:bg-surface-secondary'
                      : ''
                  )}
                  onClick={() => onRowClick?.(row)}
                >
                  {selectable && (
                    <td className="px-4 py-3 text-content-primary">
                      <input
                        type="checkbox"
                        checked={selectedRowKeys.includes(currentRowKey)}
                        onChange={(e) => toggleOne(currentRowKey, e.target.checked)}
                        onClick={(e) => e.stopPropagation()}
                        aria-label="Select row"
                      />
                    </td>
                  )}
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
              );
            })()
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function TableHeader({ className, children, ...props }: React.HTMLAttributes<HTMLTableSectionElement>) {
  return <thead className={clsx('bg-surface-secondary border-b border-border-default', className)} {...props}>{children}</thead>;
}

export function TableBody({ className, children, ...props }: React.HTMLAttributes<HTMLTableSectionElement>) {
  return <tbody className={clsx('divide-y divide-border-default', className)} {...props}>{children}</tbody>;
}

export function TableFooter({ className, children, ...props }: React.HTMLAttributes<HTMLTableSectionElement>) {
  return <tfoot className={clsx('bg-surface-secondary font-medium', className)} {...props}>{children}</tfoot>;
}

export function TableRow({ className, children, ...props }: React.HTMLAttributes<HTMLTableRowElement>) {
  return (
    <tr 
      className={clsx('border-b border-border-default transition-colors hover:bg-surface-secondary/50', className)}
      {...props}
    >
      {children}
    </tr>
  );
}

export function TableHead({ className, children, ...props }: React.ThHTMLAttributes<HTMLTableCellElement>) {
  return (
    <th className={clsx('px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-content-secondary align-middle', className)} {...props}>
      {children}
    </th>
  );
}

export function TableCell({ className, children, ...props }: React.TdHTMLAttributes<HTMLTableCellElement>) {
  return (
    <td className={clsx('px-4 py-3 align-middle text-content-primary', className)} {...props}>
      {children}
    </td>
  );
}

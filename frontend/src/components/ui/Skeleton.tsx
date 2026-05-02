import { clsx } from 'clsx';

interface SkeletonProps {
  className?: string;
  variant?: 'text' | 'circular' | 'rectangular' | 'rounded';
  width?: string | number;
  height?: string | number;
  count?: number;
}

export function Skeleton({
  className,
  variant = 'text',
  width,
  height,
  count = 1,
}: SkeletonProps) {
  const baseStyles = 'animate-pulse bg-surface-tertiary';
  
  const variantStyles = {
    text: 'rounded',
    circular: 'rounded-full',
    rectangular: 'rounded-none',
    rounded: 'rounded-lg',
  };

  const items = Array.from({ length: count }, (_, i) => (
    <div
      key={i}
      className={clsx(baseStyles, variantStyles[variant], className)}
      style={{
        width: typeof width === 'number' ? `${width}px` : width,
        height: typeof height === 'number' ? `${height}px` : height,
      }}
    />
  ));

  return count === 1 ? items[0] : <div className="space-y-2">{items}</div>;
}

// Pre-built skeleton layouts
export function CardSkeleton() {
  return (
    <div className="rounded-xl border border-border-default bg-surface-primary p-6">
      <Skeleton variant="rounded" width="100%" height={120} className="mb-4" />
      <Skeleton variant="text" width="60%" height={24} className="mb-2" />
      <Skeleton variant="text" width="40%" height={16} />
    </div>
  );
}

export function TableSkeleton({ rows = 5 }: { rows?: number }) {
  return (
    <div className="rounded-xl border border-border-default overflow-hidden">
      {/* Header */}
      <div className="flex gap-4 p-4 bg-surface-secondary border-b border-border-default">
        <Skeleton variant="text" width="25%" height={20} />
        <Skeleton variant="text" width="25%" height={20} />
        <Skeleton variant="text" width="25%" height={20} />
        <Skeleton variant="text" width="25%" height={20} />
      </div>
      {/* Rows */}
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="flex gap-4 p-4 border-b border-border-default last:border-0">
          <Skeleton variant="text" width="25%" height={16} />
          <Skeleton variant="text" width="25%" height={16} />
          <Skeleton variant="text" width="25%" height={16} />
          <Skeleton variant="text" width="25%" height={16} />
        </div>
      ))}
    </div>
  );
}

export function StatCardSkeleton() {
  return (
    <div className="rounded-xl border border-border-default bg-surface-primary p-6">
      <div className="flex items-center justify-between">
        <div className="space-y-2">
          <Skeleton variant="text" width={80} height={12} />
          <Skeleton variant="text" width={60} height={32} />
        </div>
        <Skeleton variant="circular" width={40} height={40} />
      </div>
    </div>
  );
}

export function PageHeaderSkeleton() {
  return (
    <div className="flex items-center justify-between mb-6">
      <div className="space-y-2">
        <Skeleton variant="text" width={200} height={32} />
        <Skeleton variant="text" width={300} height={16} />
      </div>
      <Skeleton variant="rounded" width={120} height={40} />
    </div>
  );
}

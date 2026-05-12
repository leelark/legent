import type { LucideIcon } from 'lucide-react';
import { StatusPill } from '@/components/admin/AdminChrome';

export function PerformanceSignalCard({
  icon: Icon,
  title,
  status,
  lines,
}: {
  icon: LucideIcon;
  title: string;
  status: string;
  lines: string[];
}) {
  return (
    <div className="rounded-xl border border-border-default bg-surface-secondary p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2">
          <div className="rounded-lg border border-border-default bg-surface-primary p-2 text-brand-600">
            <Icon className="h-4 w-4" />
          </div>
          <p className="text-sm font-semibold text-content-primary">{title}</p>
        </div>
        <StatusPill status={status} />
      </div>
      <div className="mt-4 space-y-2 text-sm text-content-secondary">
        {lines.map((line) => (
          <div key={line} className="rounded-lg border border-border-default bg-surface-primary px-3 py-2">
            {line}
          </div>
        ))}
      </div>
    </div>
  );
}

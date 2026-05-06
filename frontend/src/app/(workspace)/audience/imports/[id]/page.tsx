'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { get } from '@/lib/api-client';

const statusBadgeMap: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'default'> = {
  COMPLETED: 'success',
  COMPLETED_WITH_ERRORS: 'warning',
  FAILED: 'danger',
  PROCESSING: 'info',
  PENDING: 'default',
  CANCELLED: 'default',
};

export default function ImportDetailsPage() {
  const params = useParams();
  const id = params?.id as string;
  const [job, setJob] = useState<any>(null);
  const [loading, setLoading] = useState(false);

  const isRunning = useMemo(() => job?.status === 'PENDING' || job?.status === 'PROCESSING', [job?.status]);

  const fetchJob = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const data = await get<any>(`/imports/${id}`);
      setJob(data);
    } catch {
      setJob(null);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchJob();
  }, [fetchJob]);

  useEffect(() => {
    if (!isRunning) return;
    const timer = setInterval(fetchJob, 2000);
    return () => clearInterval(timer);
  }, [isRunning, fetchJob]);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-content-primary">Import Details</h1>
          <p className="mt-1 text-sm text-content-secondary">Track import job progress and outcomes</p>
        </div>
        <Link href="/app/audience/imports">
          <Button variant="secondary">Back to Imports</Button>
        </Link>
      </div>

      <Card>
        {loading && !job ? (
          <p className="text-sm text-content-secondary">Loading job...</p>
        ) : !job ? (
          <p className="text-sm text-danger">Import job not found.</p>
        ) : (
          <div className="space-y-4">
            <div className="flex items-center gap-3">
              <h2 className="text-lg font-semibold text-content-primary">{job.fileName}</h2>
              <Badge variant={statusBadgeMap[job.status] || 'default'}>{job.status}</Badge>
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
              <Stat label="Total Rows" value={job.totalRows || 0} />
              <Stat label="Processed" value={job.processedRows || 0} />
              <Stat label="Successful" value={job.successRows || 0} />
              <Stat label="Failed" value={job.errorRows || 0} />
              <Stat label="Progress" value={`${job.progressPercent || 0}%`} />
              <Stat label="Target" value={job.targetType || 'SUBSCRIBER'} />
            </div>

            <div className="h-2 w-full overflow-hidden rounded-full bg-surface-secondary">
              <div
                className="h-full rounded-full bg-brand-500 transition-all duration-500"
                style={{ width: `${Math.min(100, Number(job.progressPercent || 0))}%` }}
              />
            </div>
          </div>
        )}
      </Card>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-lg border border-border-default bg-surface-secondary/40 p-3">
      <p className="text-xs uppercase tracking-wide text-content-muted">{label}</p>
      <p className="mt-1 text-base font-semibold text-content-primary">{value}</p>
    </div>
  );
}

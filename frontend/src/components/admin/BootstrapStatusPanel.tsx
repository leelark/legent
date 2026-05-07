"use client";

import React, { useCallback, useEffect, useState } from 'react';
import { getBootstrapStatus, repairBootstrap } from '@/lib/admin-api';
import { Button } from '@/components/ui/Button';
import { AdminEmptyState, AdminPanel, AdminSkeletonRows, StatusPill } from '@/components/admin/AdminChrome';

type BootstrapStatus = {
  tenantId: string;
  workspaceId?: string;
  environmentId?: string;
  status: string;
  message?: string;
  retryCount: number;
  lastAttemptAt?: string;
  completedAt?: string;
  modules?: Record<string, unknown>;
};

export const BootstrapStatusPanel: React.FC = () => {
  const [status, setStatus] = useState<BootstrapStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [repairing, setRepairing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getBootstrapStatus();
      setStatus(data || null);
    } catch (err: any) {
      setError(err?.normalized?.message || 'Failed to load bootstrap status');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleRepair = async () => {
    if (!window.confirm('Force repair default setup? This retries failed or partial provisioning modules.')) {
      return;
    }
    setRepairing(true);
    setError(null);
    try {
      const data = await repairBootstrap(true);
      setStatus(data || null);
    } catch (err: any) {
      setError(err?.normalized?.message || 'Repair request failed');
    } finally {
      setRepairing(false);
    }
  };

  return (
    <AdminPanel
      title="Default Setup Status"
      subtitle="Idempotent tenant bootstrap progress, repair state, retries, and module-level provisioning output."
      action={<Button size="sm" variant="secondary" onClick={load} loading={loading}>Refresh</Button>}
    >
      {loading ? (
        <AdminSkeletonRows rows={4} />
      ) : error ? (
        <div className="rounded-xl border border-danger/30 bg-danger/10 px-4 py-3 text-sm text-danger">{error}</div>
      ) : status ? (
        <div className="space-y-5 text-sm">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <div className="rounded-xl border border-border-default bg-surface-secondary p-4">
              <p className="text-xs uppercase tracking-wide text-content-muted">State</p>
              <div className="mt-2"><StatusPill status={status.status} /></div>
            </div>
            <div className="rounded-xl border border-border-default bg-surface-secondary p-4">
              <p className="text-xs uppercase tracking-wide text-content-muted">Retry Count</p>
              <p className="mt-1 text-2xl font-semibold text-content-primary">{status.retryCount ?? 0}</p>
            </div>
            <div className="rounded-xl border border-border-default bg-surface-secondary p-4">
              <p className="text-xs uppercase tracking-wide text-content-muted">Last Attempt</p>
              <p className="mt-1 text-xs text-content-primary">{status.lastAttemptAt ? new Date(status.lastAttemptAt).toLocaleString() : '-'}</p>
            </div>
            <div className="rounded-xl border border-border-default bg-surface-secondary p-4">
              <p className="text-xs uppercase tracking-wide text-content-muted">Completed</p>
              <p className="mt-1 text-xs text-content-primary">{status.completedAt ? new Date(status.completedAt).toLocaleString() : '-'}</p>
            </div>
          </div>

          <div className="rounded-xl border border-border-default bg-surface-primary p-4">
            <p className="text-xs uppercase tracking-wide text-content-muted">Context</p>
            <div className="mt-3 grid gap-2 text-xs sm:grid-cols-3">
              <p className="truncate text-content-secondary"><span className="font-semibold text-content-primary">Tenant:</span> {status.tenantId || '-'}</p>
              <p className="truncate text-content-secondary"><span className="font-semibold text-content-primary">Workspace:</span> {status.workspaceId || '-'}</p>
              <p className="truncate text-content-secondary"><span className="font-semibold text-content-primary">Environment:</span> {status.environmentId || '-'}</p>
            </div>
            {status.message ? <p className="mt-3 rounded-lg bg-surface-secondary px-3 py-2 text-sm text-content-secondary">{status.message}</p> : null}
          </div>

          <div>
            <div className="mb-2 flex items-center justify-between gap-3">
              <p className="text-sm font-semibold text-content-primary">Module Progress</p>
              <Button size="sm" onClick={handleRepair} loading={repairing}>
                Force Repair
              </Button>
            </div>
            {status.modules && Object.keys(status.modules).length > 0 ? (
              <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                {Object.entries(status.modules).map(([module, value]) => (
                  <div key={module} className="rounded-xl border border-border-default bg-surface-secondary p-3">
                    <p className="text-xs font-semibold uppercase tracking-wide text-content-primary">{module}</p>
                    <pre className="mt-2 max-h-28 overflow-auto whitespace-pre-wrap text-xs text-content-secondary">
                      {typeof value === 'string' ? value : JSON.stringify(value, null, 2)}
                    </pre>
                  </div>
                ))}
              </div>
            ) : (
              <AdminEmptyState title="No module progress reported" description="The bootstrap service did not return module details for this tenant yet." />
            )}
          </div>
        </div>
      ) : (
        <AdminEmptyState title="No bootstrap status available" description="Refresh after tenant provisioning starts or run repair from a privileged admin session." />
      )}
    </AdminPanel>
  );
};

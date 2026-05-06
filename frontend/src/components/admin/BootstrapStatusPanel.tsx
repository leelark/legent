"use client";

import React, { useEffect, useState } from 'react';
import { getBootstrapStatus, repairBootstrap } from '@/lib/admin-api';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';

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

  const load = async () => {
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
  };

  useEffect(() => {
    load();
  }, []);

  const handleRepair = async () => {
    setRepairing(true);
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
    <Card className="p-4 space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="font-bold">Default Setup Status</h3>
        <Button size="sm" variant="secondary" onClick={load}>Refresh</Button>
      </div>
      {loading ? (
        <p className="text-sm text-content-muted">Loading bootstrap status...</p>
      ) : error ? (
        <p className="text-sm text-danger">{error}</p>
      ) : status ? (
        <div className="space-y-2 text-sm">
          <p><span className="font-semibold">State:</span> {status.status}</p>
          <p><span className="font-semibold">Message:</span> {status.message || '-'}</p>
          <p><span className="font-semibold">Workspace:</span> {status.workspaceId || '-'}</p>
          <p><span className="font-semibold">Environment:</span> {status.environmentId || '-'}</p>
          <p><span className="font-semibold">Retry Count:</span> {status.retryCount}</p>
          <div className="rounded bg-surface-secondary p-2 text-xs max-h-40 overflow-auto">
            <pre>{JSON.stringify(status.modules || {}, null, 2)}</pre>
          </div>
          <Button size="sm" onClick={handleRepair} loading={repairing}>
            Repair Bootstrap
          </Button>
        </div>
      ) : (
        <p className="text-sm text-content-muted">No bootstrap status available.</p>
      )}
    </Card>
  );
};

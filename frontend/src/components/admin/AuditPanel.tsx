'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { listAdminAuditEvents } from '@/lib/admin-api';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { AdminEmptyState, AdminPanel, AdminSkeletonRows, AdminTableShell, StatusPill } from '@/components/admin/AdminChrome';

function readString(row: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = row[key];
    if (value !== undefined && value !== null && value !== '') {
      return String(value);
    }
  }
  return '-';
}

function formatDate(value: string) {
  return value && value !== '-' ? new Date(value).toLocaleString() : '-';
}

export function AuditPanel() {
  const [rows, setRows] = useState<Array<Record<string, unknown>>>([]);
  const [action, setAction] = useState('');
  const [workspaceId, setWorkspaceId] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await listAdminAuditEvents({
        action: action.trim() || undefined,
        workspaceId: workspaceId.trim() || undefined,
        limit: 50,
      });
      setRows(Array.isArray(data) ? data : []);
    } catch (err: any) {
      setError(err?.normalized?.message || err?.message || 'Failed to load audit events.');
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [action, workspaceId]);

  useEffect(() => {
    load();
  }, [load]);

  const grouped = useMemo(() => {
    return rows.reduce<Record<string, number>>((acc, row) => {
      const key = readString(row, ['action', 'eventType', 'type']);
      acc[key] = (acc[key] || 0) + 1;
      return acc;
    }, {});
  }, [rows]);

  return (
    <AdminPanel
      title="Audit"
      subtitle="Recent platform governance events with action, resource, workspace, and request metadata."
      action={<Button size="sm" variant="secondary" onClick={load} loading={loading}>Refresh</Button>}
    >
      <div className="space-y-4">
        <div className="grid gap-3 lg:grid-cols-[1fr_1fr_auto]">
          <Input label="Action filter" value={action} onChange={(event) => setAction(event.target.value)} placeholder="CONFIG_APPLY" />
          <Input label="Workspace filter" value={workspaceId} onChange={(event) => setWorkspaceId(event.target.value)} placeholder="Workspace ID" />
          <div className="flex items-end">
            <Button className="w-full lg:w-auto" onClick={load} loading={loading}>Apply Filters</Button>
          </div>
        </div>

        {Object.keys(grouped).length > 0 ? (
          <div className="flex flex-wrap gap-2">
            {Object.entries(grouped).slice(0, 8).map(([key, count]) => (
              <span key={key} className="rounded-full border border-border-default bg-surface-secondary px-3 py-1 text-xs font-medium text-content-secondary">
                {key} <span className="text-content-muted">{count}</span>
              </span>
            ))}
          </div>
        ) : null}

        {error ? <div className="rounded-xl border border-danger/30 bg-danger/10 px-4 py-3 text-sm text-danger">{error}</div> : null}

        {loading ? (
          <AdminSkeletonRows rows={5} />
        ) : rows.length === 0 ? (
          <AdminEmptyState title="No audit events found" description="Try another action or workspace filter, or refresh after changing runtime settings." />
        ) : (
          <AdminTableShell>
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead className="bg-surface-secondary text-xs uppercase tracking-wide text-content-muted">
                <tr>
                  <th className="px-4 py-3">Action</th>
                  <th className="px-4 py-3">Resource</th>
                  <th className="px-4 py-3">Workspace</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Created</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border-default">
                {rows.map((row, index) => {
                  const actionValue = readString(row, ['action', 'eventType', 'type']);
                  return (
                    <tr key={`${actionValue}-${index}`} className="transition-colors hover:bg-surface-secondary/70">
                      <td className="px-4 py-3 font-semibold text-content-primary">{actionValue}</td>
                      <td className="px-4 py-3 text-content-secondary">
                        {readString(row, ['resourceType', 'entityType', 'resource_type'])}
                        <span className="block text-xs text-content-muted">{readString(row, ['resourceId', 'entityId', 'resource_id'])}</span>
                      </td>
                      <td className="px-4 py-3 text-xs text-content-muted">{readString(row, ['workspaceId', 'workspace_id'])}</td>
                      <td className="px-4 py-3"><StatusPill status={readString(row, ['status', 'result'])} /></td>
                      <td className="px-4 py-3 text-xs text-content-muted">{formatDate(readString(row, ['createdAt', 'created_at', 'performedAt']))}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </AdminTableShell>
        )}
      </div>
    </AdminPanel>
  );
}

'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  listContactRequests,
  updateContactRequestStatus,
  type AdminContactRequest,
  type ContactRequestStatus,
} from '@/lib/admin-api';
import { Button } from '@/components/ui/Button';
import { useToast } from '@/components/ui/Toast';
import { AdminEmptyState, AdminPanel, AdminSkeletonRows, AdminTableShell, StatusPill } from '@/components/admin/AdminChrome';

const STATUSES: Array<ContactRequestStatus | 'ALL'> = ['ALL', 'RECEIVED', 'IN_REVIEW', 'CONTACTED', 'CLOSED'];
const MUTATION_STATUSES: ContactRequestStatus[] = ['RECEIVED', 'IN_REVIEW', 'CONTACTED', 'CLOSED'];

function formatDate(value?: string | null) {
  return value ? new Date(value).toLocaleString() : '-';
}

export function ContactRequestsPanel() {
  const [status, setStatus] = useState<ContactRequestStatus | 'ALL'>('ALL');
  const [rows, setRows] = useState<AdminContactRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [total, setTotal] = useState(0);
  const { addToast } = useToast();

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await listContactRequests({ status, page: 0, size: 30 });
      setRows(result.items);
      setTotal(result.totalElements);
    } catch (err: any) {
      const message = err?.normalized?.message || err?.message || 'Failed to load contact requests.';
      setError(message);
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [status]);

  useEffect(() => {
    load();
  }, [load]);

  const statusCounts = useMemo(() => {
    const counts: Record<string, number> = { ALL: total };
    rows.forEach((row) => {
      counts[row.status] = (counts[row.status] || 0) + 1;
    });
    return counts;
  }, [rows, total]);

  const updateStatus = async (row: AdminContactRequest, nextStatus: ContactRequestStatus) => {
    if (row.status === nextStatus) {
      return;
    }
    setSavingId(row.id);
    setError(null);
    try {
      const saved = await updateContactRequestStatus(row.id, nextStatus);
      setRows((current) => current.map((item) => (item.id === row.id ? { ...item, ...saved } : item)));
      addToast({
        type: 'success',
        title: 'Contact request updated',
        message: `${row.company} moved to ${nextStatus.replace(/_/g, ' ').toLowerCase()}.`,
      });
    } catch (err: any) {
      const message = err?.normalized?.message || err?.message || 'Failed to update contact request.';
      setError(message);
      addToast({ type: 'error', title: 'Status update failed', message });
    } finally {
      setSavingId(null);
    }
  };

  return (
    <AdminPanel
      title="Contact Requests"
      subtitle="Review public-site sales and support intake, then move each request through an explicit status."
      action={<Button size="sm" variant="secondary" onClick={load} loading={loading}>Refresh</Button>}
    >
      <div className="space-y-4">
        <div className="flex gap-2 overflow-x-auto pb-1">
          {STATUSES.map((item) => (
            <button
              key={item}
              type="button"
              onClick={() => setStatus(item)}
              className={`shrink-0 rounded-full border px-3 py-1.5 text-xs font-semibold transition-all ${
                status === item
                  ? 'border-brand-500 bg-brand-600 text-white shadow-[0_10px_24px_rgba(126,34,206,0.22)]'
                  : 'border-border-default bg-surface-primary text-content-secondary hover:border-brand-300 hover:text-content-primary'
              }`}
            >
              {item.replace(/_/g, ' ')}
              <span className="ml-1 text-current/70">{statusCounts[item] ?? 0}</span>
            </button>
          ))}
        </div>

        {error ? <div className="rounded-xl border border-danger/30 bg-danger/10 px-4 py-3 text-sm text-danger">{error}</div> : null}

        {loading ? (
          <AdminSkeletonRows rows={5} />
        ) : rows.length === 0 ? (
          <AdminEmptyState
            title="No contact requests in this queue"
            description="New public-site submissions appear here after the contact form stores them in the foundation service."
          />
        ) : (
          <>
            <div className="hidden md:block">
              <AdminTableShell>
                <table className="w-full min-w-[860px] text-left text-sm">
                  <thead className="bg-surface-secondary text-xs uppercase tracking-wide text-content-muted">
                    <tr>
                      <th className="px-4 py-3">Company</th>
                      <th className="px-4 py-3">Requester</th>
                      <th className="px-4 py-3">Interest</th>
                      <th className="px-4 py-3">Status</th>
                      <th className="px-4 py-3">Received</th>
                      <th className="px-4 py-3 text-right">Move</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border-default">
                    {rows.map((row) => (
                      <tr key={row.id} className="align-top transition-colors hover:bg-surface-secondary/70">
                        <td className="px-4 py-3">
                          <p className="font-semibold text-content-primary">{row.company}</p>
                          <p className="mt-1 line-clamp-2 max-w-md text-xs text-content-secondary">{row.message}</p>
                        </td>
                        <td className="px-4 py-3">
                          <p className="text-content-primary">{row.name || '-'}</p>
                          <p className="text-xs text-content-muted">{row.workEmail}</p>
                        </td>
                        <td className="px-4 py-3 text-content-secondary">{row.interest || row.sourcePage || '-'}</td>
                        <td className="px-4 py-3"><StatusPill status={row.status} /></td>
                        <td className="px-4 py-3 text-xs text-content-muted">{formatDate(row.createdAt)}</td>
                        <td className="px-4 py-3 text-right">
                          <select
                            value={row.status}
                            disabled={savingId === row.id}
                            onChange={(event) => updateStatus(row, event.target.value as ContactRequestStatus)}
                            className="rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-xs font-medium text-content-primary focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                          >
                            {MUTATION_STATUSES.map((item) => (
                              <option key={item} value={item}>{item.replace(/_/g, ' ')}</option>
                            ))}
                          </select>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </AdminTableShell>
            </div>

            <div className="grid gap-3 md:hidden">
              {rows.map((row) => (
                <div key={row.id} className="rounded-xl border border-border-default bg-surface-primary p-4 shadow-sm">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="font-semibold text-content-primary">{row.company}</p>
                      <p className="truncate text-xs text-content-muted">{row.workEmail}</p>
                    </div>
                    <StatusPill status={row.status} />
                  </div>
                  <p className="mt-3 text-sm text-content-secondary">{row.message}</p>
                  <div className="mt-3 flex items-center justify-between gap-2">
                    <p className="text-xs text-content-muted">{formatDate(row.createdAt)}</p>
                    <select
                      value={row.status}
                      disabled={savingId === row.id}
                      onChange={(event) => updateStatus(row, event.target.value as ContactRequestStatus)}
                      className="rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-xs font-medium text-content-primary"
                    >
                      {MUTATION_STATUSES.map((item) => (
                        <option key={item} value={item}>{item.replace(/_/g, ' ')}</option>
                      ))}
                    </select>
                  </div>
                </div>
              ))}
            </div>
          </>
        )}
      </div>
    </AdminPanel>
  );
}

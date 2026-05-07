'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { coreApi } from '@/lib/core-api';
import { AdminMetricCard, AdminPanel, AdminSkeletonRows } from '@/components/admin/AdminChrome';
import { Button } from '@/components/ui/Button';

type Summary = {
  organizations: number;
  businessUnits: number;
  workspaces: number;
  teams: number;
  departments: number;
  memberships: number;
  invitations: number;
  featureControls: number;
  quotas: number;
  usageRows: number;
  subscriptions: number;
  auditEvents: number;
};

const emptySummary: Summary = {
  organizations: 0,
  businessUnits: 0,
  workspaces: 0,
  teams: 0,
  departments: 0,
  memberships: 0,
  invitations: 0,
  featureControls: 0,
  quotas: 0,
  usageRows: 0,
  subscriptions: 0,
  auditEvents: 0,
};

export function PlatformCorePanel() {
  const [summary, setSummary] = useState<Summary>(emptySummary);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);

    Promise.allSettled([
      coreApi.listOrganizations(),
      coreApi.listBusinessUnits(),
      coreApi.listWorkspaces(),
      coreApi.listTeams(),
      coreApi.listDepartments(),
      coreApi.listMemberships(),
      coreApi.listInvitations(),
      coreApi.listFeatureControls(),
      coreApi.listQuotas(),
      coreApi.listUsage(),
      coreApi.listSubscriptions(),
      coreApi.listAuditEvents(undefined, undefined),
    ])
      .then((results) => {
        const valueAt = (index: number) => {
          const result = results[index];
          return result?.status === 'fulfilled' && Array.isArray(result.value) ? result.value.length : 0;
        };
        setSummary({
          organizations: valueAt(0),
          businessUnits: valueAt(1),
          workspaces: valueAt(2),
          teams: valueAt(3),
          departments: valueAt(4),
          memberships: valueAt(5),
          invitations: valueAt(6),
          featureControls: valueAt(7),
          quotas: valueAt(8),
          usageRows: valueAt(9),
          subscriptions: valueAt(10),
          auditEvents: valueAt(11),
        });
        const rejected = results.filter((result) => result.status === 'rejected').length;
        setError(rejected > 0 ? `${rejected} platform summary source${rejected > 1 ? 's' : ''} could not be loaded.` : null);
      })
      .catch((err) => {
        setError(err?.response?.data?.error?.message || err?.message || 'Unable to load platform core summary.');
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const cards = useMemo(
    () => [
      { label: 'Organizations', value: summary.organizations, tone: 'brand' as const },
      { label: 'Business Units', value: summary.businessUnits, tone: 'neutral' as const },
      { label: 'Workspaces', value: summary.workspaces, tone: 'info' as const },
      { label: 'Teams', value: summary.teams, tone: 'neutral' as const },
      { label: 'Memberships', value: summary.memberships, tone: 'success' as const },
      { label: 'Invitations', value: summary.invitations, tone: 'warning' as const },
      { label: 'Feature Controls', value: summary.featureControls, tone: 'brand' as const },
      { label: 'Quotas', value: summary.quotas, tone: 'info' as const },
    ],
    [summary]
  );

  return (
    <AdminPanel
      title="Platform Core Summary"
      subtitle="Governance overview across hierarchy, memberships, feature controls, quotas, subscriptions, and audit signal."
      action={<Button size="sm" variant="secondary" onClick={load} loading={loading}>Refresh</Button>}
    >
      {loading ? (
        <AdminSkeletonRows rows={5} />
      ) : (
        <div className="space-y-4">
          {error ? <div className="rounded-xl border border-warning/30 bg-warning/10 px-4 py-3 text-sm text-warning">{error}</div> : null}
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            {cards.map((card) => (
              <AdminMetricCard key={card.label} label={card.label} value={card.value} tone={card.tone} />
            ))}
          </div>
          <div className="grid gap-3 lg:grid-cols-3">
            <div className="rounded-xl border border-border-default bg-surface-secondary p-4">
              <p className="text-sm font-semibold text-content-primary">Hierarchy Health</p>
              <p className="mt-1 text-sm text-content-secondary">Organizations, business units, workspaces, teams, and departments are loaded from Platform Core APIs.</p>
            </div>
            <div className="rounded-xl border border-border-default bg-surface-secondary p-4">
              <p className="text-sm font-semibold text-content-primary">Usage + Subscription</p>
              <p className="mt-1 text-sm text-content-secondary">{summary.usageRows} usage rows and {summary.subscriptions} subscriptions are visible to admin controls.</p>
            </div>
            <div className="rounded-xl border border-border-default bg-surface-secondary p-4">
              <p className="text-sm font-semibold text-content-primary">Recent Audit Events</p>
              <p className="mt-1 text-sm text-content-secondary">{summary.auditEvents} recent governance events available for investigation.</p>
            </div>
          </div>
        </div>
      )}
    </AdminPanel>
  );
}

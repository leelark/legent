'use client';

import { useEffect, useMemo, useState } from 'react';
import { coreApi } from '@/lib/core-api';

type Summary = {
  organizations: number;
  businessUnits: number;
  workspaces: number;
  teams: number;
  departments: number;
  memberships: number;
  invitations: number;
  featureControls: number;
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
};

export function PlatformCorePanel() {
  const [summary, setSummary] = useState<Summary>(emptySummary);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);

    Promise.all([
      coreApi.listOrganizations(),
      coreApi.listBusinessUnits(),
      coreApi.listWorkspaces(),
      coreApi.listTeams(),
      coreApi.listDepartments(),
      coreApi.listMemberships(),
      coreApi.listInvitations(),
      coreApi.listFeatureControls(),
    ])
      .then(([
        organizations,
        businessUnits,
        workspaces,
        teams,
        departments,
        memberships,
        invitations,
        featureControls,
      ]) => {
        if (!active) {
          return;
        }
        setSummary({
          organizations: organizations?.length ?? 0,
          businessUnits: businessUnits?.length ?? 0,
          workspaces: workspaces?.length ?? 0,
          teams: teams?.length ?? 0,
          departments: departments?.length ?? 0,
          memberships: memberships?.length ?? 0,
          invitations: invitations?.length ?? 0,
          featureControls: featureControls?.length ?? 0,
        });
      })
      .catch((err) => {
        if (!active) {
          return;
        }
        setError(err?.response?.data?.error?.message || err?.message || 'Unable to load platform core summary.');
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  const cards = useMemo(
    () => [
      { label: 'Organizations', value: summary.organizations },
      { label: 'Business Units', value: summary.businessUnits },
      { label: 'Workspaces', value: summary.workspaces },
      { label: 'Teams', value: summary.teams },
      { label: 'Departments', value: summary.departments },
      { label: 'Memberships', value: summary.memberships },
      { label: 'Invitations', value: summary.invitations },
      { label: 'Feature Controls', value: summary.featureControls },
    ],
    [summary]
  );

  return (
    <section className="rounded-2xl border border-border-default bg-surface-primary p-5">
      <div className="mb-4">
        <h2 className="text-lg font-semibold text-content-primary">Platform Core Summary</h2>
        <p className="text-sm text-content-secondary">
          Real-time visibility into hierarchy, memberships, and platform controls.
        </p>
      </div>

      {loading ? (
        <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
          {Array.from({ length: 8 }).map((_, idx) => (
            <div key={idx} className="h-20 animate-pulse rounded-xl bg-surface-secondary" />
          ))}
        </div>
      ) : error ? (
        <div className="rounded-xl bg-danger/10 px-4 py-3 text-sm text-danger">{error}</div>
      ) : (
        <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
          {cards.map((card) => (
            <div key={card.label} className="rounded-xl border border-border-default bg-surface-secondary px-4 py-3">
              <p className="text-xs uppercase tracking-wide text-content-muted">{card.label}</p>
              <p className="mt-1 text-2xl font-semibold text-content-primary">{card.value}</p>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

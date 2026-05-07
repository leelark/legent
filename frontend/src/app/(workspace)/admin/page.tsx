"use client";
import React, { useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { clsx } from 'clsx';
import { AdminConfigPanel } from '@/components/admin/AdminConfigPanel';
import { BrandingPanel } from '@/components/admin/BrandingPanel';
import { WebhookPanel } from '@/components/admin/WebhookPanel';
import { SearchPanel } from '@/components/admin/SearchPanel';
import { PlatformCorePanel } from '@/components/admin/PlatformCorePanel';
import { useAuth } from '@/hooks/useAuth';
import { BootstrapStatusPanel } from '@/components/admin/BootstrapStatusPanel';
import { PublicContentPanel } from '@/components/admin/PublicContentPanel';
import { AuditPanel } from '@/components/admin/AuditPanel';
import { ContactRequestsPanel } from '@/components/admin/ContactRequestsPanel';
import { AdminMetricCard } from '@/components/admin/AdminChrome';

// Removed export const metadata

type AdminTab =
  | 'overview'
  | 'runtime'
  | 'platform'
  | 'bootstrap'
  | 'branding'
  | 'webhooks'
  | 'content'
  | 'search'
  | 'audit'
  | 'contacts';

const ADMIN_TABS: Array<{ id: AdminTab; label: string; description: string }> = [
  { id: 'overview', label: 'Overview', description: 'Operating health and governance posture' },
  { id: 'runtime', label: 'Runtime Config', description: 'Validated settings and rollback controls' },
  { id: 'platform', label: 'Platform Core', description: 'Hierarchy, quota, feature, and ownership summary' },
  { id: 'bootstrap', label: 'Bootstrap', description: 'Default setup progress and repair actions' },
  { id: 'branding', label: 'Branding', description: 'Workspace identity and theme preview' },
  { id: 'webhooks', label: 'Webhooks', description: 'Outbound integration endpoints and events' },
  { id: 'content', label: 'Public Content', description: 'Published pages, drafts, and SEO payloads' },
  { id: 'search', label: 'Search', description: 'Admin-wide entity discovery' },
  { id: 'audit', label: 'Audit', description: 'Governance activity and operational trail' },
  { id: 'contacts', label: 'Contact Requests', description: 'Public intake queue and follow-up status' },
];

export default function AdminPage() {
  const router = useRouter();
  const { isAdmin } = useAuth();
  const admin = isAdmin();
  const [activeTab, setActiveTab] = useState<AdminTab>('overview');
  const activeMeta = useMemo(() => ADMIN_TABS.find((tab) => tab.id === activeTab) ?? ADMIN_TABS[0], [activeTab]);

  useEffect(() => {
    if (!admin) {
      router.replace('/app/email');
    }
  }, [admin, router]);

  if (!admin) {
    // AUDIT-020: Show loading state while redirecting
    return (
      <div className="flex h-screen items-center justify-center bg-surface-secondary">
        <div className="text-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-brand-500 mx-auto mb-4" />
          <p className="text-content-secondary">Checking permissions...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="relative overflow-hidden rounded-2xl border border-border-default bg-surface-primary p-5 shadow-[0_24px_70px_rgba(76,29,149,0.10)] md:p-6">
        <div className="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-brand-800 via-brand-500 to-fuchsia-500" />
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-brand-600 dark:text-brand-300">Governance Cockpit</p>
            <h1 className="mt-2 text-2xl font-semibold text-content-primary md:text-3xl">Admin Studio</h1>
            <p className="mt-2 max-w-3xl text-sm text-content-secondary">
              Production controls for runtime configuration, platform ownership, public content, branding, integrations, audit, and customer intake.
            </p>
          </div>
          <div className="grid grid-cols-3 gap-2 rounded-xl border border-border-default bg-surface-secondary/80 p-2 text-center text-xs">
            <div className="rounded-lg bg-surface-primary px-3 py-2 shadow-sm">
              <p className="font-semibold text-content-primary">Live</p>
              <p className="text-content-muted">Runtime</p>
            </div>
            <div className="rounded-lg bg-surface-primary px-3 py-2 shadow-sm">
              <p className="font-semibold text-content-primary">RBAC</p>
              <p className="text-content-muted">Guarded</p>
            </div>
            <div className="rounded-lg bg-surface-primary px-3 py-2 shadow-sm">
              <p className="font-semibold text-content-primary">Audit</p>
              <p className="text-content-muted">Tracked</p>
            </div>
          </div>
        </div>
      </div>

      <div className="rounded-2xl border border-border-default bg-surface-primary p-2 shadow-sm">
        <div className="flex gap-2 overflow-x-auto pb-1">
          {ADMIN_TABS.map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setActiveTab(tab.id)}
              className={clsx(
                'group min-w-[138px] shrink-0 rounded-xl border px-3 py-2 text-left transition-all duration-200',
                activeTab === tab.id
                  ? 'border-brand-500 bg-brand-600 text-white shadow-[0_16px_34px_rgba(126,34,206,0.22)]'
                  : 'border-transparent bg-transparent text-content-secondary hover:border-border-default hover:bg-surface-secondary hover:text-content-primary'
              )}
            >
              <span className="block text-xs font-semibold">{tab.label}</span>
              <span className={clsx('mt-0.5 line-clamp-1 block text-[11px]', activeTab === tab.id ? 'text-white/75' : 'text-content-muted')}>
                {tab.description}
              </span>
            </button>
          ))}
        </div>
      </div>

      <div className="animate-fade-in space-y-6">
        <div>
          <p className="text-sm font-semibold text-content-primary">{activeMeta.label}</p>
          <p className="text-sm text-content-secondary">{activeMeta.description}</p>
        </div>

        {activeTab === 'overview' && (
          <>
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
              <AdminMetricCard label="Runtime" value="Validated" description="Settings preview, apply, reset, history, rollback" tone="brand" />
              <AdminMetricCard label="Bootstrap" value="Repairable" description="Progress, retry count, partial-state repair" tone="success" />
              <AdminMetricCard label="Content" value="Publishable" description="Draft, publish, SEO, and payload validation" tone="info" />
              <AdminMetricCard label="Intake" value="Actionable" description="Public contact queue with lifecycle status" tone="warning" />
            </div>
            <div className="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]">
              <PlatformCorePanel />
              <BootstrapStatusPanel />
            </div>
          </>
        )}

        {activeTab === 'runtime' && <AdminConfigPanel />}
        {activeTab === 'platform' && <PlatformCorePanel />}
        {activeTab === 'bootstrap' && <BootstrapStatusPanel />}
        {activeTab === 'branding' && <BrandingPanel />}
        {activeTab === 'webhooks' && <WebhookPanel />}
        {activeTab === 'content' && <PublicContentPanel />}
        {activeTab === 'search' && <SearchPanel />}
        {activeTab === 'audit' && <AuditPanel />}
        {activeTab === 'contacts' && <ContactRequestsPanel />}
      </div>
    </div>
  );
}

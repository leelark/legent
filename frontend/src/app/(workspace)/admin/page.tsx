"use client";
import React, { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { AdminConfigPanel } from '@/components/admin/AdminConfigPanel';
import { BrandingPanel } from '@/components/admin/BrandingPanel';
import { WebhookPanel } from '@/components/admin/WebhookPanel';
import { SearchPanel } from '@/components/admin/SearchPanel';
import { PlatformCorePanel } from '@/components/admin/PlatformCorePanel';
import { useAuth } from '@/hooks/useAuth';
import { BootstrapStatusPanel } from '@/components/admin/BootstrapStatusPanel';
import { PublicContentPanel } from '@/components/admin/PublicContentPanel';

// Removed export const metadata

export default function AdminPage() {
  const router = useRouter();
  const { isAdmin } = useAuth();
  const admin = isAdmin();

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
      <div>
        <p className="text-xs font-semibold uppercase tracking-wider text-brand-300">Governance</p>
        <h1 className="mt-1 text-2xl font-semibold text-content-primary md:text-3xl">Admin Studio</h1>
        <p className="mt-1 text-sm text-content-secondary">Central settings, governance, bootstrap, and runtime controls.</p>
      </div>
      <PlatformCorePanel />
      <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
        <div className="space-y-6">
          <BootstrapStatusPanel />
          <AdminConfigPanel />
          <BrandingPanel />
        </div>
        <div className="space-y-6">
          <WebhookPanel />
          <SearchPanel />
          <PublicContentPanel />
        </div>
      </div>
    </div>
  );
}

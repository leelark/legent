"use client";
import React, { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { AdminConfigPanel } from '@/components/admin/AdminConfigPanel';
import { BrandingPanel } from '@/components/admin/BrandingPanel';
import { WebhookPanel } from '@/components/admin/WebhookPanel';
import { SearchPanel } from '@/components/admin/SearchPanel';
import { useAuth } from '@/hooks/useAuth';

// Removed export const metadata

export default function AdminPage() {
  const router = useRouter();
  const { isAdmin } = useAuth();
  const admin = isAdmin();

  useEffect(() => {
    if (!admin) {
      router.replace('/email');
    }
  }, [admin, router]);

  if (!admin) {
    return null;
  }

  return (
    <div className="space-y-6 p-8">
      <h1 className="text-3xl font-bold mb-4">Admin Console</h1>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        <div className="space-y-6">
          <AdminConfigPanel />
          <BrandingPanel />
        </div>
        <div className="space-y-6">
          <WebhookPanel />
          <SearchPanel />
        </div>
      </div>
    </div>
  );
}

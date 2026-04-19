"use client";
import React from 'react';
import { AdminConfigPanel } from '@/components/admin/AdminConfigPanel';
import { BrandingPanel } from '@/components/admin/BrandingPanel';
import { WebhookPanel } from '@/components/admin/WebhookPanel';
import { SearchPanel } from '@/components/admin/SearchPanel';

// Removed export const metadata

export default function AdminPage() {
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

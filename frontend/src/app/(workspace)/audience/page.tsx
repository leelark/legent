'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Card, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Tabs } from '@/components/ui/Tabs';
import {
  Users, ListBullets, Database, Funnel,
  Upload, ShieldCheck, TrendUp, Plus
} from '@phosphor-icons/react/dist/ssr';

function StatCard({ label, value, icon: Icon, href, color }: {
  label: string; value: string | number; icon: any; href: string; color: string;
}) {
  return (
    <Link href={href}>
      <Card hover>
        <div className="flex items-center justify-between">
          <div>
            <p className="text-xs font-medium text-content-secondary uppercase tracking-wider">{label}</p>
            <p className="mt-2 text-2xl font-bold text-content-primary">{value}</p>
          </div>
          <div className={`flex h-10 w-10 items-center justify-center rounded-xl ${color}`}>
            <Icon size={20} weight="duotone" className="text-white" />
          </div>
        </div>
      </Card>
    </Link>
  );
}

export default function AudienceDashboard() {
  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-content-primary">Audience</h1>
          <p className="mt-1 text-sm text-content-secondary">
            Manage subscribers, lists, data extensions, and segments
          </p>
        </div>
        <div className="flex gap-2">
          <Link href="/audience/imports/new">
            <Button variant="secondary" icon={<Upload size={16} />}>Import</Button>
          </Link>
          <Link href="/audience/subscribers?action=create">
            <Button icon={<Plus size={16} />}>Add Subscriber</Button>
          </Link>
        </div>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Total Subscribers" value="—" icon={Users} href="/audience/subscribers" color="bg-gradient-to-br from-brand-500 to-brand-700" />
        <StatCard label="Lists" value="—" icon={ListBullets} href="/audience/lists" color="bg-gradient-to-br from-emerald-500 to-emerald-700" />
        <StatCard label="Data Extensions" value="—" icon={Database} href="/audience/data-extensions" color="bg-gradient-to-br from-amber-500 to-amber-700" />
        <StatCard label="Segments" value="—" icon={Funnel} href="/audience/segments" color="bg-gradient-to-br from-violet-500 to-violet-700" />
      </div>

      {/* Quick Access Tabs */}
      <Card>
        <Tabs
          tabs={[
            { key: 'recent', label: 'Recent Subscribers', icon: <Users size={16} /> },
            { key: 'imports', label: 'Recent Imports', icon: <Upload size={16} /> },
            { key: 'segments', label: 'Active Segments', icon: <Funnel size={16} /> },
          ]}
        >
          {(tab) => (
            <div className="py-8 text-center">
              <p className="text-sm text-content-muted">
                {tab === 'recent' && 'Recent subscribers will appear here after data is loaded.'}
                {tab === 'imports' && 'Import history will appear here.'}
                {tab === 'segments' && 'Active segments with computed counts will appear here.'}
              </p>
              <Link href={tab === 'recent' ? '/audience/subscribers' : tab === 'imports' ? '/audience/imports' : '/audience/segments'}>
                <Button variant="ghost" size="sm" className="mt-3">View All →</Button>
              </Link>
            </div>
          )}
        </Tabs>
      </Card>
    </div>
  );
}

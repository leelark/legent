'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { Card, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Table } from '@/components/ui/Table';
import { EmptyState } from '@/components/ui/EmptyState';
import { Plus, Megaphone, MagnifyingGlass } from '@phosphor-icons/react';

const statusBadgeMap: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'default'> = {
  DRAFT: 'default',
  SCHEDULED: 'info',
  SENDING: 'warning',
  COMPLETED: 'success',
  CANCELLED: 'danger',
};

const columns = [
  { key: 'name', header: 'Campaign Name', render: (row: any) => <span className="font-medium text-content-primary">{row.name}</span> },
  { key: 'subject', header: 'Subject', render: (row: any) => row.subject || '—' },
  { key: 'status', header: 'Status', render: (row: any) => <Badge variant={statusBadgeMap[row.status] || 'default'}>{row.status}</Badge> },
  { key: 'type', header: 'Type', render: (row: any) => row.type || 'STANDARD' },
  { key: 'createdAt', header: 'Created', render: (row: any) => row.createdAt ? new Date(row.createdAt).toLocaleDateString() : '—' },
  {
    key: 'actions',
    header: '',
    render: (row: any) => (
      <div className="flex justify-end">
        <Link href={`/campaigns/${row.id}/tracking`}>
          <Button variant="ghost" size="sm">Tracking</Button>
        </Link>
      </div>
    ),
  },
];

export default function CampaignsPage() {
  const [search, setSearch] = useState('');
  const [campaigns, setCampaigns] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch('/api/v1/campaigns')
      .then(res => res.json())
      .then(data => {
        setCampaigns(data.data?.content || data.data || []);
        setLoading(false);
      })
      .catch(() => setLoading(false));
  }, []);

  const filteredCampaigns = campaigns.filter(c => c.name?.toLowerCase().includes(search.toLowerCase()));

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-content-primary">Campaigns</h1>
          <p className="mt-1 text-sm text-content-secondary">Orchestrate and manage email sends</p>
        </div>
        <Link href="/campaigns/new">
          <Button icon={<Plus size={16} />}>Create Campaign</Button>
        </Link>
      </div>

      {/* Filters */}
      <Card>
        <div className="flex gap-4 sm:items-center">
          <div className="relative flex-1">
            <MagnifyingGlass size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-content-muted" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search campaigns..."
              className="w-full rounded-lg border border-border-default bg-surface-secondary py-2 pl-9 pr-4 text-sm text-content-primary placeholder-content-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/30 transition-all"
            />
          </div>
        </div>
      </Card>

      {/* Table */}
      {filteredCampaigns.length > 0 || loading ? (
        <Card className="!p-0 overflow-hidden">
          <Table
            columns={columns}
            data={filteredCampaigns}
            rowKey={(row: any) => row.id}
            emptyMessage="No campaigns found"
            loading={loading}
          />
        </Card>
      ) : (
        <Card>
          <EmptyState
              type="empty"
              title="Create your first campaign"
              description="Start engaging with your audience by creating a new email campaign."
              action={
                <Link href="/campaigns/new">
                    <Button icon={<Megaphone size={16} />}>Create Campaign</Button>
                </Link>
              }
          />
        </Card>
      )}
    </div>
  );
}

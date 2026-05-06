'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Table } from '@/components/ui/Table';
import { EmptyState } from '@/components/ui/EmptyState';
import { useToast } from '@/components/ui/Toast';
import { Plus, Megaphone, MagnifyingGlass, ClockClockwise } from '@phosphor-icons/react';
import {
  Campaign,
  archiveCampaign,
  cancelCampaign,
  cancelCampaignSend,
  cloneCampaign,
  listCampaigns,
  pauseCampaignSend,
  resumeCampaignSend,
  restoreCampaign,
  triggerCampaignSend,
} from '@/lib/campaign-studio-api';

const statusBadgeMap: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'default'> = {
  DRAFT: 'default',
  REVIEW_PENDING: 'info',
  APPROVED: 'success',
  SCHEDULED: 'info',
  SENDING: 'warning',
  PAUSED: 'warning',
  COMPLETED: 'success',
  FAILED: 'danger',
  CANCELLED: 'danger',
  ARCHIVED: 'default',
};

export default function CampaignsPage() {
  const { addToast } = useToast();
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [actionLoadingId, setActionLoadingId] = useState<string | null>(null);
  const [campaigns, setCampaigns] = useState<Campaign[]>([]);

  const loadCampaigns = useCallback(async () => {
    setLoading(true);
    try {
      const response = await listCampaigns({ page: 0, size: 100, search: search.trim() || undefined });
      const items = Array.isArray((response as any)?.content)
        ? ((response as any).content as Campaign[])
        : [];
      setCampaigns(items);
    } catch (error: any) {
      addToast({
        type: 'error',
        title: 'Failed to load campaigns',
        message: error?.response?.data?.error?.message || 'Unable to fetch campaign list.',
      });
    } finally {
      setLoading(false);
    }
  }, [addToast, search]);

  useEffect(() => {
    void loadCampaigns();
  }, [loadCampaigns]);

  const filteredCampaigns = useMemo(() => {
    if (!search.trim()) {
      return campaigns;
    }
    const lower = search.toLowerCase();
    return campaigns.filter((campaign) =>
      campaign.name?.toLowerCase().includes(lower) ||
      campaign.subject?.toLowerCase().includes(lower)
    );
  }, [campaigns, search]);

  const withAction = async (campaignId: string, action: () => Promise<void>) => {
    setActionLoadingId(campaignId);
    try {
      await action();
      await loadCampaigns();
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleSendNow = async (campaign: Campaign) => {
    await withAction(campaign.id, async () => {
      await triggerCampaignSend(campaign.id, {
        triggerSource: 'MANUAL',
        triggerReference: 'campaigns-page',
        idempotencyKey: `manual-send-${campaign.id}-${Date.now()}`,
      });
      addToast({ type: 'success', title: 'Send queued', message: `${campaign.name} send job started.` });
    });
  };

  const handlePause = async (campaign: Campaign) => {
    await withAction(campaign.id, async () => {
      await pauseCampaignSend(campaign.id, 'Paused from campaigns list');
      addToast({ type: 'success', title: 'Campaign paused', message: `${campaign.name} paused.` });
    });
  };

  const handleResume = async (campaign: Campaign) => {
    await withAction(campaign.id, async () => {
      await resumeCampaignSend(campaign.id, 'Resumed from campaigns list');
      addToast({ type: 'success', title: 'Campaign resumed', message: `${campaign.name} resumed.` });
    });
  };

  const handleCancel = async (campaign: Campaign) => {
    await withAction(campaign.id, async () => {
      if (campaign.status === 'SENDING' || campaign.status === 'SCHEDULED' || campaign.status === 'PAUSED') {
        await cancelCampaignSend(campaign.id, 'Cancelled from campaigns list');
      } else {
        await cancelCampaign(campaign.id, 'Cancelled from campaigns list');
      }
      addToast({ type: 'success', title: 'Campaign cancelled', message: `${campaign.name} cancelled.` });
    });
  };

  const handleArchive = async (campaign: Campaign) => {
    await withAction(campaign.id, async () => {
      await archiveCampaign(campaign.id, 'Archived from campaigns list');
      addToast({ type: 'success', title: 'Campaign archived', message: `${campaign.name} archived.` });
    });
  };

  const handleRestore = async (campaign: Campaign) => {
    await withAction(campaign.id, async () => {
      await restoreCampaign(campaign.id, 'Restored from campaigns list');
      addToast({ type: 'success', title: 'Campaign restored', message: `${campaign.name} restored.` });
    });
  };

  const handleClone = async (campaign: Campaign) => {
    await withAction(campaign.id, async () => {
      await cloneCampaign(campaign.id);
      addToast({ type: 'success', title: 'Campaign cloned', message: `${campaign.name} cloned.` });
    });
  };

  const columns = [
    {
      key: 'name',
      header: 'Campaign',
      render: (row: Campaign) => (
        <div className="space-y-1">
          <p className="font-medium text-content-primary">{row.name}</p>
          <p className="text-xs text-content-secondary">{row.subject || 'No subject set'}</p>
        </div>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (row: Campaign) => (
        <Badge variant={statusBadgeMap[row.status] || 'default'}>{row.status}</Badge>
      ),
    },
    {
      key: 'type',
      header: 'Type',
      render: (row: Campaign) => row.type || 'STANDARD',
    },
    {
      key: 'updatedAt',
      header: 'Updated',
      render: (row: Campaign) => (row.updatedAt ? new Date(row.updatedAt).toLocaleString() : 'n/a'),
    },
    {
      key: 'actions',
      header: '',
      render: (row: Campaign) => {
        const busy = actionLoadingId === row.id;
        const isActive = row.status === 'SENDING' || row.status === 'SCHEDULED';
        const isPaused = row.status === 'PAUSED';
        const canSend = row.status === 'APPROVED' || row.status === 'DRAFT' || row.status === 'FAILED';
        const canRestore = row.status === 'ARCHIVED' || row.status === 'CANCELLED';
        return (
          <div className="flex flex-wrap items-center justify-end gap-2">
            <Link href={`/campaigns/${row.id}/tracking`}>
              <Button variant="ghost" size="sm">Tracking</Button>
            </Link>
            <Link href={`/campaigns/new?clone=${row.id}`}>
              <Button variant="ghost" size="sm">Edit</Button>
            </Link>
            <Button variant="ghost" size="sm" onClick={() => handleClone(row)} disabled={busy}>Clone</Button>
            {canSend && (
              <Button size="sm" onClick={() => handleSendNow(row)} disabled={busy}>
                Send
              </Button>
            )}
            {isActive && (
              <Button variant="secondary" size="sm" onClick={() => handlePause(row)} disabled={busy}>
                Pause
              </Button>
            )}
            {isPaused && (
              <Button variant="secondary" size="sm" onClick={() => handleResume(row)} disabled={busy}>
                Resume
              </Button>
            )}
            {!canRestore && row.status !== 'ARCHIVED' && (
              <Button variant="danger" size="sm" onClick={() => handleCancel(row)} disabled={busy}>
                Cancel
              </Button>
            )}
            {canRestore ? (
              <Button variant="secondary" size="sm" onClick={() => handleRestore(row)} disabled={busy}>
                Restore
              </Button>
            ) : (
              <Button variant="secondary" size="sm" onClick={() => handleArchive(row)} disabled={busy}>
                Archive
              </Button>
            )}
          </div>
        );
      },
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-content-primary">Campaign Studio</h1>
          <p className="mt-1 text-sm text-content-secondary">Create, approve, schedule, and orchestrate enterprise email campaigns.</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="secondary" icon={<ClockClockwise size={16} />} onClick={() => void loadCampaigns()}>
            Refresh
          </Button>
          <Link href="/campaigns/new">
            <Button icon={<Plus size={16} />}>Create Campaign</Button>
          </Link>
        </div>
      </div>

      <Card>
        <div className="relative">
          <MagnifyingGlass size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-content-muted" />
          <input
            type="text"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search campaigns by name or subject..."
            className="w-full rounded-lg border border-border-default bg-surface-secondary py-2 pl-9 pr-4 text-sm text-content-primary placeholder-content-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/30"
          />
        </div>
      </Card>

      {filteredCampaigns.length > 0 || loading ? (
        <Card className="!p-0 overflow-hidden">
          <Table
            columns={columns}
            data={filteredCampaigns}
            rowKey={(row: Campaign) => row.id}
            loading={loading}
            emptyMessage="No campaigns found"
          />
        </Card>
      ) : (
        <Card>
          <EmptyState
            type="empty"
            title="Create your first campaign"
            description="Start with campaign wizard, then launch or schedule directly from this workspace."
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

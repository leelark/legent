'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import { Card, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Table } from '@/components/ui/Table';
import { useToast } from '@/components/ui/Toast';
import {
  Campaign,
  SendJob,
  cancelCampaignSend,
  getCampaign,
  getCampaignJobs,
  pauseCampaignSend,
  resendCampaign,
  resumeCampaignSend,
  retrySendJob,
} from '@/lib/campaign-studio-api';

const badgeMap: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'default'> = {
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
  PENDING: 'info',
  RESOLVING: 'warning',
  BATCHING: 'warning',
  RETRYING: 'warning',
};

export default function CampaignTrackingPage() {
  const params = useParams();
  const campaignId = params?.id as string;
  const { addToast } = useToast();

  const [campaign, setCampaign] = useState<Campaign | null>(null);
  const [jobs, setJobs] = useState<SendJob[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);

  const loadTracking = useCallback(async () => {
    if (!campaignId) {
      return;
    }
    try {
      const [campaignData, jobsResponse] = await Promise.all([
        getCampaign(campaignId),
        getCampaignJobs(campaignId, 0, 50),
      ]);
      setCampaign(campaignData);
      setJobs(Array.isArray(jobsResponse?.content) ? jobsResponse.content : []);
    } catch (error: any) {
      addToast({
        type: 'error',
        title: 'Tracking load failed',
        message: error?.response?.data?.error?.message || 'Unable to load campaign tracking data.',
      });
    } finally {
      setLoading(false);
    }
  }, [addToast, campaignId]);

  useEffect(() => {
    void loadTracking();
  }, [loadTracking]);

  useEffect(() => {
    const active = jobs.some((job) => ['PENDING', 'RESOLVING', 'BATCHING', 'SENDING', 'RETRYING'].includes(job.status));
    if (!active) {
      return;
    }
    const timer = setInterval(() => {
      void loadTracking();
    }, 8000);
    return () => clearInterval(timer);
  }, [jobs, loadTracking]);

  const latestJob = useMemo(() => {
    if (jobs.length === 0) {
      return null;
    }
    return [...jobs].sort((a, b) => {
      const ta = a.createdAt ? new Date(a.createdAt).getTime() : 0;
      const tb = b.createdAt ? new Date(b.createdAt).getTime() : 0;
      return tb - ta;
    })[0];
  }, [jobs]);

  const progress = useMemo(() => {
    if (!latestJob || !latestJob.totalTarget || latestJob.totalTarget <= 0) {
      return 0;
    }
    const done = (latestJob.totalSent || 0) + (latestJob.totalFailed || 0) + (latestJob.totalSuppressed || 0);
    return Math.min(100, Math.round((done / latestJob.totalTarget) * 100));
  }, [latestJob]);

  const withAction = async (action: () => Promise<unknown>, successMessage: string) => {
    setActionLoading(true);
    try {
      await action();
      addToast({ type: 'success', title: 'Action applied', message: successMessage });
      await loadTracking();
    } catch (error: any) {
      addToast({
        type: 'error',
        title: 'Action failed',
        message: error?.response?.data?.error?.message || 'Unable to perform action.',
      });
    } finally {
      setActionLoading(false);
    }
  };

  const jobColumns = [
    {
      key: 'id',
      header: 'Job',
      render: (row: SendJob) => (
        <div className="space-y-1">
          <p className="font-medium text-content-primary">{row.id}</p>
          <p className="text-xs text-content-secondary">{row.triggerSource || 'MANUAL'}</p>
        </div>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (row: SendJob) => (
        <Badge variant={badgeMap[row.status] || 'default'}>{row.status}</Badge>
      ),
    },
    {
      key: 'totals',
      header: 'Delivery Totals',
      render: (row: SendJob) => (
        <div className="text-xs text-content-secondary">
          <p>Target: {row.totalTarget || 0}</p>
          <p>Sent: {row.totalSent || 0}</p>
          <p>Failed: {row.totalFailed || 0}</p>
        </div>
      ),
    },
    {
      key: 'updatedAt',
      header: 'Updated',
      render: (row: SendJob) => (row.updatedAt ? new Date(row.updatedAt).toLocaleString() : 'n/a'),
    },
    {
      key: 'actions',
      header: '',
      render: (row: SendJob) => (
        <div className="flex items-center justify-end gap-2">
          {row.status === 'PAUSED' && (
            <Button size="sm" variant="secondary" onClick={() => void withAction(() => resumeCampaignSend(campaignId, 'Resume from tracking page'), 'Send resumed')} disabled={actionLoading}>
              Resume
            </Button>
          )}
          {['PENDING', 'RESOLVING', 'BATCHING', 'SENDING', 'RETRYING'].includes(row.status) && (
            <Button size="sm" variant="secondary" onClick={() => void withAction(() => pauseCampaignSend(campaignId, 'Pause from tracking page'), 'Send paused')} disabled={actionLoading}>
              Pause
            </Button>
          )}
          {['FAILED', 'CANCELLED'].includes(row.status) && (
            <Button size="sm" variant="secondary" onClick={() => void withAction(() => retrySendJob(row.id, 'Retry from tracking page'), 'Retry scheduled')} disabled={actionLoading}>
              Retry
            </Button>
          )}
          {row.status !== 'COMPLETED' && row.status !== 'CANCELLED' && (
            <Button size="sm" variant="danger" onClick={() => void withAction(() => cancelCampaignSend(campaignId, 'Cancel from tracking page'), 'Send cancelled')} disabled={actionLoading}>
              Cancel
            </Button>
          )}
        </div>
      ),
    },
  ];

  if (loading) {
    return <div className="p-8 text-sm text-content-secondary">Loading campaign tracking...</div>;
  }

  if (!campaign) {
    return (
      <Card>
        <p className="p-6 text-sm text-content-secondary">Campaign not found for this workspace.</p>
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold text-content-primary">{campaign.name}</h1>
            <Badge variant={badgeMap[campaign.status] || 'default'}>{campaign.status}</Badge>
          </div>
          <p className="mt-1 text-sm text-content-secondary">{campaign.subject || 'No subject set'}</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="secondary" onClick={() => void loadTracking()} disabled={actionLoading}>Refresh</Button>
          <Button variant="secondary" onClick={() => void withAction(() => resendCampaign(campaign.id, 'Resend from tracking page'), 'Resend job created')} disabled={actionLoading}>
            Resend
          </Button>
          <Link href="/app/campaigns">
            <Button variant="secondary">Back</Button>
          </Link>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
        <Card>
          <p className="text-xs font-medium uppercase tracking-wider text-content-secondary">Target</p>
          <p className="mt-2 text-2xl font-bold text-content-primary">{latestJob?.totalTarget || 0}</p>
        </Card>
        <Card>
          <p className="text-xs font-medium uppercase tracking-wider text-content-secondary">Sent</p>
          <p className="mt-2 text-2xl font-bold text-brand-600">{latestJob?.totalSent || 0}</p>
        </Card>
        <Card>
          <p className="text-xs font-medium uppercase tracking-wider text-content-secondary">Failed</p>
          <p className="mt-2 text-2xl font-bold text-danger">{latestJob?.totalFailed || 0}</p>
        </Card>
        <Card>
          <p className="text-xs font-medium uppercase tracking-wider text-content-secondary">Suppressed</p>
          <p className="mt-2 text-2xl font-bold text-amber-500">{latestJob?.totalSuppressed || 0}</p>
        </Card>
      </div>

      <Card>
        <CardHeader title="Progress" subtitle={latestJob ? `Job ${latestJob.id}` : 'No job started yet'} />
        <div className="space-y-3 p-4">
          <div className="flex items-center justify-between text-sm">
            <span className="text-content-primary">{progress}% complete</span>
            <span className="text-content-secondary">{latestJob?.status || 'Idle'}</span>
          </div>
          <div className="h-3 w-full rounded-full bg-surface-secondary">
            <div
              className="h-full rounded-full bg-gradient-to-r from-brand-500 to-brand-400 transition-all"
              style={{ width: `${progress}%` }}
            />
          </div>
          {latestJob?.errorMessage && (
            <p className="text-sm text-danger">{latestJob.errorMessage}</p>
          )}
        </div>
      </Card>

      <Card className="!p-0 overflow-hidden">
        <Table
          columns={jobColumns}
          data={jobs}
          rowKey={(row: SendJob) => row.id}
          emptyMessage="No send jobs for this campaign."
        />
      </Card>
    </div>
  );
}

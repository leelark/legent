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
  CampaignBudget,
  CampaignExperiment,
  DeadLetterEntry,
  FrequencyPolicy,
  ResendPlan,
  SendJob,
  SendPreflightReport,
  VariantMetrics,
  cancelCampaignSend,
  createResendPlan,
  getCampaign,
  getCampaignBudget,
  getExperimentMetrics,
  getFrequencyPolicy,
  getCampaignJobs,
  listCampaignExperiments,
  listSendJobDeadLetters,
  pauseCampaignSend,
  preflightCampaignSend,
  promoteExperimentWinner,
  replayDeadLetter,
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
  const [experiments, setExperiments] = useState<CampaignExperiment[]>([]);
  const [budget, setBudget] = useState<CampaignBudget | null>(null);
  const [frequencyPolicy, setFrequencyPolicy] = useState<FrequencyPolicy | null>(null);
  const [preflight, setPreflight] = useState<SendPreflightReport | null>(null);
  const [deadLetters, setDeadLetters] = useState<DeadLetterEntry[]>([]);
  const [variantMetrics, setVariantMetrics] = useState<VariantMetrics[]>([]);
  const [resendMode, setResendMode] = useState<ResendPlan['resendMode']>('FAILED_ONLY');
  const [activeTab, setActiveTab] = useState<'safety' | 'experiments' | 'budget' | 'ops' | 'dlq' | 'analytics'>('safety');
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);

  const loadTracking = useCallback(async () => {
    if (!campaignId) {
      return;
    }
    try {
      const [campaignData, jobsResponse, experimentData, budgetData, frequencyData, preflightData] = await Promise.all([
        getCampaign(campaignId),
        getCampaignJobs(campaignId, 0, 50),
        listCampaignExperiments(campaignId),
        getCampaignBudget(campaignId),
        getFrequencyPolicy(campaignId),
        preflightCampaignSend(campaignId),
      ]);
      const jobItems = Array.isArray(jobsResponse?.content) ? jobsResponse.content : [];
      const latest = [...jobItems].sort((a, b) => {
        const ta = a.createdAt ? new Date(a.createdAt).getTime() : 0;
        const tb = b.createdAt ? new Date(b.createdAt).getTime() : 0;
        return tb - ta;
      })[0];
      const activeExperiment = Array.isArray(experimentData) ? experimentData[0] : undefined;
      const [metricData, deadLetterData] = await Promise.all([
        activeExperiment ? getExperimentMetrics(campaignId, activeExperiment.id) : Promise.resolve([]),
        latest ? listSendJobDeadLetters(latest.id) : Promise.resolve([]),
      ]);
      setCampaign(campaignData);
      setJobs(jobItems);
      setExperiments(Array.isArray(experimentData) ? experimentData : []);
      setBudget(budgetData);
      setFrequencyPolicy(frequencyData);
      setPreflight(preflightData);
      setVariantMetrics(metricData);
      setDeadLetters(deadLetterData);
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

  const budgetPercent = useMemo(() => {
    if (!budget || !budget.budgetLimit || budget.budgetLimit <= 0) {
      return 0;
    }
    const spend = Number(budget.actualSpend || 0) + Number(budget.reservedSpend || 0);
    return Math.min(100, Math.round((spend / Number(budget.budgetLimit)) * 100));
  }, [budget]);

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

  const handleCreateResendPlan = async () => {
    await withAction(async () => {
      const plan = await createResendPlan(campaignId, {
        resendMode,
        confirmed: resendMode === 'ALL_REQUIRES_CONFIRMATION',
        reason: 'Operator created resend plan from campaign tracking page',
      });
      addToast({
        type: plan.eligibleRecipients > 0 ? 'success' : 'info',
        title: 'Resend plan ready',
        message: `${plan.eligibleRecipients} eligible recipients for ${plan.resendMode}.`,
      });
    }, 'Resend plan created');
  };

  const handlePromoteWinner = async (experiment: CampaignExperiment) => {
    await withAction(
      () => promoteExperimentWinner(campaignId, experiment.id),
      `Winner promoted for ${experiment.name}`
    );
  };

  const handleReplayDeadLetter = async (entry: DeadLetterEntry) => {
    await withAction(
      () => replayDeadLetter(entry.jobId, entry.id),
      `Dead letter ${entry.id} replay requested`
    );
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

  const experimentColumns = [
    {
      key: 'name',
      header: 'Experiment',
      render: (row: CampaignExperiment) => (
        <div className="space-y-1">
          <p className="font-medium text-content-primary">{row.name}</p>
          <p className="text-xs text-content-secondary">
            {row.experimentType} by {row.winnerMetric} | Holdout {Number(row.holdoutPercentage || 0)}%
          </p>
        </div>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (row: CampaignExperiment) => <Badge variant={row.status === 'PROMOTED' ? 'success' : 'info'}>{row.status}</Badge>,
    },
    {
      key: 'variants',
      header: 'Variants',
      render: (row: CampaignExperiment) => (
        <div className="flex flex-wrap gap-1">
          {(row.variants || []).map((variant) => (
            <Badge key={variant.id || variant.variantKey} variant={variant.winner ? 'success' : 'default'}>
              {variant.variantKey}: {variant.weight}%
            </Badge>
          ))}
        </div>
      ),
    },
    {
      key: 'actions',
      header: '',
      render: (row: CampaignExperiment) => (
        <div className="flex justify-end">
          <Button size="sm" variant="secondary" onClick={() => void handlePromoteWinner(row)} disabled={actionLoading || row.status === 'PROMOTED'}>
            Promote Winner
          </Button>
        </div>
      ),
    },
  ];

  const metricColumns = [
    {
      key: 'variantId',
      header: 'Variant',
      render: (row: VariantMetrics) => row.variantId || 'HOLDOUT',
    },
    { key: 'targetCount', header: 'Target', render: (row: VariantMetrics) => row.targetCount },
    { key: 'sentCount', header: 'Sent', render: (row: VariantMetrics) => row.sentCount },
    { key: 'openCount', header: 'Opens', render: (row: VariantMetrics) => row.openCount },
    { key: 'clickCount', header: 'Clicks', render: (row: VariantMetrics) => row.clickCount },
    { key: 'conversionCount', header: 'Conv.', render: (row: VariantMetrics) => row.conversionCount },
    {
      key: 'score',
      header: 'Score',
      render: (row: VariantMetrics) => Number(row.score || 0).toFixed(4),
    },
  ];

  const deadLetterColumns = [
    {
      key: 'id',
      header: 'Dead Letter',
      render: (row: DeadLetterEntry) => (
        <div className="space-y-1">
          <p className="font-medium text-content-primary">{row.id}</p>
          <p className="text-xs text-content-secondary">{row.email || row.subscriberId || 'recipient unavailable'}</p>
        </div>
      ),
    },
    { key: 'reason', header: 'Reason', render: (row: DeadLetterEntry) => row.reason || 'n/a' },
    { key: 'retryCount', header: 'Retries', render: (row: DeadLetterEntry) => row.retryCount || 0 },
    {
      key: 'status',
      header: 'Status',
      render: (row: DeadLetterEntry) => <Badge variant={row.status === 'OPEN' ? 'danger' : 'default'}>{row.status}</Badge>,
    },
    {
      key: 'actions',
      header: '',
      render: (row: DeadLetterEntry) => (
        <div className="flex justify-end">
          <Button size="sm" variant="secondary" onClick={() => void handleReplayDeadLetter(row)} disabled={actionLoading || row.status === 'REPLAYED'}>
            Replay
          </Button>
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
          <select
            value={resendMode}
            onChange={(event) => setResendMode(event.target.value as ResendPlan['resendMode'])}
            className="rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-sm text-content-primary"
          >
            <option value="FAILED_ONLY">Failed only</option>
            <option value="NOT_SENT">Not sent</option>
            <option value="SUPPRESSED_RECHECK">Suppressed recheck</option>
            <option value="ALL_REQUIRES_CONFIRMATION">All with confirmation</option>
          </select>
          <Button variant="secondary" onClick={() => void handleCreateResendPlan()} disabled={actionLoading}>
            Resend Plan
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

      <div className="flex flex-wrap gap-2">
        {[
          ['safety', 'Audience Safety'],
          ['experiments', 'Experiments'],
          ['budget', 'Budget'],
          ['ops', 'Send Ops'],
          ['dlq', 'DLQ'],
          ['analytics', 'Variant Analytics'],
        ].map(([key, label]) => (
          <Button
            key={key}
            size="sm"
            variant={activeTab === key ? 'primary' : 'secondary'}
            onClick={() => setActiveTab(key as typeof activeTab)}
          >
            {label}
          </Button>
        ))}
      </div>

      {activeTab === 'safety' && (
        <Card>
          <CardHeader title="Preflight Gates" subtitle="Approval, audience, frequency, budget, and experiment readiness." />
          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <Badge variant={preflight?.sendAllowed ? 'success' : 'danger'}>
                  {preflight?.sendAllowed ? 'SEND ALLOWED' : 'BLOCKED'}
                </Badge>
                <span className="text-sm text-content-secondary">
                  {Object.entries(preflight?.checks || {}).map(([key, value]) => `${key}: ${String(value)}`).join(' | ') || 'No checks returned'}
                </span>
              </div>
              {(preflight?.errors || []).map((item) => (
                <p key={item} className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">{item}</p>
              ))}
              {(preflight?.warnings || []).map((item) => (
                <p key={item} className="rounded-lg border border-amber-300/50 bg-amber-500/10 px-3 py-2 text-sm text-amber-700 dark:text-amber-200">{item}</p>
              ))}
            </div>
            <div className="grid gap-3 text-sm text-content-primary">
              <div className="flex justify-between rounded-lg bg-surface-secondary px-3 py-2">
                <span>Frequency</span>
                <span>{frequencyPolicy?.enabled ? `${frequencyPolicy.maxSends} per ${frequencyPolicy.windowHours}h` : 'Off'}</span>
              </div>
              <div className="flex justify-between rounded-lg bg-surface-secondary px-3 py-2">
                <span>Journey Scope</span>
                <span>{frequencyPolicy?.includeJourneys ? 'Included' : 'Campaign only'}</span>
              </div>
              <div className="flex justify-between rounded-lg bg-surface-secondary px-3 py-2">
                <span>Latest Job</span>
                <span>{latestJob?.status || 'Idle'}</span>
              </div>
            </div>
          </div>
        </Card>
      )}

      {activeTab === 'experiments' && (
        <Card className="!p-0 overflow-hidden">
          <Table
            columns={experimentColumns}
            data={experiments}
            rowKey={(row: CampaignExperiment) => row.id}
            emptyMessage="No experiments configured for this campaign."
          />
        </Card>
      )}

      {activeTab === 'budget' && (
        <Card>
          <CardHeader title="Budget Ledger" subtitle={budget ? `${budget.currency} ${budget.status}` : 'No budget configured'} />
          <div className="space-y-4">
            <div className="grid gap-3 md:grid-cols-4">
              <div className="rounded-lg bg-surface-secondary p-3">
                <p className="text-xs text-content-secondary">Limit</p>
                <p className="text-lg font-semibold text-content-primary">{budget?.budgetLimit || 0}</p>
              </div>
              <div className="rounded-lg bg-surface-secondary p-3">
                <p className="text-xs text-content-secondary">Reserved</p>
                <p className="text-lg font-semibold text-content-primary">{budget?.reservedSpend || 0}</p>
              </div>
              <div className="rounded-lg bg-surface-secondary p-3">
                <p className="text-xs text-content-secondary">Actual</p>
                <p className="text-lg font-semibold text-content-primary">{budget?.actualSpend || 0}</p>
              </div>
              <div className="rounded-lg bg-surface-secondary p-3">
                <p className="text-xs text-content-secondary">Cost/Send</p>
                <p className="text-lg font-semibold text-content-primary">{budget?.costPerSend || 0}</p>
              </div>
            </div>
            <div className="h-3 w-full rounded-full bg-surface-secondary">
              <div className="h-full rounded-full bg-brand-500 transition-all" style={{ width: `${budgetPercent}%` }} />
            </div>
          </div>
        </Card>
      )}

      {activeTab === 'ops' && (
        <Card className="!p-0 overflow-hidden">
          <Table
            columns={jobColumns}
            data={jobs}
            rowKey={(row: SendJob) => row.id}
            emptyMessage="No send jobs for this campaign."
          />
        </Card>
      )}

      {activeTab === 'dlq' && (
        <Card className="!p-0 overflow-hidden">
          <Table
            columns={deadLetterColumns}
            data={deadLetters}
            rowKey={(row: DeadLetterEntry) => row.id}
            emptyMessage="No dead letters for latest send job."
          />
        </Card>
      )}

      {activeTab === 'analytics' && (
        <Card className="!p-0 overflow-hidden">
          <Table
            columns={metricColumns}
            data={variantMetrics}
            rowKey={(row: VariantMetrics) => `${row.experimentId}-${row.variantId || 'holdout'}`}
            emptyMessage="No variant metrics recorded yet."
          />
        </Card>
      )}
    </div>
  );
}

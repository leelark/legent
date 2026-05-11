'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import {
  AlertTriangle,
  CalendarClock,
  CheckCircle2,
  GaugeCircle,
  RefreshCw,
  Rocket,
  Send,
  ShieldCheck,
  Sparkles,
  WandSparkles,
  XCircle,
} from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { PageHeader } from '@/components/ui/PageChrome';
import { useToast } from '@/components/ui/Toast';
import {
  Campaign,
  LaunchAction,
  LaunchPlanResponse,
  createRequestKey,
  executeLaunchPlan,
  listCampaigns,
  previewLaunchPlan,
} from '@/lib/campaign-studio-api';
import { listTemplates } from '@/lib/template-studio-api';
import { getQueueStats, getWarmupStatus } from '@/lib/delivery-api';
import { listProviderHealth, listProviders } from '@/lib/providers-api';
import { listDomains } from '@/lib/deliverability-api';

type SystemSnapshot = {
  templateCount: number;
  providerCount: number;
  healthyProviders: number;
  domainCount: number;
  queuePending: number;
  warmupReadiness: number;
  degradedReads: number;
};

const emptySnapshot: SystemSnapshot = {
  templateCount: 0,
  providerCount: 0,
  healthyProviders: 0,
  domainCount: 0,
  queuePending: 0,
  warmupReadiness: 0,
  degradedReads: 0,
};

const stepTone: Record<string, string> = {
  PASS: 'border-success/20 bg-success/10 text-success',
  WARN: 'border-warning/25 bg-warning/10 text-warning',
  BLOCKED: 'border-danger/20 bg-danger/10 text-danger',
  EXECUTED: 'border-brand-500/25 bg-brand-500/10 text-brand-600 dark:text-brand-300',
  SKIPPED: 'border-border-default bg-surface-secondary text-content-secondary',
  FAILED: 'border-danger/20 bg-danger/10 text-danger',
};

function asArray<T = any>(value: any): T[] {
  if (Array.isArray(value)) return value;
  if (Array.isArray(value?.content)) return value.content;
  if (Array.isArray(value?.data)) return value.data;
  return [];
}

function scoreTone(score: number) {
  if (score >= 86) return 'text-success';
  if (score >= 68) return 'text-warning';
  return 'text-danger';
}

function toDateTimeLocal(value?: string) {
  const date = value ? new Date(value) : new Date(Date.now() + 60 * 60 * 1000);
  const pad = (input: number) => String(input).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function fromDateTimeLocal(value: string) {
  return new Date(value).toISOString();
}

export default function LaunchCommandCenterPage() {
  const { addToast } = useToast();
  const [campaigns, setCampaigns] = useState<Campaign[]>([]);
  const [selectedCampaignId, setSelectedCampaignId] = useState('');
  const [snapshot, setSnapshot] = useState<SystemSnapshot>(emptySnapshot);
  const [report, setReport] = useState<LaunchPlanResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<LaunchAction | 'REFRESH' | null>(null);
  const [scheduledAt, setScheduledAt] = useState(toDateTimeLocal());

  const selectedCampaign = useMemo(
    () => campaigns.find((campaign) => campaign.id === selectedCampaignId) ?? campaigns[0],
    [campaigns, selectedCampaignId]
  );

  const loadWorkspace = useCallback(async () => {
    setLoading(true);
    setActionLoading('REFRESH');
    try {
      const [campaignResult, templateResult, providerResult, healthResult, queueResult, warmupResult, domainResult] =
        await Promise.allSettled([
          listCampaigns({ page: 0, size: 50 }),
          listTemplates(0, 50),
          listProviders(false),
          listProviderHealth(),
          getQueueStats(),
          getWarmupStatus(),
          listDomains(),
        ]);

      const nextCampaigns = campaignResult.status === 'fulfilled' ? asArray<Campaign>(campaignResult.value) : [];
      setCampaigns(nextCampaigns);
      setSelectedCampaignId((current) => current || nextCampaigns[0]?.id || '');

      const providers = providerResult.status === 'fulfilled' ? asArray<any>(providerResult.value) : [];
      const providerHealth = healthResult.status === 'fulfilled' ? asArray<any>(healthResult.value) : [];
      const queue = queueResult.status === 'fulfilled' ? queueResult.value : null;
      const warmup = warmupResult.status === 'fulfilled' ? warmupResult.value : null;

      setSnapshot({
        templateCount: templateResult.status === 'fulfilled' ? asArray(templateResult.value).length : 0,
        providerCount: providers.length,
        healthyProviders: providerHealth.filter((provider) => String(provider.healthStatus || '').toUpperCase() === 'HEALTHY').length,
        domainCount: domainResult.status === 'fulfilled' ? asArray(domainResult.value).length : 0,
        queuePending: Number((queue as any)?.pending ?? 0),
        warmupReadiness: Number((warmup as any)?.readiness ?? 0),
        degradedReads: [templateResult, providerResult, healthResult, queueResult, warmupResult, domainResult]
          .filter((result) => result.status === 'rejected').length,
      });
    } catch (error: any) {
      addToast({
        type: 'error',
        title: 'Launch workspace failed',
        message: error?.normalized?.message || 'Unable to load launch command data.',
      });
    } finally {
      setLoading(false);
      setActionLoading(null);
    }
  }, [addToast]);

  useEffect(() => {
    void loadWorkspace();
  }, [loadWorkspace]);

  const runPlan = async (action: LaunchAction, confirmLaunch = false) => {
    if (!selectedCampaign) return;
    setActionLoading(action);
    try {
      const payload = {
        campaignId: selectedCampaign.id,
        idempotencyKey: createRequestKey(`launch-${action.toLowerCase()}-${selectedCampaign.id}`),
        action,
        scheduledAt: action === 'SCHEDULE' ? fromDateTimeLocal(scheduledAt) : undefined,
        confirmLaunch,
        metadata: {
          source: 'launch-command-center',
          scoreBeforeAction: report?.readinessScore,
        },
      };
      const response = action === 'PREVIEW'
        ? await previewLaunchPlan(payload)
        : await executeLaunchPlan(payload);
      setReport(response);
      addToast({
        type: response.blockerCount > 0 ? 'warning' : 'success',
        title: action === 'PREVIEW' ? 'Readiness scan complete' : 'Launch action complete',
        message: response.message || 'Launch command center updated.',
      });
      if (action !== 'PREVIEW') {
        void loadWorkspace();
      }
    } catch (error: any) {
      addToast({
        type: 'error',
        title: 'Launch action failed',
        message: error?.normalized?.message || error?.response?.data?.error?.message || 'Unable to complete launch action.',
      });
    } finally {
      setActionLoading(null);
    }
  };

  const primaryAction = report?.primaryAction || 'RUN_READINESS';
  const canLaunch = report && report.blockerCount === 0;

  const primaryButton = () => {
    if (!selectedCampaign) return null;
    if (!report) {
      return (
        <Button icon={<GaugeCircle size={16} />} loading={actionLoading === 'PREVIEW'} onClick={() => runPlan('PREVIEW')}>
          Run readiness
        </Button>
      );
    }
    if (primaryAction === 'SUBMIT_APPROVAL') {
      return (
        <Button icon={<ShieldCheck size={16} />} loading={actionLoading === 'SUBMIT_APPROVAL'} onClick={() => runPlan('SUBMIT_APPROVAL')}>
          Submit approval
        </Button>
      );
    }
    if (primaryAction === 'FIX_BLOCKERS') {
      return (
        <Button icon={<WandSparkles size={16} />} loading={actionLoading === 'SAFE_FIX'} onClick={() => runPlan('SAFE_FIX')}>
          Apply safe fixes
        </Button>
      );
    }
    if (primaryAction === 'SCHEDULE') {
      return (
        <Button icon={<CalendarClock size={16} />} loading={actionLoading === 'SCHEDULE'} onClick={() => runPlan('SCHEDULE')}>
          Schedule launch
        </Button>
      );
    }
    return (
      <Button icon={<Send size={16} />} loading={actionLoading === 'LAUNCH'} disabled={!canLaunch} onClick={() => runPlan('LAUNCH', true)}>
        Confirm launch
      </Button>
    );
  };

  if (!loading && campaigns.length === 0) {
    return (
      <div className="space-y-6">
        <PageHeader
          eyebrow="Launch Command Center"
          title="One-action campaign launch"
          description="Create a campaign first, then run readiness, approval, delivery, and launch orchestration from one flow."
          action={<Link href="/app/campaigns/new"><Button icon={<Rocket size={16} />}>Create campaign</Button></Link>}
        />
        <Card>
          <EmptyState
            type="empty"
            title="No campaigns ready for launch"
            description="Campaign launch plans are generated from real campaign, audience, template, delivery, and governance data."
            action={<Link href="/app/campaigns/new"><Button>Create Campaign</Button></Link>}
          />
        </Card>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Launch Command Center"
        title="One-action launch orchestration"
        description="Run readiness, approval, scheduling, and send execution from one controlled command flow."
        action={(
          <div className="flex flex-wrap items-center gap-2">
            <Button variant="secondary" icon={<RefreshCw size={16} />} loading={actionLoading === 'REFRESH'} onClick={() => loadWorkspace()}>
              Refresh
            </Button>
            {primaryButton()}
          </div>
        )}
      />

      <section className="grid gap-4 lg:grid-cols-[1.05fr_0.95fr]">
        <Card className="overflow-hidden">
          <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
            <div className="min-w-0 flex-1">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-content-secondary">Active launch</p>
              <select
                value={selectedCampaign?.id || ''}
                onChange={(event) => {
                  setSelectedCampaignId(event.target.value);
                  setReport(null);
                }}
                className="mt-3 w-full rounded-xl border border-border-default bg-surface-secondary px-3 py-2 text-sm font-semibold text-content-primary outline-none focus:border-accent focus:ring-2 focus:ring-accent/20"
              >
                {campaigns.map((campaign) => (
                  <option key={campaign.id} value={campaign.id}>{campaign.name}</option>
                ))}
              </select>
              <div className="mt-4 grid gap-3 sm:grid-cols-3">
                <Signal label="Status" value={selectedCampaign?.status || 'n/a'} />
                <Signal label="Audience rules" value={String(selectedCampaign?.audiences?.length ?? 0)} />
                <Signal label="Template" value={selectedCampaign?.templateId ? 'Linked' : 'Missing'} />
              </div>
            </div>
            <div className="min-w-[180px] rounded-xl border border-border-default bg-surface-secondary/70 p-4 text-center">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-content-secondary">Readiness</p>
              <p className={`mt-2 text-5xl font-semibold ${scoreTone(report?.readinessScore ?? 0)}`}>
                {report?.readinessScore ?? '--'}
              </p>
              <p className="mt-1 text-xs text-content-secondary">{report ? `${report.blockerCount} blockers, ${report.warningCount} warnings` : 'Run scan'}</p>
            </div>
          </div>

          <div className="mt-5 flex flex-col gap-3 rounded-xl border border-border-default bg-surface-secondary/65 p-3 sm:flex-row sm:items-center">
            <label className="min-w-0 flex-1 text-sm font-medium text-content-primary">
              Schedule window
              <input
                type="datetime-local"
                value={scheduledAt}
                onChange={(event) => setScheduledAt(event.target.value)}
                className="mt-1 w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary outline-none focus:border-accent"
              />
            </label>
            <div className="flex flex-wrap gap-2 sm:pt-5">
              <Button variant="secondary" size="sm" onClick={() => runPlan('PREVIEW')} loading={actionLoading === 'PREVIEW'}>
                Scan
              </Button>
              <Button variant="secondary" size="sm" onClick={() => runPlan('SCHEDULE')} loading={actionLoading === 'SCHEDULE'} disabled={!canLaunch}>
                Schedule
              </Button>
              <Button size="sm" onClick={() => runPlan('LAUNCH', true)} loading={actionLoading === 'LAUNCH'} disabled={!canLaunch}>
                Launch
              </Button>
            </div>
          </div>
        </Card>

        <Card>
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-content-secondary">Runtime map</p>
              <h2 className="mt-1 text-lg font-semibold text-content-primary">Connected launch systems</h2>
            </div>
            {snapshot.degradedReads > 0 ? <Badge variant="warning">{snapshot.degradedReads} degraded</Badge> : <Badge variant="success">Live</Badge>}
          </div>
          <div className="mt-4 grid grid-cols-2 gap-3">
            <Signal label="Templates" value={String(snapshot.templateCount)} icon={<Sparkles size={15} />} />
            <Signal label="Providers" value={`${snapshot.healthyProviders}/${snapshot.providerCount}`} icon={<CheckCircle2 size={15} />} />
            <Signal label="Domains" value={String(snapshot.domainCount)} icon={<ShieldCheck size={15} />} />
            <Signal label="Queue" value={String(snapshot.queuePending)} icon={<Send size={15} />} />
          </div>
          <div className="mt-4 rounded-xl border border-border-default bg-surface-secondary/70 p-3">
            <div className="flex items-center justify-between gap-3 text-sm">
              <span className="font-medium text-content-primary">Provider warmup</span>
              <span className="font-semibold text-content-primary">{snapshot.warmupReadiness}%</span>
            </div>
            <div className="mt-2 h-2 overflow-hidden rounded-full bg-surface-primary">
              <div className="h-full rounded-full bg-gradient-to-r from-emerald-400 via-brand-500 to-fuchsia-500" style={{ width: `${Math.max(0, Math.min(100, snapshot.warmupReadiness))}%` }} />
            </div>
          </div>
        </Card>
      </section>

      <section className="grid gap-4 xl:grid-cols-[1fr_0.82fr]">
        <Card>
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-content-secondary">Execution path</p>
              <h2 className="mt-1 text-lg font-semibold text-content-primary">Readiness steps</h2>
            </div>
            {report ? <Badge variant={report.blockerCount > 0 ? 'danger' : report.warningCount > 0 ? 'warning' : 'success'}>{report.status}</Badge> : null}
          </div>
          <div className="mt-5 grid gap-3 md:grid-cols-5">
            {(report?.steps ?? ['audience', 'content', 'deliverability', 'delivery', 'governance'].map((key) => ({
              key,
              label: key[0].toUpperCase() + key.slice(1),
              status: 'SKIPPED',
              score: 0,
              message: 'Awaiting scan',
            }))).map((step) => (
              <div key={step.key} className="rounded-xl border border-border-default bg-surface-secondary/70 p-3">
                <span className={`inline-flex rounded-full border px-2 py-1 text-[11px] font-semibold ${stepTone[step.status] || stepTone.SKIPPED}`}>
                  {step.status}
                </span>
                <p className="mt-3 font-semibold text-content-primary">{step.label}</p>
                <p className="mt-1 min-h-[38px] text-xs leading-5 text-content-secondary">{step.message}</p>
                <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-surface-primary">
                  <div className="h-full rounded-full bg-brand-500" style={{ width: `${Math.max(0, Math.min(100, (step.score || 0) * 5))}%` }} />
                </div>
              </div>
            ))}
          </div>
        </Card>

        <Card>
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-content-secondary">Action queue</p>
              <h2 className="mt-1 text-lg font-semibold text-content-primary">Blockers and recommendations</h2>
            </div>
            {report?.blockerCount ? <XCircle className="text-danger" size={20} /> : <CheckCircle2 className="text-success" size={20} />}
          </div>
          <div className="mt-4 space-y-3">
            {report?.blockers?.map((blocker) => (
              <IssueRow key={blocker} tone="danger" icon={<XCircle size={16} />} text={blocker} />
            ))}
            {report?.warnings?.map((warning) => (
              <IssueRow key={warning} tone="warning" icon={<AlertTriangle size={16} />} text={warning} />
            ))}
            {report?.recommendations?.slice(0, 5).map((item) => (
              <IssueRow
                key={item.key}
                tone={item.severity === 'BLOCKER' ? 'danger' : 'info'}
                icon={<WandSparkles size={16} />}
                text={`${item.title}: ${item.detail}`}
              />
            ))}
            {!report ? (
              <p className="rounded-xl border border-border-default bg-surface-secondary/70 p-4 text-sm text-content-secondary">
                Run readiness to generate blockers, warnings, and safe next actions.
              </p>
            ) : report.blockerCount === 0 && report.warningCount === 0 ? (
              <p className="rounded-xl border border-success/20 bg-success/10 p-4 text-sm font-medium text-success">
                No blockers or warnings detected. Campaign is ready for controlled execution.
              </p>
            ) : null}
          </div>
        </Card>
      </section>
    </div>
  );
}

function Signal({ label, value, icon }: { label: string; value: string; icon?: React.ReactNode }) {
  return (
    <div className="min-w-0 rounded-xl border border-border-default bg-surface-secondary/70 p-3">
      <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.14em] text-content-secondary">
        {icon}
        <span className="truncate">{label}</span>
      </div>
      <p className="mt-2 truncate text-lg font-semibold text-content-primary">{value}</p>
    </div>
  );
}

function IssueRow({ tone, icon, text }: { tone: 'danger' | 'warning' | 'info'; icon: React.ReactNode; text: string }) {
  const toneClass = {
    danger: 'border-danger/20 bg-danger/10 text-danger',
    warning: 'border-warning/25 bg-warning/10 text-warning',
    info: 'border-brand-500/20 bg-brand-500/10 text-brand-600 dark:text-brand-300',
  }[tone];
  return (
    <div className={`flex items-start gap-3 rounded-xl border p-3 text-sm ${toneClass}`}>
      <span className="mt-0.5 shrink-0">{icon}</span>
      <span className="min-w-0 leading-5">{text}</span>
    </div>
  );
}

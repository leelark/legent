'use client';

import { useEffect, useMemo, useState } from 'react';
import { Activity, AlertTriangle, CheckCircle, Flame, GitBranch, MousePointerClick, RefreshCw, Route } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';
import { PageHeader } from '@/components/ui/PageChrome';
import { Skeleton } from '@/components/ui/Skeleton';
import { get } from '@/lib/api-client';
import { getWorkflowAnalytics, listWorkflows, type Workflow } from '@/lib/automation-api';

interface EventCount {
  event_type: string;
  count: number;
}

interface CampaignSummary {
  id?: string;
  campaignId?: string;
  totalSends?: number;
  totalOpens?: number;
  totalClicks?: number;
  totalConversions?: number;
}

interface StepMetric {
  nodeId?: string;
  nodeType?: string;
  label?: string;
  entered?: number;
  completed?: number;
  failed?: number;
  completionRate?: number;
  failureRate?: number;
}

interface PathMetric {
  signature?: string;
  nodes?: string[];
  runs?: number;
  completed?: number;
  failed?: number;
  completionRate?: number;
}

interface PathTest {
  nodeId?: string;
  nodeType?: string;
  label?: string;
  observedRuns?: number;
  observedTargets?: Record<string, number>;
  interpretation?: string;
}

interface FlowDiagnostic {
  code?: string;
  severity?: string;
  message?: string;
  sampleSize?: number;
}

interface WorkflowAnalytics {
  workflowId?: string;
  runCount?: number;
  runStatusCounts?: Record<string, number>;
  stepMetrics?: StepMetric[];
  topPaths?: PathMetric[];
  pathTests?: PathTest[];
  conversionGoals?: Array<{ goalId?: string; label?: string; hits?: number; observedRunRate?: number }>;
  diagnostics?: FlowDiagnostic[];
  evidenceNotes?: string[];
}

interface JourneyGoalMetric {
  goal_id?: string;
  step_id?: string;
  path_id?: string;
  experiment_scope?: string;
  conversions?: number;
  unique_subscribers?: number;
  revenue?: number | string;
}

const eventLabels: Record<string, string> = {
  OPEN: 'Opens',
  CLICK: 'Clicks',
  CONVERSION: 'Conversions',
};

export default function AnalyticsDashboard() {
  const [eventCounts, setEventCounts] = useState<EventCount[]>([]);
  const [campaigns, setCampaigns] = useState<CampaignSummary[]>([]);
  const [workflows, setWorkflows] = useState<Workflow[]>([]);
  const [selectedWorkflowId, setSelectedWorkflowId] = useState<string>('');
  const [workflowAnalytics, setWorkflowAnalytics] = useState<WorkflowAnalytics | null>(null);
  const [journeyGoals, setJourneyGoals] = useState<JourneyGoalMetric[]>([]);
  const [loading, setLoading] = useState(true);
  const [flowLoading, setFlowLoading] = useState(false);

  const load = async () => {
    setLoading(true);
    const [counts, camps, flows] = await Promise.allSettled([
      get<EventCount[]>('/analytics/events/counts'),
      get<CampaignSummary[]>('/analytics/campaigns'),
      listWorkflows(),
    ]);
    const nextWorkflows = flows.status === 'fulfilled' && Array.isArray(flows.value) ? flows.value : [];
    setEventCounts(counts.status === 'fulfilled' && Array.isArray(counts.value) ? counts.value : []);
    setCampaigns(camps.status === 'fulfilled' && Array.isArray(camps.value) ? camps.value : []);
    setWorkflows(nextWorkflows);
    setSelectedWorkflowId((current) => {
      if (current && nextWorkflows.some((workflow) => workflow.id === current)) {
        return current;
      }
      return nextWorkflows[0]?.id || '';
    });
    setLoading(false);
  };

  useEffect(() => {
    void load();
  }, []);

  useEffect(() => {
    const loadFlowAnalytics = async () => {
      if (!selectedWorkflowId) {
        setWorkflowAnalytics(null);
        setJourneyGoals([]);
        return;
      }
      setFlowLoading(true);
      const [analytics, goals] = await Promise.allSettled([
        getWorkflowAnalytics(selectedWorkflowId),
        get<JourneyGoalMetric[]>(`/analytics/journeys/${selectedWorkflowId}/goals`),
      ]);
      setWorkflowAnalytics(analytics.status === 'fulfilled' ? analytics.value as WorkflowAnalytics : null);
      setJourneyGoals(goals.status === 'fulfilled' && Array.isArray(goals.value) ? goals.value : []);
      setFlowLoading(false);
    };
    void loadFlowAnalytics();
  }, [selectedWorkflowId]);

  const totals = useMemo(() => {
    const find = (type: string) => eventCounts.find((event) => event.event_type === type)?.count || 0;
    return {
      opens: find('OPEN'),
      clicks: find('CLICK'),
      conversions: find('CONVERSION'),
      campaigns: campaigns.length,
    };
  }, [campaigns.length, eventCounts]);

  const maxCount = Math.max(1, ...eventCounts.map((event) => event.count));
  const selectedWorkflow = workflows.find((workflow) => workflow.id === selectedWorkflowId);
  const flowTotals = useMemo(() => {
    const statusCounts = workflowAnalytics?.runStatusCounts || {};
    const failures = Object.entries(statusCounts)
      .filter(([status]) => status.toUpperCase() === 'FAILED')
      .reduce((sum, [, value]) => sum + Number(value || 0), 0);
    const goalHits = (workflowAnalytics?.conversionGoals || []).reduce((sum, goal) => sum + Number(goal.hits || 0), 0);
    const trackedConversions = journeyGoals.reduce((sum, goal) => sum + Number(goal.conversions || 0), 0);
    return {
      runs: Number(workflowAnalytics?.runCount || 0),
      failedRuns: failures,
      goalHits,
      trackedConversions,
    };
  }, [journeyGoals, workflowAnalytics]);

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Live telemetry"
        title="Analytics Overview"
        description="Aggregate delivery and engagement metrics from tracking services."
        action={(
          <Button variant="secondary" icon={<RefreshCw size={16} />} onClick={() => void load()} loading={loading}>
            Refresh
          </Button>
        )}
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard label="Total Opens" value={totals.opens} helper="Tracked open events" icon={<Activity size={18} />} />
        <MetricCard label="Total Clicks" value={totals.clicks} helper="Tracked click events" icon={<MousePointerClick size={18} />} />
        <MetricCard label="Conversions" value={totals.conversions} helper="Goal events captured" icon={<CheckCircle size={18} />} />
        <MetricCard label="Active Campaigns" value={totals.campaigns} helper="Campaign summaries" icon={<Flame size={18} />} />
      </div>

      <div className="grid gap-4 lg:grid-cols-[1.2fr_0.8fr]">
        <Card>
          <CardHeader>
            <CardTitle>Engagement Mix</CardTitle>
            <CardDescription>Event distribution by type.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {loading ? (
              <div className="space-y-4 py-6">
                {Array.from({ length: 4 }).map((_, index) => (
                  <Skeleton key={index} className="h-12 rounded-lg" />
                ))}
              </div>
            ) : eventCounts.length === 0 ? (
              <EmptyState type="empty" title="No event data" description="Engagement events will appear after campaigns generate activity." />
            ) : (
              eventCounts.map((event) => (
                <div key={event.event_type} className="space-y-2">
                  <div className="flex items-center justify-between text-sm">
                    <span className="font-medium">{eventLabels[event.event_type] || event.event_type}</span>
                    <span className="font-semibold">{event.count.toLocaleString()}</span>
                  </div>
                  <div className="h-2 overflow-hidden rounded-full bg-surface-secondary">
                    <div className="h-full rounded-full bg-gradient-to-r from-brand-700 to-brand-300" style={{ width: `${Math.max(4, (event.count / maxCount) * 100)}%` }} />
                  </div>
                </div>
              ))
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Top Campaigns</CardTitle>
            <CardDescription>Recent campaign summaries.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {loading ? (
              <div className="space-y-3 py-4">
                {Array.from({ length: 4 }).map((_, index) => (
                  <Skeleton key={index} className="h-16 rounded-lg" />
                ))}
              </div>
            ) : campaigns.length === 0 ? (
              <EmptyState type="empty" title="No campaigns tracked" description="Campaign summaries will appear once sends are recorded." />
            ) : (
              campaigns.slice(0, 6).map((campaign) => {
                const sends = campaign.totalSends || 0;
                const ctr = sends ? Math.round(((campaign.totalClicks || 0) / sends) * 100) : 0;
                return (
                  <div key={campaign.campaignId || campaign.id} className="rounded-lg border border-border-default bg-surface-secondary p-3">
                    <div className="flex items-center justify-between gap-3">
                      <p className="truncate text-sm font-semibold">{campaign.campaignId || campaign.id || 'Campaign'}</p>
                      <Badge variant={ctr >= 10 ? 'success' : 'default'}>{ctr}% CTR</Badge>
                    </div>
                    <p className="mt-1 text-xs text-content-secondary">Opens {campaign.totalOpens || 0} / Clicks {campaign.totalClicks || 0}</p>
                  </div>
                );
              })
            )}
          </CardContent>
        </Card>
      </div>

      <div className="space-y-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="text-xs font-medium uppercase tracking-wider text-content-secondary">Flow analytics</p>
            <h2 className="mt-1 text-xl font-semibold text-content-primary">Journey Path Performance</h2>
          </div>
          <select
            aria-label="Workflow"
            className="min-h-10 rounded-md border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary shadow-sm outline-none focus:border-brand-500"
            value={selectedWorkflowId}
            onChange={(event) => setSelectedWorkflowId(event.target.value)}
            disabled={workflows.length === 0}
          >
            {workflows.length === 0 ? (
              <option value="">No workflows</option>
            ) : (
              workflows.map((workflow) => (
                <option key={workflow.id} value={workflow.id}>{workflow.name || workflow.id}</option>
              ))
            )}
          </select>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <MetricCard label="Journey Runs" value={flowTotals.runs} helper={selectedWorkflow?.name || 'Selected workflow'} icon={<Route size={18} />} />
          <MetricCard label="Goal Hits" value={flowTotals.goalHits} helper="Observed exit-goal steps" icon={<CheckCircle size={18} />} />
          <MetricCard label="Tracked Goals" value={flowTotals.trackedConversions} helper="Tracking conversion events" icon={<Activity size={18} />} />
          <MetricCard label="Failed Runs" value={flowTotals.failedRuns} helper="Deterministic run status" icon={<AlertTriangle size={18} />} />
        </div>

        <div className="grid gap-4 xl:grid-cols-[1.1fr_0.9fr]">
          <Card>
            <CardHeader>
              <CardTitle>Step Metrics</CardTitle>
              <CardDescription>Observed workflow node execution and failure counts.</CardDescription>
            </CardHeader>
            <CardContent>
              {flowLoading ? (
                <div className="space-y-3 py-4">
                  {Array.from({ length: 4 }).map((_, index) => (
                    <Skeleton key={index} className="h-14 rounded-lg" />
                  ))}
                </div>
              ) : !workflowAnalytics || (workflowAnalytics.stepMetrics || []).length === 0 ? (
                <EmptyState type="empty" title="No journey activity" description="Workflow step metrics will appear after journeys execute." />
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[620px] text-left text-sm">
                    <thead className="text-xs uppercase tracking-wider text-content-muted">
                      <tr>
                        <th className="py-2 pr-3 font-medium">Step</th>
                        <th className="py-2 pr-3 font-medium">Type</th>
                        <th className="py-2 pr-3 font-medium">Entered</th>
                        <th className="py-2 pr-3 font-medium">Completed</th>
                        <th className="py-2 pr-3 font-medium">Failed</th>
                        <th className="py-2 font-medium">Completion</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border-subtle">
                      {(workflowAnalytics.stepMetrics || []).slice(0, 8).map((step) => (
                        <tr key={step.nodeId || step.label}>
                          <td className="py-3 pr-3 font-medium text-content-primary">{step.label || step.nodeId || 'Step'}</td>
                          <td className="py-3 pr-3 text-content-secondary">{step.nodeType || 'UNKNOWN'}</td>
                          <td className="py-3 pr-3">{formatNumber(step.entered)}</td>
                          <td className="py-3 pr-3">{formatNumber(step.completed)}</td>
                          <td className="py-3 pr-3">{formatNumber(step.failed)}</td>
                          <td className="py-3">{formatPercent(step.completionRate)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Observed Paths</CardTitle>
              <CardDescription>Top path signatures from bounded workflow runs.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              {flowLoading ? (
                <div className="space-y-3 py-4">
                  {Array.from({ length: 3 }).map((_, index) => (
                    <Skeleton key={index} className="h-20 rounded-lg" />
                  ))}
                </div>
              ) : !workflowAnalytics || (workflowAnalytics.topPaths || []).length === 0 ? (
                <EmptyState type="empty" title="No paths observed" description="Path signatures will appear after workflow steps are recorded." />
              ) : (
                (workflowAnalytics.topPaths || []).slice(0, 5).map((path) => (
                  <div key={path.signature} className="rounded-lg border border-border-default bg-surface-secondary p-3">
                    <div className="flex items-center justify-between gap-3">
                      <p className="min-w-0 truncate text-sm font-semibold text-content-primary">{path.signature}</p>
                      <Badge variant={(path.failed || 0) > 0 ? 'warning' : 'success'}>{formatNumber(path.runs)} runs</Badge>
                    </div>
                    <p className="mt-1 text-xs text-content-secondary">Completed {formatNumber(path.completed)} / Failed {formatNumber(path.failed)} / Rate {formatPercent(path.completionRate)}</p>
                  </div>
                ))
              )}
            </CardContent>
          </Card>
        </div>

        <div className="grid gap-4 lg:grid-cols-3">
          <FlowListCard
            icon={<GitBranch size={16} />}
            title="Journey Path Tests"
            empty="No path tests observed"
            items={(workflowAnalytics?.pathTests || []).slice(0, 4).map((test) => ({
              title: test.label || test.nodeId || 'Decision step',
              detail: `${formatNumber(test.observedRuns)} observed runs`,
              meta: Object.entries(test.observedTargets || {}).map(([target, count]) => `${target}: ${count}`).join(' / ') || test.interpretation || 'Observed path distribution',
            }))}
          />
          <FlowListCard
            icon={<CheckCircle size={16} />}
            title="Conversion Goals"
            empty="No tracked goals"
            items={[
              ...(workflowAnalytics?.conversionGoals || []).slice(0, 3).map((goal) => ({
                title: goal.label || goal.goalId || 'Goal',
                detail: `${formatNumber(goal.hits)} workflow hits`,
                meta: `Observed run rate ${formatPercent(goal.observedRunRate)}`,
              })),
              ...journeyGoals.slice(0, 3).map((goal) => ({
                title: goal.goal_id || 'Tracking goal',
                detail: `${formatNumber(goal.conversions)} tracking conversions`,
                meta: `${goal.experiment_scope || 'JOURNEY'} / ${goal.path_id || goal.step_id || 'no path'}`,
              })),
            ]}
          />
          <FlowListCard
            icon={<AlertTriangle size={16} />}
            title="Deterministic Signals"
            empty="No deterministic signals"
            items={(workflowAnalytics?.diagnostics || []).map((diagnostic) => ({
              title: diagnostic.code || 'Signal',
              detail: diagnostic.message || 'Threshold signal',
              meta: `Sample ${formatNumber(diagnostic.sampleSize)}`,
            }))}
          />
        </div>
      </div>
    </div>
  );
}

function MetricCard({ label, value, helper, icon }: { label: string; value: number; helper: string; icon: React.ReactNode }) {
  return (
    <Card>
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-medium uppercase tracking-wider text-content-secondary">{label}</p>
          <p className="mt-2 text-3xl font-semibold text-content-primary">{value.toLocaleString()}</p>
          <p className="mt-1 text-xs text-content-muted">{helper}</p>
        </div>
        <div className="rounded-lg border border-brand-500/20 bg-brand-500/10 p-2 text-brand-600 dark:text-brand-300">{icon}</div>
      </div>
    </Card>
  );
}

function FlowListCard({
  icon,
  title,
  empty,
  items,
}: {
  icon: React.ReactNode;
  title: string;
  empty: string;
  items: Array<{ title: string; detail: string; meta: string }>;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">{icon}{title}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {items.length === 0 ? (
          <EmptyState type="empty" title={empty} description="Signals appear after bounded journey analytics has enough local evidence." />
        ) : (
          items.map((item) => (
            <div key={`${item.title}-${item.detail}`} className="rounded-lg border border-border-default bg-surface-secondary p-3">
              <p className="text-sm font-semibold text-content-primary">{item.title}</p>
              <p className="mt-1 text-sm text-content-secondary">{item.detail}</p>
              <p className="mt-1 text-xs text-content-muted">{item.meta}</p>
            </div>
          ))
        )}
      </CardContent>
    </Card>
  );
}

function formatNumber(value: unknown) {
  return Number(value || 0).toLocaleString();
}

function formatPercent(value: unknown) {
  const numeric = Number(value || 0);
  return `${Math.round(numeric * 100)}%`;
}

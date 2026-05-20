'use client';

import { useEffect, useState } from 'react';
import { Card, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { Input } from '@/components/ui/Input';
import { PageHeader } from '@/components/ui/PageChrome';
import { Skeleton } from '@/components/ui/Skeleton';
import {
  AutomationActivity,
  AutomationActivityRun,
  AutomationActivityType,
  archiveWorkflow,
  cloneWorkflow,
  createAutomationActivity,
  createWorkflow,
  listAutomationActivities,
  listAutomationActivityRuns,
  listWorkflows,
  pauseWorkflow,
  publishWorkflow,
  resumeWorkflow,
  runAutomationActivity,
  stopWorkflow,
  verifyAutomationActivity,
  Workflow,
} from '@/lib/automation-api';
import { Plus } from '@phosphor-icons/react';
import Link from 'next/link';
import { AUTOMATION_WORKFLOW_MODE_FEATURES, isModeFeatureVisible } from '@/lib/ui-mode-contract';
import { useUIStore } from '@/stores/uiStore';

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null;

const readStringPath = (value: unknown, path: string[]) => {
  let current: unknown = value;
  for (const key of path) {
    if (!isRecord(current)) {
      return undefined;
    }
    current = current[key];
  }
  return typeof current === 'string' && current.length > 0 ? current : undefined;
};

const getAutomationErrorMessage = (error: unknown, fallback: string) =>
  readStringPath(error, ['normalized', 'message']) ??
  readStringPath(error, ['response', 'data', 'error', 'message']) ??
  fallback;

const formatRunTimestamp = (value?: string) => {
  if (!value) {
    return 'Not recorded';
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return parsed.toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};

const runStatusVariant = (status?: AutomationActivityRun['status']): 'success' | 'danger' | 'info' | 'default' => {
  if (status === 'SUCCEEDED') return 'success';
  if (status === 'FAILED') return 'danger';
  if (status === 'VERIFIED') return 'info';
  return 'default';
};

export default function AutomationPage() {
  const uiMode = useUIStore((state) => state.uiMode);
  const [workflows, setWorkflows] = useState<Workflow[]>([]);
  const [activities, setActivities] = useState<AutomationActivity[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [showCreateActivity, setShowCreateActivity] = useState(false);
  const [newName, setNewName] = useState('');
  const [newDescription, setNewDescription] = useState('');
  const [activityName, setActivityName] = useState('');
  const [activityType, setActivityType] = useState<AutomationActivityType>('SQL_QUERY');
  const [activitySql, setActivitySql] = useState('SELECT subscriber_key, email FROM subscribers');
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [actionBusy, setActionBusy] = useState<string | null>(null);
  const [expandedRunHistory, setExpandedRunHistory] = useState<string[]>([]);
  const [activityRuns, setActivityRuns] = useState<Record<string, AutomationActivityRun[]>>({});
  const [runHistoryLoading, setRunHistoryLoading] = useState<Record<string, boolean>>({});
  const [runHistoryError, setRunHistoryError] = useState<Record<string, string | undefined>>({});
  const activeCount = workflows.filter((workflow) => workflow.status === 'ACTIVE').length;
  const draftCount = workflows.filter((workflow) => workflow.status === 'DRAFT').length;
  const pausedCount = workflows.filter((workflow) => workflow.status === 'PAUSED' || workflow.status === 'SCHEDULED').length;
  const activeActivityCount = activities.filter((activity) => activity.status === 'ACTIVE').length;
  const showActivityAuthoring = isModeFeatureVisible(AUTOMATION_WORKFLOW_MODE_FEATURES.activityAuthoring, uiMode);
  const showActivityExecution = isModeFeatureVisible(AUTOMATION_WORKFLOW_MODE_FEATURES.activityExecution, uiMode);

  const loadWorkflows = async () => {
    setLoading(true);
    try {
      const [data, activityData] = await Promise.all([
        listWorkflows(),
        listAutomationActivities(),
      ]);
      setWorkflows(data);
      setActivities(activityData);
    } catch (e: unknown) {
      setError(getAutomationErrorMessage(e, 'Failed to load workflows'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadWorkflows();
  }, []);

  const loadActivityRuns = async (activityId: string) => {
    setRunHistoryLoading((current) => ({ ...current, [activityId]: true }));
    setRunHistoryError((current) => ({ ...current, [activityId]: undefined }));
    try {
      const runs = await listAutomationActivityRuns(activityId);
      setActivityRuns((current) => ({ ...current, [activityId]: runs.slice(0, 5) }));
    } catch (e: unknown) {
      setRunHistoryError((current) => ({
        ...current,
        [activityId]: getAutomationErrorMessage(e, 'Failed to load run history'),
      }));
    } finally {
      setRunHistoryLoading((current) => ({ ...current, [activityId]: false }));
    }
  };

  const toggleRunHistory = async (activityId: string) => {
    if (expandedRunHistory.includes(activityId)) {
      setExpandedRunHistory((current) => current.filter((id) => id !== activityId));
      return;
    }
    setExpandedRunHistory((current) => [...current, activityId]);
    if (!activityRuns[activityId]) {
      await loadActivityRuns(activityId);
    }
  };

  const handleCreate = async () => {
    if (!newName.trim()) {
      setError('Workflow name is required');
      return;
    }
    setCreating(true);
    setError(null);
    try {
      await createWorkflow({ name: newName.trim(), description: newDescription.trim(), status: 'DRAFT' });
      setNewName('');
      setNewDescription('');
      setShowCreate(false);
      loadWorkflows();
    } catch (e: unknown) {
      setError(getAutomationErrorMessage(e, 'Failed to create workflow'));
    } finally {
      setCreating(false);
    }
  };

  const handleCreateActivity = async () => {
    if (!showActivityAuthoring) {
      return;
    }
    if (!activityName.trim()) {
      setError('Activity name is required');
      return;
    }
    setCreating(true);
    setError(null);
    try {
      const inputConfig =
        activityType === 'SQL_QUERY'
          ? { sql: activitySql }
          : activityType === 'WEBHOOK'
            ? { url: 'https://example.com/webhook', method: 'POST' }
            : activityType === 'FILE_DROP'
              ? { locationPattern: 's3://bucket/path/*.csv' }
              : activityType === 'IMPORT'
                ? { sourceLocation: 's3://bucket/file.csv', targetType: 'DATA_EXTENSION', targetId: 'data-extension-id', fieldMapping: {} }
                : activityType === 'EXTRACT'
                  ? { sourceType: 'RAW_EVENTS' }
                  : { scriptRef: 'signed-script-artifact' };
      const outputConfig = activityType === 'SQL_QUERY' ? { targetDataExtensionId: 'data-extension-id' } : { destination: 's3://bucket/output/' };
      await createAutomationActivity({
        name: activityName.trim(),
        activityType,
        status: 'DRAFT',
        inputConfig,
        outputConfig,
      });
      setActivityName('');
      setShowCreateActivity(false);
      await loadWorkflows();
    } catch (e: unknown) {
      setError(getAutomationErrorMessage(e, 'Failed to create activity'));
    } finally {
      setCreating(false);
    }
  };

  const runActivityAction = async (activityId: string, mode: 'verify' | 'dryRun') => {
    if (!showActivityExecution) {
      return;
    }
    setActionBusy(`${activityId}:${mode}`);
    try {
      if (mode === 'verify') {
        await verifyAutomationActivity(activityId);
      } else {
        await runAutomationActivity(activityId, { dryRun: true, triggerSource: 'MANUAL' });
      }
      await loadWorkflows();
      if (mode === 'dryRun' && expandedRunHistory.includes(activityId)) {
        await loadActivityRuns(activityId);
      }
    } catch (e: unknown) {
      setError(getAutomationErrorMessage(e, `Failed to ${mode} activity`));
    } finally {
      setActionBusy(null);
    }
  };

  const transitionWorkflow = async (workflowId: string, action: 'publish' | 'pause' | 'resume' | 'archive' | 'stop' | 'clone') => {
    setActionBusy(`${workflowId}:${action}`);
    try {
      if (action === 'publish') await publishWorkflow(workflowId);
      if (action === 'pause') await pauseWorkflow(workflowId);
      if (action === 'resume') await resumeWorkflow(workflowId);
      if (action === 'archive') await archiveWorkflow(workflowId);
      if (action === 'stop') await stopWorkflow(workflowId);
      if (action === 'clone') await cloneWorkflow(workflowId);
      await loadWorkflows();
    } catch (e: unknown) {
      setError(getAutomationErrorMessage(e, `Failed to ${action} workflow`));
    } finally {
      setActionBusy(null);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Journey orchestration"
        title="Automation"
        description="Build customer journeys with draft control, publish gates, rollback posture, and execution visibility."
        action={<Button icon={<Plus size={16} />} onClick={() => setShowCreate(!showCreate)}>New Workflow</Button>}
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {[
          { label: 'Active journeys', value: activeCount, detail: 'running lifecycle paths' },
          { label: 'Drafts', value: draftCount, detail: 'waiting for review' },
          { label: 'Paused or scheduled', value: pausedCount, detail: 'operator controlled' },
          { label: 'Studio activities', value: activeActivityCount, detail: `${activities.length} configured` },
        ].map((stat) => (
          <Card key={stat.label}>
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-content-secondary">{stat.label}</p>
            <div className="mt-3 flex items-end justify-between gap-3">
              <p className="text-3xl font-semibold text-content-primary">{loading ? '-' : stat.value}</p>
              <span className="rounded-full border border-brand-500/20 bg-brand-500/10 px-2 py-1 text-xs font-semibold text-brand-600 dark:text-brand-300">Live</span>
            </div>
            <p className="mt-2 text-xs text-content-muted">{stat.detail}</p>
          </Card>
        ))}
      </div>

      {showCreate && (
        <Card>
          <CardHeader title="Create Workflow" />
          <div className="p-6 space-y-4">
            {error && <p className="text-sm text-danger">{error}</p>}
            <Input label="Workflow Name" value={newName} onChange={(e) => setNewName(e.target.value)} placeholder="Welcome Series" />
            <div>
              <label className="mb-1 block text-sm font-medium text-content-primary">Description</label>
              <textarea
                value={newDescription}
                onChange={(e) => setNewDescription(e.target.value)}
                placeholder="Trigger: subscriber joins list..."
                rows={3}
                className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-sm text-content-primary placeholder-content-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/30 transition-all"
              />
            </div>
            <div className="flex flex-wrap gap-3">
              <Button onClick={handleCreate} loading={creating} disabled={creating}>Create</Button>
              <Button variant="secondary" onClick={() => setShowCreate(false)}>Cancel</Button>
            </div>
          </div>
        </Card>
      )}

      <Card className="overflow-hidden !p-0">
        <div className="border-b border-border-default bg-surface-secondary/60 p-5">
          <CardHeader
            title="Automation Studio Activities"
            subtitle="SQL, file-drop, import, extract, script, and webhook activities with verification and run history."
            action={showActivityAuthoring ? (
              <Button
                icon={<Plus size={16} />}
                onClick={() => setShowCreateActivity(!showCreateActivity)}
                data-mode-feature={AUTOMATION_WORKFLOW_MODE_FEATURES.activityAuthoring.id}
                data-mode-visibility={AUTOMATION_WORKFLOW_MODE_FEATURES.activityAuthoring.visibility}
              >
                New Activity
              </Button>
            ) : undefined}
            className="mb-0"
          />
        </div>
        {showActivityAuthoring && showCreateActivity && (
          <div
            className="grid gap-3 border-b border-border-default p-5 md:grid-cols-[1fr_180px_1fr_auto]"
            data-mode-feature={AUTOMATION_WORKFLOW_MODE_FEATURES.activityAuthoring.id}
            data-mode-visibility={AUTOMATION_WORKFLOW_MODE_FEATURES.activityAuthoring.visibility}
          >
            <Input label="Activity Name" value={activityName} onChange={(event) => setActivityName(event.target.value)} />
            <div>
              <label className="mb-1 block text-sm font-medium text-content-primary">Type</label>
              <select
                value={activityType}
                onChange={(event) => setActivityType(event.target.value as AutomationActivityType)}
                className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-sm text-content-primary"
              >
                {['SQL_QUERY', 'FILE_DROP', 'IMPORT', 'EXTRACT', 'SCRIPT', 'WEBHOOK'].map((type) => (
                  <option key={type} value={type}>{type}</option>
                ))}
              </select>
            </div>
            <Input label="SQL" value={activitySql} onChange={(event) => setActivitySql(event.target.value)} />
            <Button className="mt-6" onClick={handleCreateActivity} loading={creating}>Create</Button>
          </div>
        )}
        <div className="divide-y divide-border-default">
          {activities.length === 0 ? (
            <div className="p-5 text-sm text-content-secondary">No automation activities configured.</div>
          ) : activities.map((activity) => {
            const isExpanded = expandedRunHistory.includes(activity.id);
            const runs = activityRuns[activity.id] ?? [];
            const historyError = runHistoryError[activity.id];
            const historyLoading = runHistoryLoading[activity.id];

            return (
              <div key={activity.id} className="p-4">
                <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                  <div>
                    <p className="font-semibold text-content-primary">{activity.name}</p>
                    <p className="text-sm text-content-secondary">{activity.activityType}</p>
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge variant={activity.status === 'ACTIVE' ? 'success' : 'default'}>{activity.status}</Badge>
                    <Button
                      size="sm"
                      variant="secondary"
                      aria-expanded={isExpanded}
                      aria-label={`${isExpanded ? 'Hide' : 'Show'} run history for ${activity.name}`}
                      onClick={() => toggleRunHistory(activity.id)}
                    >
                      {isExpanded ? 'Hide history' : 'Run history'}
                    </Button>
                    {showActivityExecution && (
                      <>
                        <Button
                          size="sm"
                          variant="secondary"
                          loading={actionBusy === `${activity.id}:verify`}
                          onClick={() => runActivityAction(activity.id, 'verify')}
                          data-mode-feature={AUTOMATION_WORKFLOW_MODE_FEATURES.activityExecution.id}
                          data-mode-visibility={AUTOMATION_WORKFLOW_MODE_FEATURES.activityExecution.visibility}
                        >
                          Verify
                        </Button>
                        <Button
                          size="sm"
                          loading={actionBusy === `${activity.id}:dryRun`}
                          onClick={() => runActivityAction(activity.id, 'dryRun')}
                          data-mode-feature={AUTOMATION_WORKFLOW_MODE_FEATURES.activityExecution.id}
                          data-mode-visibility={AUTOMATION_WORKFLOW_MODE_FEATURES.activityExecution.visibility}
                        >
                          Dry Run
                        </Button>
                      </>
                    )}
                  </div>
                </div>
                {isExpanded && (
                  <div className="mt-4 rounded-lg border border-border-default bg-surface-secondary/60 p-3">
                    <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                      <p className="text-sm font-semibold text-content-primary">Recent runs</p>
                      <Button
                        size="sm"
                        variant="ghost"
                        loading={historyLoading}
                        onClick={() => loadActivityRuns(activity.id)}
                      >
                        Refresh
                      </Button>
                    </div>
                    {historyLoading ? (
                      <p className="text-sm text-content-secondary">Loading recent runs...</p>
                    ) : historyError ? (
                      <div className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">
                        {historyError}
                      </div>
                    ) : runs.length === 0 ? (
                      <p className="text-sm text-content-secondary">No recent runs recorded.</p>
                    ) : (
                      <div className="grid gap-2" role="list" aria-label={`Recent runs for ${activity.name}`}>
                        {runs.map((run) => (
                          <div
                            key={run.id}
                            role="listitem"
                            className="grid gap-3 rounded-lg border border-border-default bg-surface-primary p-3 text-sm md:grid-cols-[minmax(150px,0.9fr)_minmax(180px,1fr)_minmax(120px,0.8fr)] md:items-center"
                          >
                            <div className="flex flex-wrap items-center gap-2">
                              <Badge variant={runStatusVariant(run.status)}>{run.status}</Badge>
                              <Badge variant={run.dryRun ? 'info' : 'warning'}>{run.dryRun ? 'Dry run' : 'Live'}</Badge>
                            </div>
                            <div className="grid gap-1 text-content-secondary sm:grid-cols-2">
                              <span>Trigger: {run.triggerSource || 'Unknown'}</span>
                              <span>Rows: {run.rowsRead ?? 0} read, {run.rowsWritten ?? 0} written</span>
                              <span>Started: {formatRunTimestamp(run.startedAt)}</span>
                              <span>Completed: {formatRunTimestamp(run.completedAt)}</span>
                            </div>
                            {run.errorMessage ? (
                              <p className="rounded-md bg-danger/10 px-2 py-1 text-xs font-medium text-danger">{run.errorMessage}</p>
                            ) : (
                              <p className="text-xs font-medium text-content-muted">No error reported</p>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </Card>

      <Card className="overflow-hidden !p-0">
        <div className="border-b border-border-default bg-surface-secondary/60 p-5">
          <CardHeader title="Workflows" subtitle="Draft, publish, pause, clone, and archive journeys from one control surface." action={<Badge variant="info">{workflows.length} items</Badge>} className="mb-0" />
        </div>
        <div className="p-5">
        {error && <div className="mb-4 rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">{error}</div>}
        {loading ? (
          <div className="space-y-3">
            {Array.from({ length: 4 }).map((_, index) => (
              <Skeleton key={index} className="h-20 rounded-xl" />
            ))}
          </div>
        ) : workflows.length === 0 ? (
          <div className="grid gap-4 lg:grid-cols-[0.9fr_1.1fr]">
            <div className="rounded-xl border border-dashed border-border-default bg-surface-secondary/70 p-6">
              <EmptyState
                type="empty"
                title="No workflows yet"
                description="Create a governed lifecycle path, then publish when branch logic and approvals are ready."
                action={<Button icon={<Plus size={16} />} onClick={() => setShowCreate(true)}>Create Workflow</Button>}
              />
            </div>
            <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-1">
              {[
                ['Trigger', 'Start from subscriber joins, segment entry, or campaign event.'],
                ['Decide', 'Branch by audience state, consent, engagement, or readiness score.'],
                ['Deliver', 'Send through approved templates with provider-safe timing.'],
              ].map(([label, detail], index) => (
                <div key={label} className="rounded-xl border border-border-default bg-surface-secondary/70 p-4">
                  <div className="flex items-center gap-3">
                    <span className="grid h-8 w-8 place-items-center rounded-lg bg-brand-500/10 text-sm font-semibold text-brand-600 dark:text-brand-300">{index + 1}</span>
                    <p className="font-semibold text-content-primary">{label}</p>
                  </div>
                  <p className="mt-2 text-sm leading-6 text-content-secondary">{detail}</p>
                </div>
              ))}
            </div>
          </div>
        ) : (
          <div className="grid gap-0 divide-y divide-border-default">
            {workflows.map((wf) => (
              <div key={wf.id} className="flex flex-col gap-3 p-4 hover:bg-surface-secondary xl:flex-row xl:items-center xl:justify-between">
                <div className="min-w-0">
                  <p className="font-semibold text-content-primary">{wf.name}</p>
                  <p className="truncate text-sm text-content-secondary">{wf.description || 'No description'}</p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <Badge variant={wf.status === 'ACTIVE' ? 'success' : 'default'}>{wf.status}</Badge>
                  <Link href={`/app/automations/builder?id=${wf.id}`}>
                    <Button variant="secondary" size="sm">Open Builder</Button>
                  </Link>
                  <Button size="sm" variant="secondary" loading={actionBusy === `${wf.id}:clone`} onClick={() => transitionWorkflow(wf.id, 'clone')}>Clone</Button>
                  {wf.status === 'DRAFT' && (
                    <Button size="sm" loading={actionBusy === `${wf.id}:publish`} onClick={() => transitionWorkflow(wf.id, 'publish')}>Publish</Button>
                  )}
                  {wf.status === 'ACTIVE' && (
                    <Button size="sm" variant="secondary" loading={actionBusy === `${wf.id}:pause`} onClick={() => transitionWorkflow(wf.id, 'pause')}>Pause</Button>
                  )}
                  {wf.status === 'PAUSED' && (
                    <Button size="sm" loading={actionBusy === `${wf.id}:resume`} onClick={() => transitionWorkflow(wf.id, 'resume')}>Resume</Button>
                  )}
                  {(wf.status === 'ACTIVE' || wf.status === 'PAUSED' || wf.status === 'SCHEDULED') && (
                    <Button size="sm" variant="secondary" loading={actionBusy === `${wf.id}:stop`} onClick={() => transitionWorkflow(wf.id, 'stop')}>Stop</Button>
                  )}
                  {wf.status !== 'ARCHIVED' && (
                    <Button size="sm" variant="outline" loading={actionBusy === `${wf.id}:archive`} onClick={() => transitionWorkflow(wf.id, 'archive')}>Archive</Button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
        </div>
      </Card>
    </div>
  );
}

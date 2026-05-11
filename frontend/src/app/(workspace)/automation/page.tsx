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
  AutomationActivityType,
  archiveWorkflow,
  cloneWorkflow,
  createAutomationActivity,
  createWorkflow,
  listAutomationActivities,
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

export default function AutomationPage() {
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
  const activeCount = workflows.filter((workflow) => workflow.status === 'ACTIVE').length;
  const draftCount = workflows.filter((workflow) => workflow.status === 'DRAFT').length;
  const pausedCount = workflows.filter((workflow) => workflow.status === 'PAUSED' || workflow.status === 'SCHEDULED').length;
  const activeActivityCount = activities.filter((activity) => activity.status === 'ACTIVE').length;

  const loadWorkflows = async () => {
    setLoading(true);
    try {
      const [data, activityData] = await Promise.all([
        listWorkflows(),
        listAutomationActivities(),
      ]);
      setWorkflows(data);
      setActivities(activityData);
    } catch (e: any) {
      setError(e?.normalized?.message || e?.response?.data?.error?.message || 'Failed to load workflows');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadWorkflows();
  }, []);

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
    } catch (e: any) {
      setError(e?.normalized?.message || e?.response?.data?.error?.message || 'Failed to create workflow');
    } finally {
      setCreating(false);
    }
  };

  const handleCreateActivity = async () => {
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
    } catch (e: any) {
      setError(e?.normalized?.message || e?.response?.data?.error?.message || 'Failed to create activity');
    } finally {
      setCreating(false);
    }
  };

  const runActivityAction = async (activityId: string, mode: 'verify' | 'dryRun') => {
    setActionBusy(`${activityId}:${mode}`);
    try {
      if (mode === 'verify') {
        await verifyAutomationActivity(activityId);
      } else {
        await runAutomationActivity(activityId, { dryRun: true, triggerSource: 'MANUAL' });
      }
      await loadWorkflows();
    } catch (e: any) {
      setError(e?.normalized?.message || e?.response?.data?.error?.message || `Failed to ${mode} activity`);
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
    } catch (e: any) {
      setError(e?.normalized?.message || e?.response?.data?.error?.message || `Failed to ${action} workflow`);
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
            action={<Button icon={<Plus size={16} />} onClick={() => setShowCreateActivity(!showCreateActivity)}>New Activity</Button>}
            className="mb-0"
          />
        </div>
        {showCreateActivity && (
          <div className="grid gap-3 border-b border-border-default p-5 md:grid-cols-[1fr_180px_1fr_auto]">
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
          ) : activities.map((activity) => (
            <div key={activity.id} className="flex flex-col gap-3 p-4 xl:flex-row xl:items-center xl:justify-between">
              <div>
                <p className="font-semibold text-content-primary">{activity.name}</p>
                <p className="text-sm text-content-secondary">{activity.activityType}</p>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant={activity.status === 'ACTIVE' ? 'success' : 'default'}>{activity.status}</Badge>
                <Button size="sm" variant="secondary" loading={actionBusy === `${activity.id}:verify`} onClick={() => runActivityAction(activity.id, 'verify')}>Verify</Button>
                <Button size="sm" loading={actionBusy === `${activity.id}:dryRun`} onClick={() => runActivityAction(activity.id, 'dryRun')}>Dry Run</Button>
              </div>
            </div>
          ))}
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

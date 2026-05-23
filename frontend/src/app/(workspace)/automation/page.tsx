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
  AutomationFailurePolicy,
  AutomationVerificationResponse,
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

const runStatusVariant = (status?: AutomationActivityRun['status']): 'success' | 'danger' | 'warning' | 'info' | 'default' => {
  if (status === 'SUCCEEDED') return 'success';
  if (status === 'FAILED') return 'danger';
  if (status === 'VERIFIED') return 'info';
  if (status === 'LOCKED') return 'warning';
  return 'default';
};

const formatRetryAfter = (seconds?: number) => {
  if (!seconds || seconds <= 0) {
    return 'not provided';
  }
  if (seconds < 60) {
    return `${seconds} seconds`;
  }
  const minutes = Math.ceil(seconds / 60);
  return `${minutes} minute${minutes === 1 ? '' : 's'}`;
};

const ACTIVITY_TYPES: AutomationActivityType[] = ['SQL_QUERY', 'IMPORT', 'WEBHOOK', 'NOTIFICATION', 'FILE_DROP', 'EXTRACT', 'SCRIPT'];
const LIVE_SUPPORTED_ACTIVITY_TYPES = new Set<AutomationActivityType>(['SQL_QUERY', 'IMPORT', 'WEBHOOK', 'NOTIFICATION', 'SEND_EMAIL']);
const UNSAFE_ARTIFACT_REFERENCE_TOKENS = ['http://', 'https://', 's3://', 'gs://', 'file:'];

const isUnsafeArtifactReference = (value: string) => {
  const normalized = value.trim().toLowerCase();
  return (
    normalized.length === 0 ||
    normalized.startsWith('.') ||
    normalized.startsWith('/') ||
    normalized.includes('/') ||
    normalized.includes('\\') ||
    normalized.includes('..') ||
    UNSAFE_ARTIFACT_REFERENCE_TOKENS.some((token) => normalized.startsWith(token))
  );
};

const isUnsafeWebhookEventType = (value: string) => {
  const normalized = value.trim().toLowerCase();
  return !/^automation\.[a-z0-9][a-z0-9._-]{1,126}$/.test(normalized);
};

const parseJsonRecord = (value: string) => {
  if (!value.trim()) {
    return {};
  }
  const parsed = JSON.parse(value);
  if (!isRecord(parsed) || Array.isArray(parsed)) {
    throw new Error('Payload JSON must be an object');
  }
  return parsed;
};

const ACTIVITY_CAPABILITY: Record<AutomationActivityType, { label: string; description: string; liveSupported: boolean }> = {
  SQL_QUERY: {
    label: 'Live execution',
    description: 'SELECT-only query activity with dry-run support and bounded row controls.',
    liveSupported: true,
  },
  IMPORT: {
    label: 'Live execution',
    description: 'CSV import activity with explicit live confirmation on the backend.',
    liveSupported: true,
  },
  FILE_DROP: {
    label: 'Design only',
    description: 'File-drop detection is pending scoped artifact ownership and storage safety work.',
    liveSupported: false,
  },
  EXTRACT: {
    label: 'Design only',
    description: 'Extract and file-transfer work is pending artifact, retention, and adapter controls.',
    liveSupported: false,
  },
  SCRIPT: {
    label: 'Blocked',
    description: 'Script execution is blocked until signed artifact sandboxing is approved.',
    liveSupported: false,
  },
  WEBHOOK: {
    label: 'Live execution',
    description: 'Publishes guarded platform webhook events with bounded metadata and idempotent runs.',
    liveSupported: true,
  },
  NOTIFICATION: {
    label: 'Live execution',
    description: 'Publishes terminal-state platform notifications with scoped recipients and idempotent runs.',
    liveSupported: true,
  },
  SEND_EMAIL: {
    label: 'Live execution',
    description: 'Launches approved campaign sends from automation without overriding campaign safety controls.',
    liveSupported: true,
  },
};

const asStringArray = (value: unknown) => Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];

const asVerification = (value: unknown): AutomationVerificationResponse | undefined => {
  if (!isRecord(value)) {
    return undefined;
  }
  const valid = value.valid;
  return {
    valid: valid === true,
    errors: asStringArray(value.errors),
    warnings: asStringArray(value.warnings),
    normalizedConfig: isRecord(value.normalizedConfig) ? value.normalizedConfig : {},
  };
};

const formatFailurePolicy = (policy?: AutomationFailurePolicy) =>
  policy ? policy.replaceAll('_', ' ').toLowerCase() : 'stop on failure';

const toDisplayValue = (value: unknown) => {
  if (value === null || value === undefined || value === '') {
    return 'None';
  }
  if (Array.isArray(value)) {
    return value.length ? value.join(', ') : 'None';
  }
  if (typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value);
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
  const [activityTargetDataExtensionId, setActivityTargetDataExtensionId] = useState('');
  const [activityImportSource, setActivityImportSource] = useState('');
  const [activityImportTargetType, setActivityImportTargetType] = useState<'SUBSCRIBER' | 'DATA_EXTENSION'>('SUBSCRIBER');
  const [activityImportTargetId, setActivityImportTargetId] = useState('');
  const [activityImportEmailField, setActivityImportEmailField] = useState('Email Address');
  const [activityExtractSourceType, setActivityExtractSourceType] = useState('');
  const [activityExtractSourceId, setActivityExtractSourceId] = useState('');
  const [activityExtractDestination, setActivityExtractDestination] = useState('');
  const [activityWebhookEventType, setActivityWebhookEventType] = useState('automation.activity.completed');
  const [activityWebhookAuthRef, setActivityWebhookAuthRef] = useState('');
  const [activityWebhookPayloadJson, setActivityWebhookPayloadJson] = useState('');
  const [activityNotificationUserId, setActivityNotificationUserId] = useState('');
  const [activityNotificationTitle, setActivityNotificationTitle] = useState('');
  const [activityNotificationMessage, setActivityNotificationMessage] = useState('');
  const [activityNotificationSeverity, setActivityNotificationSeverity] = useState<'INFO' | 'WARNING' | 'ERROR' | 'SUCCESS'>('INFO');
  const [activityNotificationTerminalStatus, setActivityNotificationTerminalStatus] = useState<'SUCCEEDED' | 'FAILED'>('FAILED');
  const [activityNotificationLinkUrl, setActivityNotificationLinkUrl] = useState('/app/automation');
  const [activityScriptRef, setActivityScriptRef] = useState('');
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [actionBusy, setActionBusy] = useState<string | null>(null);
  const [activityActionErrors, setActivityActionErrors] = useState<Record<string, string | undefined>>({});
  const [verificationResults, setVerificationResults] = useState<Record<string, AutomationVerificationResponse | undefined>>({});
  const [lastActivityRuns, setLastActivityRuns] = useState<Record<string, AutomationActivityRun | undefined>>({});
  const [overrideReasons, setOverrideReasons] = useState<Record<string, string>>({});
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
  const selectedActivityCapability = ACTIVITY_CAPABILITY[activityType];

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
      const runs = await listAutomationActivityRuns(activityId, 5);
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
      const config = buildActivityConfig();
      if (!config) {
        setCreating(false);
        return;
      }
      await createAutomationActivity({
        name: activityName.trim(),
        activityType,
        status: 'DRAFT',
        failurePolicy: 'STOP_ON_FAILURE',
        inputConfig: config.inputConfig,
        outputConfig: config.outputConfig,
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

  const buildActivityConfig = () => {
    if (activityType === 'SQL_QUERY') {
      if (!activitySql.trim()) {
        setError('SQL activity requires a SELECT statement');
        return null;
      }
      return {
        inputConfig: { sql: activitySql.trim() },
        outputConfig: activityTargetDataExtensionId.trim()
          ? { targetDataExtensionId: activityTargetDataExtensionId.trim() }
          : {},
      };
    }
    if (activityType === 'IMPORT') {
      if (!activityImportSource.trim()) {
        setError('Import activity requires a scoped artifact ID');
        return null;
      }
      if (isUnsafeArtifactReference(activityImportSource)) {
        setError('Import activity requires an opaque scoped artifact ID');
        return null;
      }
      if (!activityImportEmailField.trim()) {
        setError('Import activity requires an email field mapping');
        return null;
      }
      if (activityImportTargetType === 'DATA_EXTENSION' && !activityImportTargetId.trim()) {
        setError('Data extension imports require a target data extension ID');
        return null;
      }
      return {
        inputConfig: {
          artifactId: activityImportSource.trim(),
          targetType: activityImportTargetType,
          ...(activityImportTargetType === 'DATA_EXTENSION' ? { targetId: activityImportTargetId.trim() } : {}),
          fieldMapping: { email: activityImportEmailField.trim() },
        },
        outputConfig: {},
      };
    }
    if (activityType === 'FILE_DROP') {
      if (!activityImportSource.trim()) {
        setError('File-drop activity requires a scoped artifact ID');
        return null;
      }
      if (isUnsafeArtifactReference(activityImportSource)) {
        setError('File-drop activity requires an opaque scoped artifact ID');
        return null;
      }
      return {
        inputConfig: { artifactId: activityImportSource.trim() },
        outputConfig: {},
      };
    }
    if (activityType === 'EXTRACT') {
      const sourceType = activityExtractSourceType.trim().toUpperCase();
      if (!sourceType || !activityExtractSourceId.trim() || !activityExtractDestination.trim()) {
        setError('Extract activity requires a source type, source ID, and output artifact ID');
        return null;
      }
      if (isUnsafeArtifactReference(activityExtractDestination)) {
        setError('Extract activity requires an opaque output artifact ID');
        return null;
      }
      if (sourceType !== 'DATA_EXTENSION' && isUnsafeArtifactReference(activityExtractSourceId)) {
        setError('Extract source artifact must be an opaque scoped artifact ID');
        return null;
      }
      return {
        inputConfig: {
          sourceType,
          ...(sourceType === 'DATA_EXTENSION'
            ? { sourceId: activityExtractSourceId.trim() }
            : { sourceArtifactId: activityExtractSourceId.trim() }),
        },
        outputConfig: { artifactId: activityExtractDestination.trim() },
      };
    }
    if (activityType === 'WEBHOOK') {
      if (isUnsafeWebhookEventType(activityWebhookEventType)) {
        setError('Webhook activity requires an automation.* platform event type');
        return null;
      }
      if (activityWebhookAuthRef.trim() && isUnsafeArtifactReference(activityWebhookAuthRef)) {
        setError('Webhook auth reference must be opaque');
        return null;
      }
      let data: Record<string, unknown>;
      try {
        data = parseJsonRecord(activityWebhookPayloadJson);
      } catch {
        setError('Webhook payload must be valid JSON object metadata');
        return null;
      }
      return {
        inputConfig: {
          eventToDispatch: activityWebhookEventType.trim().toLowerCase(),
          ...(activityWebhookAuthRef.trim() ? { webhookAuthRef: activityWebhookAuthRef.trim() } : {}),
          ...(Object.keys(data).length > 0 ? { data } : {}),
        },
        outputConfig: {},
      };
    }
    if (activityType === 'NOTIFICATION') {
      if (!activityNotificationUserId.trim() || isUnsafeArtifactReference(activityNotificationUserId)) {
        setError('Notification activity requires an opaque user ID');
        return null;
      }
      if (!activityNotificationTitle.trim() || !activityNotificationMessage.trim()) {
        setError('Notification activity requires a title and message');
        return null;
      }
      if (activityNotificationLinkUrl.trim() && (!activityNotificationLinkUrl.trim().startsWith('/') || activityNotificationLinkUrl.trim().startsWith('//'))) {
        setError('Notification link must be an application path');
        return null;
      }
      return {
        inputConfig: {
          userId: activityNotificationUserId.trim(),
          title: activityNotificationTitle.trim(),
          message: activityNotificationMessage.trim(),
          severity: activityNotificationSeverity,
          terminalStatus: activityNotificationTerminalStatus,
          ...(activityNotificationLinkUrl.trim() ? { linkUrl: activityNotificationLinkUrl.trim() } : {}),
        },
        outputConfig: {},
      };
    }
    if (!activityScriptRef.trim()) {
      setError('Script activity requires a signed artifact reference');
      return null;
    }
    return {
      inputConfig: { scriptRef: activityScriptRef.trim() },
      outputConfig: {},
    };
  };

  const runActivityAction = async (activityId: string, mode: 'verify' | 'dryRun' | 'liveRun' | 'overrideLiveRun') => {
    if (!showActivityExecution) {
      return;
    }
    const overrideReason = (overrideReasons[activityId] ?? '').trim();
    if (mode === 'overrideLiveRun' && !overrideReason) {
      setActivityActionErrors((current) => ({
        ...current,
        [activityId]: 'Override reason is required before bypassing an active lock.',
      }));
      return;
    }
    setActionBusy(`${activityId}:${mode}`);
    setActivityActionErrors((current) => ({ ...current, [activityId]: undefined }));
    try {
      if (mode === 'verify') {
        const verification = await verifyAutomationActivity(activityId);
        setVerificationResults((current) => ({ ...current, [activityId]: verification }));
      } else {
        const liveRun = mode === 'liveRun' || mode === 'overrideLiveRun';
        const run = await runAutomationActivity(activityId, {
          dryRun: !liveRun,
          confirmLiveRun: liveRun ? true : undefined,
          triggerSource: 'MANUAL',
          operatorOverride: mode === 'overrideLiveRun' ? true : undefined,
          overrideReason: mode === 'overrideLiveRun' ? overrideReason : undefined,
        });
        setLastActivityRuns((current) => ({ ...current, [activityId]: run }));
      }
      await loadWorkflows();
      if (mode !== 'verify' && expandedRunHistory.includes(activityId)) {
        await loadActivityRuns(activityId);
      }
    } catch (e: unknown) {
      setActivityActionErrors((current) => ({
        ...current,
        [activityId]: getAutomationErrorMessage(e, `Failed to ${mode} activity`),
      }));
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
            className="grid gap-4 border-b border-border-default p-5 xl:grid-cols-[1fr_180px_minmax(280px,1.4fr)_auto]"
            data-mode-feature={AUTOMATION_WORKFLOW_MODE_FEATURES.activityAuthoring.id}
            data-mode-visibility={AUTOMATION_WORKFLOW_MODE_FEATURES.activityAuthoring.visibility}
          >
            <Input label="Activity Name" value={activityName} onChange={(event) => setActivityName(event.target.value)} />
            <div>
              <label className="mb-1 block text-sm font-medium text-content-primary">Type</label>
              <select
                aria-label="Activity Type"
                value={activityType}
                onChange={(event) => setActivityType(event.target.value as AutomationActivityType)}
                className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-sm text-content-primary"
              >
                {ACTIVITY_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {type}{LIVE_SUPPORTED_ACTIVITY_TYPES.has(type) ? '' : ' - draft only'}
                  </option>
                ))}
              </select>
            </div>
            <div className="grid gap-3">
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant={selectedActivityCapability.liveSupported ? 'success' : activityType === 'SCRIPT' ? 'danger' : 'warning'}>
                  {selectedActivityCapability.label}
                </Badge>
                <span className="text-xs leading-5 text-content-secondary">{selectedActivityCapability.description}</span>
              </div>
              {activityType === 'SQL_QUERY' && (
                <div className="grid gap-3 md:grid-cols-[1fr_220px]">
                  <Input label="SQL" value={activitySql} onChange={(event) => setActivitySql(event.target.value)} />
                  <Input label="Target Data Extension ID" value={activityTargetDataExtensionId} onChange={(event) => setActivityTargetDataExtensionId(event.target.value)} />
                </div>
              )}
              {activityType === 'IMPORT' && (
                <div className="grid gap-3 md:grid-cols-2">
                  <Input label="Scoped Artifact ID" value={activityImportSource} onChange={(event) => setActivityImportSource(event.target.value)} />
                  <div>
                    <label className="mb-1 block text-sm font-medium text-content-primary">Target Type</label>
                    <select
                      aria-label="Import Target Type"
                      value={activityImportTargetType}
                      onChange={(event) => setActivityImportTargetType(event.target.value as 'SUBSCRIBER' | 'DATA_EXTENSION')}
                      className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-sm text-content-primary"
                    >
                      <option value="SUBSCRIBER">SUBSCRIBER</option>
                      <option value="DATA_EXTENSION">DATA_EXTENSION</option>
                    </select>
                  </div>
                  {activityImportTargetType === 'DATA_EXTENSION' && (
                    <Input label="Target Data Extension ID" value={activityImportTargetId} onChange={(event) => setActivityImportTargetId(event.target.value)} />
                  )}
                  <Input label="Email Field Mapping" value={activityImportEmailField} onChange={(event) => setActivityImportEmailField(event.target.value)} />
                </div>
              )}
              {activityType === 'FILE_DROP' && (
                <Input label="Scoped Artifact ID" value={activityImportSource} onChange={(event) => setActivityImportSource(event.target.value)} />
              )}
              {activityType === 'EXTRACT' && (
                <div className="grid gap-3 md:grid-cols-2">
                  <Input label="Source Type" value={activityExtractSourceType} onChange={(event) => setActivityExtractSourceType(event.target.value)} />
                  <Input label="Source ID or Artifact ID" value={activityExtractSourceId} onChange={(event) => setActivityExtractSourceId(event.target.value)} />
                  <Input label="Output Artifact ID" value={activityExtractDestination} onChange={(event) => setActivityExtractDestination(event.target.value)} />
                </div>
              )}
              {activityType === 'WEBHOOK' && (
                <div className="grid gap-3 md:grid-cols-2">
                  <Input label="Platform Event Type" value={activityWebhookEventType} onChange={(event) => setActivityWebhookEventType(event.target.value)} />
                  <Input label="Webhook Auth Ref" value={activityWebhookAuthRef} onChange={(event) => setActivityWebhookAuthRef(event.target.value)} />
                  <div className="md:col-span-2">
                    <label className="mb-1 block text-sm font-medium text-content-primary">Payload JSON</label>
                    <textarea
                      aria-label="Webhook Payload JSON"
                      value={activityWebhookPayloadJson}
                      onChange={(event) => setActivityWebhookPayloadJson(event.target.value)}
                      rows={3}
                      className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-sm text-content-primary"
                    />
                  </div>
                </div>
              )}
              {activityType === 'NOTIFICATION' && (
                <div className="grid gap-3 md:grid-cols-2">
                  <Input label="Recipient User ID" value={activityNotificationUserId} onChange={(event) => setActivityNotificationUserId(event.target.value)} />
                  <Input label="Title" value={activityNotificationTitle} onChange={(event) => setActivityNotificationTitle(event.target.value)} />
                  <Input label="Message" value={activityNotificationMessage} onChange={(event) => setActivityNotificationMessage(event.target.value)} />
                  <div>
                    <label className="mb-1 block text-sm font-medium text-content-primary">Severity</label>
                    <select
                      aria-label="Notification Severity"
                      value={activityNotificationSeverity}
                      onChange={(event) => setActivityNotificationSeverity(event.target.value as 'INFO' | 'WARNING' | 'ERROR' | 'SUCCESS')}
                      className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-sm text-content-primary"
                    >
                      {['INFO', 'WARNING', 'ERROR', 'SUCCESS'].map((severity) => (
                        <option key={severity} value={severity}>{severity}</option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="mb-1 block text-sm font-medium text-content-primary">Terminal Status</label>
                    <select
                      aria-label="Notification Terminal Status"
                      value={activityNotificationTerminalStatus}
                      onChange={(event) => setActivityNotificationTerminalStatus(event.target.value as 'SUCCEEDED' | 'FAILED')}
                      className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-sm text-content-primary"
                    >
                      <option value="FAILED">FAILED</option>
                      <option value="SUCCEEDED">SUCCEEDED</option>
                    </select>
                  </div>
                  <Input label="Link URL" value={activityNotificationLinkUrl} onChange={(event) => setActivityNotificationLinkUrl(event.target.value)} />
                </div>
              )}
              {activityType === 'SCRIPT' && (
                <Input label="Signed Artifact Reference" value={activityScriptRef} onChange={(event) => setActivityScriptRef(event.target.value)} />
              )}
            </div>
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
            const capability = ACTIVITY_CAPABILITY[activity.activityType];
            const storedVerification = verificationResults[activity.id] ?? asVerification(activity.verification);
            const verificationErrors = storedVerification?.errors ?? [];
            const verificationWarnings = storedVerification?.warnings ?? [];
            const liveExecutionSupported = isRecord(storedVerification?.normalizedConfig)
              ? storedVerification?.normalizedConfig.liveExecutionSupported !== false && capability.liveSupported
              : capability.liveSupported;
            const dependencyIds = activity.dependencyActivityIds ?? [];
            const actionError = activityActionErrors[activity.id];
            const lastRun = lastActivityRuns[activity.id];
            const overrideReason = overrideReasons[activity.id] ?? '';
            const lockOverrideAvailable = lastRun?.status === 'LOCKED' || runs.some((run) => run.status === 'LOCKED');

            return (
              <div key={activity.id} className="p-4">
                <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-semibold text-content-primary">{activity.name}</p>
                      <Badge variant={liveExecutionSupported ? 'success' : activity.activityType === 'SCRIPT' ? 'danger' : 'warning'}>
                        {capability.label}
                      </Badge>
                    </div>
                    <p className="text-sm text-content-secondary">{activity.activityType}</p>
                    <p className="mt-1 max-w-3xl text-xs leading-5 text-content-muted">{capability.description}</p>
                    <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-content-muted">
                      <span>Failure policy: {formatFailurePolicy(activity.failurePolicy)}</span>
                      <span>Dependencies: {dependencyIds.length ? dependencyIds.length : 'none'}</span>
                    </div>
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
                          disabled={!liveExecutionSupported}
                          title={liveExecutionSupported ? undefined : `${activity.activityType} dry-run execution is not supported yet`}
                          data-mode-feature={AUTOMATION_WORKFLOW_MODE_FEATURES.activityExecution.id}
                          data-mode-visibility={AUTOMATION_WORKFLOW_MODE_FEATURES.activityExecution.visibility}
                        >
                          Dry Run
                        </Button>
                        <Button
                          size="sm"
                          variant="danger"
                          loading={actionBusy === `${activity.id}:liveRun`}
                          onClick={() => runActivityAction(activity.id, 'liveRun')}
                          disabled={!liveExecutionSupported}
                          title={liveExecutionSupported ? undefined : `${activity.activityType} live execution is not supported yet`}
                          data-mode-feature={AUTOMATION_WORKFLOW_MODE_FEATURES.activityExecution.id}
                          data-mode-visibility={AUTOMATION_WORKFLOW_MODE_FEATURES.activityExecution.visibility}
                        >
                          Run Live
                        </Button>
                      </>
                    )}
                  </div>
                </div>
                {showActivityExecution && liveExecutionSupported && (
                  <div className="mt-3 grid gap-2 rounded-lg border border-border-default bg-surface-secondary/60 p-3 md:grid-cols-[minmax(220px,1fr)_auto] md:items-end">
                    <Input
                      label="Override reason"
                      id={`override-reason-${activity.id}`}
                      value={overrideReason}
                      onChange={(event) => setOverrideReasons((current) => ({ ...current, [activity.id]: event.target.value }))}
                      placeholder="Required to bypass an active lock"
                      aria-label={`Override reason for ${activity.name}`}
                    />
                    <Button
                      size="sm"
                      variant="outline"
                      loading={actionBusy === `${activity.id}:overrideLiveRun`}
                      onClick={() => runActivityAction(activity.id, 'overrideLiveRun')}
                      disabled={!lockOverrideAvailable}
                      title={lockOverrideAvailable ? undefined : 'Run Live must detect an active lock before override.'}
                      data-mode-feature={AUTOMATION_WORKFLOW_MODE_FEATURES.activityExecution.id}
                      data-mode-visibility={AUTOMATION_WORKFLOW_MODE_FEATURES.activityExecution.visibility}
                    >
                      Override lock
                    </Button>
                  </div>
                )}
                {(actionError || storedVerification) && (
                  <div className="mt-3 grid gap-2 md:grid-cols-2">
                    {actionError && (
                      <div className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">
                        {actionError}
                      </div>
                    )}
                    {storedVerification && (
                      <div
                        className="rounded-lg border border-border-default bg-surface-secondary/70 px-3 py-2 text-sm"
                        aria-label={`Verification result for ${activity.name}`}
                      >
                        <div className="mb-1 flex flex-wrap items-center gap-2">
                          <Badge variant={storedVerification.valid ? 'success' : 'danger'}>
                            {storedVerification.valid ? 'Verified' : 'Verification failed'}
                          </Badge>
                          <span className="text-xs text-content-muted">
                            Live support: {String(storedVerification.normalizedConfig.liveExecutionSupported ?? liveExecutionSupported)}
                          </span>
                        </div>
                        {verificationErrors.length > 0 && (
                          <p className="text-xs leading-5 text-danger">{verificationErrors.join('; ')}</p>
                        )}
                        {verificationWarnings.length > 0 && (
                          <p className="text-xs leading-5 text-amber-700 dark:text-amber-300">{verificationWarnings.join('; ')}</p>
                        )}
                      </div>
                    )}
                  </div>
                )}
                {lastRun && (
                  <div
                    className="mt-3 rounded-lg border border-border-default bg-surface-secondary/70 px-3 py-2 text-sm"
                    aria-label={`Latest run result for ${activity.name}`}
                  >
                    <div className="mb-1 flex flex-wrap items-center gap-2">
                      <Badge variant={runStatusVariant(lastRun.status)}>{lastRun.status}</Badge>
                      <Badge variant={lastRun.dryRun ? 'info' : 'warning'}>{lastRun.dryRun ? 'Dry run' : 'Live'}</Badge>
                      {lastRun.operatorOverride && <Badge variant="warning">Override</Badge>}
                    </div>
                    {lastRun.status === 'LOCKED' ? (
                      <p className="text-xs leading-5 text-amber-700 dark:text-amber-300">
                        Lock owner: {lastRun.lockOwnerRunId || 'unknown'}; retry after {formatRetryAfter(lastRun.retryAfterSeconds)}; locked until {formatRunTimestamp(lastRun.lockedUntil)}.
                      </p>
                    ) : lastRun.operatorOverride ? (
                      <p className="text-xs leading-5 text-content-secondary">
                        Operator override recorded: {lastRun.overrideReason || 'reason retained by audit ledger'}.
                      </p>
                    ) : (
                      <p className="text-xs leading-5 text-content-secondary">
                        Rows: {lastRun.rowsRead ?? 0} read, {lastRun.rowsWritten ?? 0} written; trace: {lastRun.traceId || 'None'}.
                      </p>
                    )}
                    {lastRun.errorMessage && <p className="mt-1 text-xs leading-5 text-danger">{lastRun.errorMessage}</p>}
                  </div>
                )}
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
                              {run.operatorOverride && <Badge variant="warning">Override</Badge>}
                            </div>
                            <div className="grid gap-1 text-content-secondary sm:grid-cols-2">
                              <span>Trigger: {run.triggerSource || 'Unknown'}</span>
                              <span>Rows: {run.rowsRead ?? 0} read, {run.rowsWritten ?? 0} written</span>
                              <span>Error code: {run.errorCode || 'None'}</span>
                              <span>Trace: {run.traceId || 'None'}</span>
                              <span>Dependencies: {toDisplayValue(run.dependencyTrace?.dependencyCount)}</span>
                              <span>Started: {formatRunTimestamp(run.startedAt)}</span>
                              <span>Completed: {formatRunTimestamp(run.completedAt)}</span>
                            </div>
                            {run.status === 'LOCKED' ? (
                              <div className="rounded-md bg-amber-500/10 px-2 py-1 text-xs font-medium text-amber-700 dark:text-amber-300">
                                <p>Lock owner: {run.lockOwnerRunId || 'unknown'}</p>
                                <p>Retry after: {formatRetryAfter(run.retryAfterSeconds)}</p>
                                <p>Locked until: {formatRunTimestamp(run.lockedUntil)}</p>
                              </div>
                            ) : run.errorMessage ? (
                              <p className="rounded-md bg-danger/10 px-2 py-1 text-xs font-medium text-danger">{run.errorMessage}</p>
                            ) : run.operatorOverride ? (
                              <p className="rounded-md bg-amber-500/10 px-2 py-1 text-xs font-medium text-amber-700 dark:text-amber-300">
                                Override reason: {run.overrideReason || 'retained by audit ledger'}
                              </p>
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

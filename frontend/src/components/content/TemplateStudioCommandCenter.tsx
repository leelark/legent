'use client';

import { clsx } from 'clsx';
import {
  AlertTriangle,
  CheckCircle2,
  Clock3,
  FileCheck2,
  Gauge,
  ImageIcon,
  Layers,
  Rocket,
  Save,
  Send,
  ShieldCheck,
  Sparkles,
  XCircle,
} from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { MetricCard, Panel } from '@/components/ui/PageChrome';
import type { ContentBlock } from '@/components/content/TemplateBuilder';
import { TEMPLATE_STUDIO_MODE_FEATURES } from '@/lib/ui-mode-contract';
import type {
  Asset,
  BrandKit,
  ContentSnippet,
  DynamicContentRule,
  PersonalizationToken,
  Template,
  TemplateApproval,
  TemplateVersion,
  TestSendRecord,
  ValidationResponse,
} from '@/lib/template-studio-api';

type StepState = 'done' | 'active' | 'blocked' | 'pending';
type BadgeTone = 'default' | 'success' | 'warning' | 'danger' | 'info' | 'brand';

type TemplateStudioCommandCenterProps = {
  template: Template;
  name: string;
  subject: string;
  blocks: ContentBlock[];
  previewWarnings: string[];
  validation: ValidationResponse | null;
  versions: TemplateVersion[];
  approvals: TemplateApproval[];
  assets: Asset[];
  snippets: ContentSnippet[];
  tokens: PersonalizationToken[];
  dynamicRules: DynamicContentRule[];
  brandKits: BrandKit[];
  testSendRecords: TestSendRecord[];
  isBusy: boolean;
  showApprovalWorkflow: boolean;
  showPublishControls: boolean;
  showTestSends: boolean;
  onSaveDraft: () => void;
  onRunPreview: () => void;
  onSubmitApproval: () => void;
  onPublish: () => void;
};

function normalizeStatus(status?: string) {
  return (status || '').trim().toUpperCase();
}

function formatShortDate(value?: string) {
  if (!value) {
    return 'Not yet';
  }
  const timestamp = Date.parse(value);
  if (Number.isNaN(timestamp)) {
    return 'Unknown';
  }
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' }).format(new Date(timestamp));
}

function stepTone(state: StepState): BadgeTone {
  if (state === 'done') return 'success';
  if (state === 'blocked') return 'danger';
  if (state === 'active') return 'warning';
  return 'default';
}

function StepIcon({ state }: { state: StepState }) {
  if (state === 'done') return <CheckCircle2 size={16} aria-hidden="true" />;
  if (state === 'blocked') return <XCircle size={16} aria-hidden="true" />;
  if (state === 'active') return <Clock3 size={16} aria-hidden="true" />;
  return <Sparkles size={16} aria-hidden="true" />;
}

export function TemplateStudioCommandCenter({
  template,
  name,
  subject,
  blocks,
  previewWarnings,
  validation,
  versions,
  approvals,
  assets,
  snippets,
  tokens,
  dynamicRules,
  brandKits,
  testSendRecords,
  isBusy,
  showApprovalWorkflow,
  showPublishControls,
  showTestSends,
  onSaveDraft,
  onRunPreview,
  onSubmitApproval,
  onPublish,
}: TemplateStudioCommandCenterProps) {
  const hasIdentity = name.trim().length > 0 && subject.trim().length > 0;
  const hasCanvas = blocks.length > 0;
  const validationErrors = validation?.errors ?? [];
  const validationWarnings = [...previewWarnings, ...(validation?.warnings ?? []), ...(validation?.compatibilityWarnings ?? [])];
  const brokenLinks = validation?.brokenLinkCount ?? 0;
  const missingAlt = validation?.imagesMissingAlt ?? 0;
  const qaBlockers = validationErrors.length + brokenLinks + missingAlt;
  const qaHasRun = validation !== null;
  const pendingApprovals = approvals.filter((approval) => normalizeStatus(approval.status) === 'PENDING').length;
  const approvedApprovals = approvals.filter((approval) => normalizeStatus(approval.status) === 'APPROVED').length;
  const failedTests = testSendRecords.filter((record) => ['FAILED', 'ERROR'].includes(normalizeStatus(record.status))).length;
  const successfulTests = testSendRecords.filter((record) => ['QUEUED', 'SENT', 'DELIVERED'].includes(normalizeStatus(record.status))).length;
  const activeDynamicRules = dynamicRules.filter((rule) => rule.active !== false).length;
  const latestVersion = versions.reduce((latest, version) => Math.max(latest, version.versionNumber), 0);
  const defaultBrand = brandKits.find((brand) => brand.isDefault) ?? brandKits[0];
  const approvalSatisfied = template.approvalRequired ? approvedApprovals > 0 || normalizeStatus(template.status) === 'PUBLISHED' : true;

  const readinessChecks = [
    { label: 'Name and subject', ready: hasIdentity, weight: 15 },
    { label: 'Builder content', ready: hasCanvas, weight: 20 },
    { label: 'QA run completed', ready: qaHasRun, weight: 15 },
    { label: 'No QA blockers', ready: qaHasRun && qaBlockers === 0, weight: 25 },
    ...(showTestSends ? [{ label: 'Test send evidence', ready: successfulTests > 0 && failedTests === 0, weight: 15 }] : []),
    ...(showApprovalWorkflow ? [{ label: 'Approval state', ready: approvalSatisfied, weight: 10 }] : []),
  ];
  const totalWeight = readinessChecks.reduce((sum, check) => sum + check.weight, 0);
  const readinessScore = Math.round(
    readinessChecks.reduce((sum, check) => sum + (check.ready ? check.weight : 0), 0) / totalWeight * 100
  );
  const blockers = readinessChecks.filter((check) => !check.ready).map((check) => check.label);
  const scoreAccent = readinessScore >= 85 ? 'success' : readinessScore >= 60 ? 'warning' : 'danger';
  const scoreLabel = readinessScore >= 85 ? 'Ready' : readinessScore >= 60 ? 'Review' : 'Needs work';

  const workflowSteps: Array<{ label: string; detail: string; state: StepState }> = [
    {
      label: 'Draft',
      detail: hasIdentity ? `${blocks.length} block${blocks.length === 1 ? '' : 's'} on canvas` : 'Add name and subject',
      state: hasIdentity && hasCanvas ? 'done' : 'active',
    },
    {
      label: 'QA',
      detail: qaHasRun
        ? `${validation?.linkCount ?? 0} links, ${qaBlockers} blocker${qaBlockers === 1 ? '' : 's'}`
        : 'Run render and validation',
      state: !qaHasRun ? 'active' : qaBlockers > 0 ? 'blocked' : 'done',
    },
  ];
  if (showTestSends) {
    workflowSteps.push({
      label: 'Tests',
      detail: testSendRecords.length > 0
        ? `${successfulTests} queued/sent, ${failedTests} failed`
        : 'Queue inbox checks before launch',
      state: failedTests > 0 ? 'blocked' : successfulTests > 0 ? 'done' : 'pending',
    });
  }
  if (showApprovalWorkflow) {
    workflowSteps.push({
      label: 'Approval',
      detail: template.approvalRequired
        ? pendingApprovals > 0
          ? `${pendingApprovals} pending`
          : `${approvedApprovals} approved`
        : 'Not required',
      state: approvalSatisfied ? 'done' : pendingApprovals > 0 ? 'active' : 'pending',
    });
  }
  if (showPublishControls) {
    workflowSteps.push({
      label: 'Publish',
      detail: normalizeStatus(template.status) === 'PUBLISHED'
        ? `Published ${formatShortDate(template.lastPublishedAt)}`
        : 'Draft not published',
      state: normalizeStatus(template.status) === 'PUBLISHED' ? 'done' : readinessScore >= 85 ? 'active' : 'pending',
    });
  }

  return (
    <Panel className="space-y-4" data-testid="template-command-center">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-content-secondary">Command center</p>
            <Badge variant={scoreAccent}>{scoreLabel}</Badge>
            <Badge variant={normalizeStatus(template.status) === 'PUBLISHED' ? 'success' : 'default'}>{template.status}</Badge>
          </div>
          <h2 className="mt-2 text-xl font-semibold text-content-primary">Operational readiness</h2>
          <p className="mt-1 max-w-3xl text-sm leading-6 text-content-secondary">
            {blockers.length > 0
              ? `${blockers.length} launch blocker${blockers.length === 1 ? '' : 's'}: ${blockers.slice(0, 3).join(', ')}${blockers.length > 3 ? '...' : ''}.`
              : 'Draft, QA, tests, and approval signals are aligned for publish.'}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" icon={<Save size={16} />} onClick={onSaveDraft} loading={isBusy}>Save</Button>
          <Button variant="secondary" icon={<FileCheck2 size={16} />} onClick={onRunPreview} loading={isBusy}>Run QA</Button>
          {showApprovalWorkflow && (
            <Button
              data-mode-feature={TEMPLATE_STUDIO_MODE_FEATURES.approvalWorkflow.id}
              data-mode-visibility={TEMPLATE_STUDIO_MODE_FEATURES.approvalWorkflow.visibility}
              variant="secondary"
              icon={<Send size={16} />}
              onClick={onSubmitApproval}
              loading={isBusy}
            >
              Approval
            </Button>
          )}
          {showPublishControls && (
            <Button
              data-mode-feature={TEMPLATE_STUDIO_MODE_FEATURES.publishControls.id}
              data-mode-visibility={TEMPLATE_STUDIO_MODE_FEATURES.publishControls.visibility}
              icon={<Rocket size={16} />}
              onClick={onPublish}
              loading={isBusy}
            >
              Publish
            </Button>
          )}
        </div>
      </div>

      <div className="h-2 overflow-hidden rounded-full bg-surface-secondary">
        <div
          className={clsx(
            'h-full rounded-full transition-all',
            readinessScore >= 85 ? 'bg-success' : readinessScore >= 60 ? 'bg-warning' : 'bg-danger'
          )}
          style={{ width: `${readinessScore}%` }}
          aria-hidden="true"
        />
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          label="Readiness"
          value={`${readinessScore}%`}
          helper={blockers.length > 0 ? `${blockers.length} blocker${blockers.length === 1 ? '' : 's'}` : 'Clear to proceed'}
          icon={<Gauge size={18} />}
          accent={scoreAccent}
        />
        <MetricCard
          label="Content Graph"
          value={blocks.length}
          helper={`${snippets.length} snippets, ${activeDynamicRules} dynamic rules`}
          icon={<Layers size={18} />}
          accent="brand"
        />
        <MetricCard
          label="Quality"
          value={qaBlockers}
          helper={`${validationWarnings.length} warning${validationWarnings.length === 1 ? '' : 's'}, ${validationErrors.length} error${validationErrors.length === 1 ? '' : 's'}`}
          icon={qaBlockers > 0 ? <AlertTriangle size={18} /> : <ShieldCheck size={18} />}
          accent={qaBlockers > 0 ? 'danger' : 'success'}
        />
        <MetricCard
          label="Assets & Tokens"
          value={assets.length + tokens.length}
          helper={`${assets.length} assets, ${tokens.length} tokens, ${brandKits.length} brand kits`}
          icon={<ImageIcon size={18} />}
          accent={assets.length + tokens.length > 0 ? 'success' : 'warning'}
        />
      </div>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_320px]">
        <div className="grid gap-3 md:grid-cols-5">
          {workflowSteps.map((step) => (
            <div key={step.label} className="rounded-lg border border-border-default bg-surface-primary/70 p-3">
              <div className="flex items-center justify-between gap-2">
                <span className={clsx(
                  'inline-flex h-8 w-8 items-center justify-center rounded-full',
                  step.state === 'done' && 'bg-success/10 text-success',
                  step.state === 'blocked' && 'bg-danger/10 text-danger',
                  step.state === 'active' && 'bg-warning/10 text-warning',
                  step.state === 'pending' && 'bg-surface-secondary text-content-muted'
                )}>
                  <StepIcon state={step.state} />
                </span>
                <Badge variant={stepTone(step.state)}>{step.state}</Badge>
              </div>
              <p className="mt-3 text-sm font-semibold text-content-primary">{step.label}</p>
              <p className="mt-1 line-clamp-2 text-xs text-content-secondary">{step.detail}</p>
            </div>
          ))}
        </div>

        <div className="rounded-lg border border-border-default bg-surface-primary/70 p-4">
          <p className="text-sm font-semibold text-content-primary">Launch context</p>
          <dl className="mt-3 space-y-2 text-sm">
            <div className="flex items-center justify-between gap-3">
              <dt className="text-content-secondary">Latest version</dt>
              <dd className="font-medium text-content-primary">{latestVersion > 0 ? `v${latestVersion}` : 'No version'}</dd>
            </div>
            <div className="flex items-center justify-between gap-3">
              <dt className="text-content-secondary">Default brand</dt>
              <dd className="max-w-[12rem] truncate font-medium text-content-primary">{defaultBrand?.name ?? 'Not selected'}</dd>
            </div>
            <div className="flex items-center justify-between gap-3">
              <dt className="text-content-secondary">Last published</dt>
              <dd className="font-medium text-content-primary">{formatShortDate(template.lastPublishedAt)}</dd>
            </div>
          </dl>
        </div>
      </div>
    </Panel>
  );
}

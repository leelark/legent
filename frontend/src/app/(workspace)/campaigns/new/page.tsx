'use client';

import { useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { Card, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { PageHeader } from '@/components/ui/PageChrome';
import { Skeleton } from '@/components/ui/Skeleton';
import { useToast } from '@/components/ui/Toast';
import { ArrowRight, ArrowLeft, CheckCircle, CircleNotch, PaperPlaneTilt } from '@phosphor-icons/react';
import { get } from '@/lib/api-client';
import {
  createCampaignExperiment,
  createCampaign,
  createRequestKey,
  getCampaign,
  submitCampaignApproval,
  triggerCampaignSend,
  updateCampaignBudget,
  updateFrequencyPolicy,
} from '@/lib/campaign-studio-api';

const STEPS = ['Basics', 'Targeting', 'Delivery', 'Review'];

type AudienceItem = {
  id: string;
  name: string;
  type: 'LIST' | 'SEGMENT';
};

type TemplateItem = {
  id: string;
  name: string;
  subject?: string;
};

export default function CampaignWizardPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const cloneId = searchParams.get('clone');
  const { addToast } = useToast();

  const [step, setStep] = useState(0);
  const [loading, setLoading] = useState(false);
  const [bootLoading, setBootLoading] = useState(true);
  const [templates, setTemplates] = useState<TemplateItem[]>([]);
  const [audiences, setAudiences] = useState<AudienceItem[]>([]);
  const [selectedAudienceId, setSelectedAudienceId] = useState('');

  const [form, setForm] = useState({
    name: '',
    subject: '',
    preheader: '',
    senderName: '',
    senderEmail: '',
    replyToEmail: '',
    templateId: '',
    providerId: '',
    sendingDomain: '',
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
    frequencyCap: 0,
    approvalRequired: true,
    trackingEnabled: true,
    complianceEnabled: true,
    scheduleType: 'NOW' as 'NOW' | 'LATER',
    scheduleTime: '',
    budgetEnforced: false,
    budgetLimit: 0,
    costPerSend: 0,
    frequencyWindowHours: 24,
    includeJourneyFrequency: true,
    experimentEnabled: false,
    experimentType: 'AB' as 'AB' | 'MULTIVARIATE',
    experimentMetric: 'CLICKS' as 'OPENS' | 'CLICKS' | 'CONVERSIONS' | 'REVENUE' | 'CUSTOM',
    experimentAutoPromote: false,
    experimentHoldout: 0,
    experimentMinRecipients: 100,
    experimentWindowHours: 24,
    variantAName: 'Control',
    variantBName: 'Variant B',
    variantAWeight: 50,
    variantBWeight: 50,
    variantASubject: '',
    variantBSubject: '',
    audiences: [] as { audienceType: 'LIST' | 'SEGMENT'; audienceId: string; action: 'INCLUDE' | 'EXCLUDE' }[],
  });

  useEffect(() => {
    const load = async () => {
      setBootLoading(true);
      try {
        const [templateRes, listsRes, segmentsRes] = await Promise.all([
          get<any>('/templates?page=0&size=100'),
          get<any>('/lists?page=0&size=100'),
          get<any>('/segments?page=0&size=100'),
        ]);

        const templateItems = (templateRes?.content ?? templateRes?.data ?? templateRes ?? []).map((item: any) => ({
          id: item.id,
          name: item.name,
          subject: item.subject,
        }));
        setTemplates(templateItems);

        const listItems = (listsRes?.content ?? listsRes?.data ?? listsRes ?? []).map((item: any) => ({
          id: item.id,
          name: item.name || item.id,
          type: 'LIST' as const,
        }));
        const segmentItems = (segmentsRes?.content ?? segmentsRes?.data ?? segmentsRes ?? []).map((item: any) => ({
          id: item.id,
          name: item.name || item.id,
          type: 'SEGMENT' as const,
        }));
        setAudiences([...listItems, ...segmentItems]);

        if (cloneId) {
          const campaign = await getCampaign(cloneId);
          setForm((current) => ({
            ...current,
            name: `${campaign.name} Copy`,
            subject: campaign.subject || '',
            preheader: campaign.preheader || '',
            senderName: campaign.senderName || '',
            senderEmail: campaign.senderEmail || '',
            replyToEmail: campaign.replyToEmail || '',
            templateId: campaign.templateId || (campaign as any).contentId || '',
            providerId: campaign.providerId || '',
            sendingDomain: campaign.sendingDomain || '',
            timezone: campaign.timezone || current.timezone,
            frequencyCap: campaign.frequencyCap || 0,
            approvalRequired: campaign.approvalRequired ?? true,
            trackingEnabled: campaign.trackingEnabled ?? true,
            complianceEnabled: campaign.complianceEnabled ?? true,
            audiences: (campaign.audiences || []).map((audience) => ({
              audienceType: audience.audienceType,
              audienceId: audience.audienceId,
              action: audience.action,
            })),
          }));
        }
      } catch (error: any) {
        addToast({
          type: 'error',
          title: 'Failed to initialize wizard',
          message: error?.response?.data?.error?.message || 'Unable to load templates and audiences.',
        });
      } finally {
        setBootLoading(false);
      }
    };

    void load();
  }, [addToast, cloneId]);

  const selectedAudienceNames = useMemo(() => {
    const map = new Map(audiences.map((item) => [item.id, item]));
    return form.audiences.map((audience) => map.get(audience.audienceId)?.name || audience.audienceId);
  }, [audiences, form.audiences]);

  const toggleAudience = (audience: AudienceItem, action: 'INCLUDE' | 'EXCLUDE') => {
    const exists = form.audiences.find((item) => item.audienceId === audience.id && item.action === action);
    if (exists) {
      setForm((current) => ({
        ...current,
        audiences: current.audiences.filter((item) => !(item.audienceId === audience.id && item.action === action)),
      }));
      return;
    }
    setForm((current) => ({
      ...current,
      audiences: [...current.audiences, { audienceType: audience.type, audienceId: audience.id, action }],
    }));
  };

  const handleCreate = async (mode: 'DRAFT' | 'SEND' | 'APPROVAL') => {
    setLoading(true);
    try {
      const created = await createCampaign({
        name: form.name,
        subject: form.subject,
        preheader: form.preheader,
        senderName: form.senderName || undefined,
        senderEmail: form.senderEmail || undefined,
        replyToEmail: form.replyToEmail || undefined,
        providerId: form.providerId || undefined,
        sendingDomain: form.sendingDomain || undefined,
        timezone: form.timezone || 'UTC',
        frequencyCap: form.frequencyCap || 0,
        approvalRequired: form.approvalRequired,
        trackingEnabled: form.trackingEnabled,
        complianceEnabled: form.complianceEnabled,
        templateId: form.templateId || undefined,
        audiences: form.audiences,
      } as any);

      await Promise.all([
        updateCampaignBudget(created.id, {
          currency: 'USD',
          enforced: form.budgetEnforced,
          budgetLimit: Number(form.budgetLimit || 0),
          costPerSend: Number(form.costPerSend || 0),
        }),
        updateFrequencyPolicy(created.id, {
          enabled: Number(form.frequencyCap || 0) > 0,
          maxSends: Number(form.frequencyCap || 0),
          windowHours: Number(form.frequencyWindowHours || 24),
          includeJourneys: form.includeJourneyFrequency,
        }),
      ]);

      if (form.experimentEnabled) {
        await createCampaignExperiment(created.id, {
          name: `${form.name} Experiment`,
          experimentType: form.experimentType,
          winnerMetric: form.experimentMetric,
          autoPromotion: form.experimentAutoPromote,
          minRecipientsPerVariant: Number(form.experimentMinRecipients || 100),
          evaluationWindowHours: Number(form.experimentWindowHours || 24),
          holdoutPercentage: Number(form.experimentHoldout || 0),
          status: 'ACTIVE',
          factors: form.experimentType === 'MULTIVARIATE'
            ? JSON.stringify([{ key: 'subject', values: ['A', 'B'] }])
            : '[]',
          variants: [
            {
              variantKey: 'A',
              name: form.variantAName || 'Control',
              weight: Number(form.variantAWeight || 0),
              controlVariant: true,
              active: true,
              subjectOverride: form.variantASubject || undefined,
              contentId: form.templateId || undefined,
            },
            {
              variantKey: 'B',
              name: form.variantBName || 'Variant B',
              weight: Number(form.variantBWeight || 0),
              active: true,
              subjectOverride: form.variantBSubject || undefined,
              contentId: form.templateId || undefined,
            },
          ],
        });
      }

      if (mode === 'APPROVAL') {
        await submitCampaignApproval(created.id, 'Approval requested from campaign wizard');
        addToast({ type: 'success', title: 'Submitted for approval', message: `${created.name} moved to review.` });
      }

      if (mode === 'SEND') {
        const scheduledAt =
          form.scheduleType === 'LATER' && form.scheduleTime
            ? new Date(form.scheduleTime).toISOString()
            : undefined;
        await triggerCampaignSend(created.id, {
          scheduledAt,
          triggerSource: 'MANUAL',
          triggerReference: 'campaign-wizard',
          idempotencyKey: createRequestKey(`wizard-${created.id}`),
        });
        addToast({ type: 'success', title: 'Campaign launched', message: `${created.name} send job queued.` });
      }

      if (mode === 'DRAFT') {
        addToast({ type: 'success', title: 'Draft saved', message: `${created.name} saved as draft.` });
      }
      router.push('/app/campaigns');
    } catch (error: any) {
      addToast({
        type: 'error',
        title: 'Campaign action failed',
        message: error?.response?.data?.error?.message || 'Unable to complete campaign action.',
      });
    } finally {
      setLoading(false);
    }
  };

  if (bootLoading) {
    return (
      <div className="mx-auto max-w-5xl space-y-6">
        <PageHeader
          eyebrow="Campaign setup"
          title="Campaign Wizard"
          description="Loading templates, audiences, and launch controls."
        />
        <Card>
          <div className="space-y-4">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-64 w-full" />
          </div>
        </Card>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <PageHeader
        eyebrow="Campaign setup"
        title="Campaign Wizard"
        description="Plan campaign setup, targeting, approvals, and delivery in one controlled flow."
        action={(
          <Link href="/app/campaigns">
            <Button variant="secondary">Back to Campaigns</Button>
          </Link>
        )}
      />

      <div className="flex flex-wrap items-center justify-center gap-3">
        {STEPS.map((label, index) => (
          <div key={label} className="flex items-center gap-2">
            <div className={`flex h-8 w-8 items-center justify-center rounded-full text-xs font-semibold ${
              index < step
                ? 'bg-brand-500 text-white'
                : index === step
                  ? 'bg-brand-100 text-brand-700 border border-brand-400'
                  : 'bg-surface-secondary text-content-muted'
            }`}>
              {index < step ? <CheckCircle size={14} weight="fill" /> : index + 1}
            </div>
            <span className={`text-sm ${index === step ? 'text-content-primary font-medium' : 'text-content-muted'}`}>
              {label}
            </span>
          </div>
        ))}
      </div>

      <Card>
        {step === 0 && (
          <div className="space-y-6">
            <CardHeader title="Campaign Basics" subtitle="Define message identity and template." />
            <div className="grid gap-4 p-6 pt-0 md:grid-cols-2">
              <Input
                label="Campaign Name *"
                value={form.name}
                onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
                placeholder="Product Launch - APAC"
              />
              <Input
                label="Subject Line *"
                value={form.subject}
                onChange={(event) => setForm((current) => ({ ...current, subject: event.target.value }))}
                placeholder="Introducing our latest release"
              />
              <Input
                label="Preview Text"
                value={form.preheader}
                onChange={(event) => setForm((current) => ({ ...current, preheader: event.target.value }))}
                placeholder="See what is new this week"
              />
              <div>
                <label className="mb-1 block text-sm font-medium text-content-primary">Template</label>
                <select
                  aria-label="Template"
                  value={form.templateId}
                  onChange={(event) => {
                    const template = templates.find((item) => item.id === event.target.value);
                    setForm((current) => ({
                      ...current,
                      templateId: event.target.value,
                      subject: current.subject || template?.subject || '',
                    }));
                  }}
                  className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-sm text-content-primary"
                >
                  <option value="">Select template</option>
                  {templates.map((template) => (
                    <option key={template.id} value={template.id}>
                      {template.name}
                    </option>
                  ))}
                </select>
              </div>
              <Input
                label="Sender Name"
                value={form.senderName}
                onChange={(event) => setForm((current) => ({ ...current, senderName: event.target.value }))}
                placeholder="Legent Team"
              />
              <Input
                label="Sender Email"
                value={form.senderEmail}
                onChange={(event) => setForm((current) => ({ ...current, senderEmail: event.target.value }))}
                placeholder="updates@company.com"
              />
              <Input
                label="Reply-to Email"
                value={form.replyToEmail}
                onChange={(event) => setForm((current) => ({ ...current, replyToEmail: event.target.value }))}
                placeholder="support@company.com"
              />
              <Input
                label="Provider ID"
                value={form.providerId}
                onChange={(event) => setForm((current) => ({ ...current, providerId: event.target.value }))}
                placeholder="primary-smtp-provider"
              />
              <Input
                label="Sending Domain"
                value={form.sendingDomain}
                onChange={(event) => setForm((current) => ({ ...current, sendingDomain: event.target.value }))}
                placeholder="mail.company.com"
              />
              <Input
                label="Timezone"
                value={form.timezone}
                onChange={(event) => setForm((current) => ({ ...current, timezone: event.target.value }))}
                placeholder="UTC"
              />
            </div>
          </div>
        )}

        {step === 1 && (
          <div className="space-y-6">
            <CardHeader title="Audience Targeting" subtitle="Select include and exclusion audiences." />
            <div className="grid gap-4 p-6 pt-0 md:grid-cols-[1fr_auto_auto]">
              <div>
                <label className="mb-1 block text-sm font-medium text-content-primary">Audience</label>
                <select
                  aria-label="Audience"
                  value={selectedAudienceId}
                  onChange={(event) => setSelectedAudienceId(event.target.value)}
                  className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-sm text-content-primary"
                >
                  <option value="">Select list or segment</option>
                  {audiences.map((audience) => (
                    <option key={audience.id} value={audience.id}>
                      {audience.name} ({audience.type})
                    </option>
                  ))}
                </select>
              </div>
              <Button
                className="mt-6"
                variant="secondary"
                onClick={() => {
                  const audience = audiences.find((item) => item.id === selectedAudienceId);
                  if (audience) {
                    toggleAudience(audience, 'INCLUDE');
                  }
                }}
              >
                Include
              </Button>
              <Button
                className="mt-6"
                variant="secondary"
                onClick={() => {
                  const audience = audiences.find((item) => item.id === selectedAudienceId);
                  if (audience) {
                    toggleAudience(audience, 'EXCLUDE');
                  }
                }}
              >
                Exclude
              </Button>
            </div>
            <div className="px-6 pb-6">
              {form.audiences.length === 0 ? (
                <p className="text-sm text-content-muted">No audiences selected yet.</p>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {form.audiences.map((audience, index) => (
                    <Badge key={`${audience.audienceId}-${audience.action}-${index}`} variant={audience.action === 'INCLUDE' ? 'success' : 'danger'}>
                      {audience.action}: {selectedAudienceNames[index]}
                    </Badge>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

        {step === 2 && (
          <div className="space-y-6">
            <CardHeader title="Delivery Rules" subtitle="Configure compliance, cadence, and scheduling." />
            <div className="grid gap-4 p-6 pt-0 md:grid-cols-2">
              <Input
                type="number"
                label="Frequency Cap"
                value={String(form.frequencyCap)}
                onChange={(event) => setForm((current) => ({ ...current, frequencyCap: Number(event.target.value || '0') }))}
              />
              <div className="space-y-2">
                <label className="block text-sm font-medium text-content-primary">Send Timing</label>
                <div className="flex items-center gap-3">
                  <label className="flex items-center gap-2 text-sm text-content-primary">
                    <input
                      type="radio"
                      checked={form.scheduleType === 'NOW'}
                      onChange={() => setForm((current) => ({ ...current, scheduleType: 'NOW' }))}
                    />
                    Send now
                  </label>
                  <label className="flex items-center gap-2 text-sm text-content-primary">
                    <input
                      type="radio"
                      checked={form.scheduleType === 'LATER'}
                      onChange={() => setForm((current) => ({ ...current, scheduleType: 'LATER' }))}
                    />
                    Schedule
                  </label>
                </div>
                {form.scheduleType === 'LATER' && (
                  <input
                    type="datetime-local"
                    value={form.scheduleTime}
                    onChange={(event) => setForm((current) => ({ ...current, scheduleTime: event.target.value }))}
                    className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-sm text-content-primary"
                  />
                )}
              </div>
              <label className="flex items-center gap-2 text-sm text-content-primary">
                <input
                  type="checkbox"
                  checked={form.approvalRequired}
                  onChange={(event) => setForm((current) => ({ ...current, approvalRequired: event.target.checked }))}
                />
                Approval required
              </label>
              <label className="flex items-center gap-2 text-sm text-content-primary">
                <input
                  type="checkbox"
                  checked={form.trackingEnabled}
                  onChange={(event) => setForm((current) => ({ ...current, trackingEnabled: event.target.checked }))}
                />
                Tracking enabled
              </label>
              <label className="flex items-center gap-2 text-sm text-content-primary">
                <input
                  type="checkbox"
                  checked={form.complianceEnabled}
                  onChange={(event) => setForm((current) => ({ ...current, complianceEnabled: event.target.checked }))}
                />
                Compliance checks enabled
              </label>
              <div className="grid gap-4 rounded-lg border border-border-default bg-surface-secondary/50 p-4 md:col-span-2 md:grid-cols-3">
                <div className="md:col-span-3">
                  <p className="text-sm font-semibold text-content-primary">Budget Guard</p>
                  <p className="text-xs text-content-secondary">Reserve estimated spend before each recipient send.</p>
                </div>
                <Input
                  type="number"
                  min="0"
                  step="0.000001"
                  label="Budget Limit"
                  value={String(form.budgetLimit)}
                  onChange={(event) => setForm((current) => ({ ...current, budgetLimit: Number(event.target.value || '0') }))}
                />
                <Input
                  type="number"
                  min="0"
                  step="0.000001"
                  label="Cost Per Send"
                  value={String(form.costPerSend)}
                  onChange={(event) => setForm((current) => ({ ...current, costPerSend: Number(event.target.value || '0') }))}
                />
                <label className="mt-7 flex items-center gap-2 text-sm text-content-primary">
                  <input
                    type="checkbox"
                    checked={form.budgetEnforced}
                    onChange={(event) => setForm((current) => ({ ...current, budgetEnforced: event.target.checked }))}
                  />
                  Enforce budget
                </label>
              </div>
              <div className="grid gap-4 rounded-lg border border-border-default bg-surface-secondary/50 p-4 md:col-span-2 md:grid-cols-3">
                <div className="md:col-span-3">
                  <p className="text-sm font-semibold text-content-primary">Workspace Frequency Policy</p>
                  <p className="text-xs text-content-secondary">Count reserved and sent messages across campaigns and journeys.</p>
                </div>
                <Input
                  type="number"
                  min="1"
                  label="Window Hours"
                  value={String(form.frequencyWindowHours)}
                  onChange={(event) => setForm((current) => ({ ...current, frequencyWindowHours: Number(event.target.value || '24') }))}
                />
                <label className="mt-7 flex items-center gap-2 text-sm text-content-primary md:col-span-2">
                  <input
                    type="checkbox"
                    checked={form.includeJourneyFrequency}
                    onChange={(event) => setForm((current) => ({ ...current, includeJourneyFrequency: event.target.checked }))}
                  />
                  Include journey-triggered sends
                </label>
              </div>
              <div className="space-y-4 rounded-lg border border-border-default bg-surface-secondary/50 p-4 md:col-span-2">
                <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                  <div>
                    <p className="text-sm font-semibold text-content-primary">Experiment Engine</p>
                    <p className="text-xs text-content-secondary">Deterministic A/B or multivariate assignment with holdout and winner rules.</p>
                  </div>
                  <label className="flex items-center gap-2 text-sm text-content-primary">
                    <input
                      type="checkbox"
                      checked={form.experimentEnabled}
                      onChange={(event) => setForm((current) => ({ ...current, experimentEnabled: event.target.checked }))}
                    />
                    Enable
                  </label>
                </div>
                {form.experimentEnabled && (
                  <div className="grid gap-4 md:grid-cols-4">
                    <div>
                      <label className="mb-1 block text-sm font-medium text-content-primary">Type</label>
                      <select
                        aria-label="Experiment Type"
                        value={form.experimentType}
                        onChange={(event) => setForm((current) => ({ ...current, experimentType: event.target.value as 'AB' | 'MULTIVARIATE' }))}
                        className="w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary"
                      >
                        <option value="AB">A/B</option>
                        <option value="MULTIVARIATE">Multivariate</option>
                      </select>
                    </div>
                    <div>
                      <label className="mb-1 block text-sm font-medium text-content-primary">Winner Metric</label>
                      <select
                        aria-label="Winner Metric"
                        value={form.experimentMetric}
                        onChange={(event) => setForm((current) => ({ ...current, experimentMetric: event.target.value as typeof form.experimentMetric }))}
                        className="w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary"
                      >
                        <option value="OPENS">Opens</option>
                        <option value="CLICKS">Clicks</option>
                        <option value="CONVERSIONS">Conversions</option>
                        <option value="REVENUE">Revenue</option>
                        <option value="CUSTOM">Custom</option>
                      </select>
                    </div>
                    <Input
                      type="number"
                      min="0"
                      max="95"
                      label="Holdout %"
                      value={String(form.experimentHoldout)}
                      onChange={(event) => setForm((current) => ({ ...current, experimentHoldout: Number(event.target.value || '0') }))}
                    />
                    <Input
                      type="number"
                      min="1"
                      label="Min Recipients"
                      value={String(form.experimentMinRecipients)}
                      onChange={(event) => setForm((current) => ({ ...current, experimentMinRecipients: Number(event.target.value || '100') }))}
                    />
                    <Input
                      type="number"
                      min="1"
                      label="Window Hours"
                      value={String(form.experimentWindowHours)}
                      onChange={(event) => setForm((current) => ({ ...current, experimentWindowHours: Number(event.target.value || '24') }))}
                    />
                    <Input
                      label="Variant A"
                      value={form.variantAName}
                      onChange={(event) => setForm((current) => ({ ...current, variantAName: event.target.value }))}
                    />
                    <Input
                      type="number"
                      min="0"
                      max="100"
                      label="A Weight"
                      value={String(form.variantAWeight)}
                      onChange={(event) => setForm((current) => ({ ...current, variantAWeight: Number(event.target.value || '0') }))}
                    />
                    <Input
                      label="A Subject Override"
                      value={form.variantASubject}
                      onChange={(event) => setForm((current) => ({ ...current, variantASubject: event.target.value }))}
                    />
                    <Input
                      label="Variant B"
                      value={form.variantBName}
                      onChange={(event) => setForm((current) => ({ ...current, variantBName: event.target.value }))}
                    />
                    <Input
                      type="number"
                      min="0"
                      max="100"
                      label="B Weight"
                      value={String(form.variantBWeight)}
                      onChange={(event) => setForm((current) => ({ ...current, variantBWeight: Number(event.target.value || '0') }))}
                    />
                    <Input
                      label="B Subject Override"
                      value={form.variantBSubject}
                      onChange={(event) => setForm((current) => ({ ...current, variantBSubject: event.target.value }))}
                    />
                    <label className="mt-7 flex items-center gap-2 text-sm text-content-primary">
                      <input
                        type="checkbox"
                        checked={form.experimentAutoPromote}
                        onChange={(event) => setForm((current) => ({ ...current, experimentAutoPromote: event.target.checked }))}
                      />
                      Auto-promote winner
                    </label>
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {step === 3 && (
          <div className="space-y-6">
            <CardHeader title="Review and Launch" subtitle="Validate setup before creating campaign." />
            <div className="space-y-3 px-6 pb-6 text-sm">
              <div className="flex items-center justify-between">
                <span className="text-content-secondary">Campaign</span>
                <span className="font-medium text-content-primary">{form.name || 'Unnamed campaign'}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-content-secondary">Subject</span>
                <span className="font-medium text-content-primary">{form.subject || 'No subject'}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-content-secondary">Template</span>
                <span className="font-medium text-content-primary">
                  {templates.find((template) => template.id === form.templateId)?.name || 'Not selected'}
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-content-secondary">Audience Rules</span>
                <span className="font-medium text-content-primary">{form.audiences.length}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-content-secondary">Delivery</span>
                <span className="font-medium text-content-primary">
                  {form.scheduleType === 'LATER' && form.scheduleTime ? `Scheduled ${new Date(form.scheduleTime).toLocaleString()}` : 'Send immediately'}
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-content-secondary">Approval Policy</span>
                <span className="font-medium text-content-primary">{form.approvalRequired ? 'Required' : 'Optional'}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-content-secondary">Budget Guard</span>
                <span className="font-medium text-content-primary">
                  {form.budgetEnforced ? `Enforced ${form.budgetLimit || 0} USD` : 'Not enforced'}
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-content-secondary">Frequency Cap</span>
                <span className="font-medium text-content-primary">
                  {form.frequencyCap > 0 ? `${form.frequencyCap} per ${form.frequencyWindowHours}h` : 'Off'}
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-content-secondary">Experiment</span>
                <span className="font-medium text-content-primary">
                  {form.experimentEnabled ? `${form.experimentType} by ${form.experimentMetric}` : 'Off'}
                </span>
              </div>
            </div>
          </div>
        )}
      </Card>

      <div className="flex items-center justify-between">
        <Button
          variant="secondary"
          icon={<ArrowLeft size={16} />}
          onClick={() => setStep((current) => Math.max(0, current - 1))}
          disabled={step === 0 || loading}
        >
          Back
        </Button>
        {step < STEPS.length - 1 ? (
          <Button
            icon={<ArrowRight size={16} />}
            onClick={() => setStep((current) => Math.min(STEPS.length - 1, current + 1))}
            disabled={!form.name || !form.subject || (step === 1 && form.audiences.length === 0)}
          >
            Next
          </Button>
        ) : (
          <div className="flex flex-wrap items-center gap-2">
            <Button
              variant="secondary"
              onClick={() => void handleCreate('DRAFT')}
              disabled={loading}
              icon={loading ? <CircleNotch size={16} className="animate-spin" /> : undefined}
            >
              Save Draft
            </Button>
            <Button
              variant="secondary"
              onClick={() => void handleCreate('APPROVAL')}
              disabled={loading}
            >
              Submit Approval
            </Button>
            <Button
              onClick={() => void handleCreate('SEND')}
              disabled={loading || form.approvalRequired || (form.scheduleType === 'LATER' && !form.scheduleTime)}
              icon={loading ? <CircleNotch size={16} className="animate-spin" /> : <PaperPlaneTilt size={16} />}
            >
              Launch Campaign
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}

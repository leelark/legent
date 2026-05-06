'use client';

import { useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { Card, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { useToast } from '@/components/ui/Toast';
import { ArrowRight, ArrowLeft, CheckCircle, CircleNotch, PaperPlaneTilt } from '@phosphor-icons/react';
import { get } from '@/lib/api-client';
import {
  createCampaign,
  getCampaign,
  submitCampaignApproval,
  triggerCampaignSend,
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
            templateId: (campaign as any).contentId || '',
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
          idempotencyKey: `wizard-${created.id}-${Date.now()}`,
        });
        addToast({ type: 'success', title: 'Campaign launched', message: `${created.name} send job queued.` });
      }

      if (mode === 'DRAFT') {
        addToast({ type: 'success', title: 'Draft saved', message: `${created.name} saved as draft.` });
      }
      router.push('/campaigns');
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
    return <div className="p-8 text-sm text-content-secondary">Loading campaign wizard...</div>;
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-content-primary">Campaign Wizard</h1>
          <p className="mt-1 text-sm text-content-secondary">Plan campaign setup, targeting, approvals, and delivery in one flow.</p>
        </div>
        <Link href="/campaigns">
          <Button variant="secondary">Back to Campaigns</Button>
        </Link>
      </div>

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
              disabled={loading || (form.scheduleType === 'LATER' && !form.scheduleTime)}
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

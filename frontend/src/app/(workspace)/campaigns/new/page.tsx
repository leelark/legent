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
import { listDomains, type DeliverabilityDomain } from '@/lib/deliverability-api';
import { listProviderHealth, listProviders, type Provider } from '@/lib/providers-api';
import {
  listSendGovernancePolicies,
  sendGovernancePolicyItems,
  type SendGovernancePolicy,
} from '@/lib/send-governance-policy-api';
import { CAMPAIGN_WORKFLOW_MODE_FEATURES, isModeFeatureVisible } from '@/lib/ui-mode-contract';
import {
  type Campaign,
  createCampaignExperiment,
  createCampaign,
  createRequestKey,
  getCampaign,
  submitCampaignApproval,
  triggerCampaignSend,
  updateCampaignBudget,
  updateFrequencyPolicy,
} from '@/lib/campaign-studio-api';
import { useUIStore } from '@/stores/uiStore';

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

type PagedApiResponse<T> = {
  content?: T[];
  data?: T[];
};

type TemplateApiItem = {
  id: string;
  name?: string;
  subject?: string;
};

type AudienceApiItem = {
  id: string;
  name?: string;
};

type ProviderHealthItem = {
  id: string;
  healthStatus?: string;
  isActive?: boolean;
  active?: boolean;
};

type SenderDomainOption = {
  id: string;
  domainName: string;
  status: string;
  active: boolean;
  spfVerified?: boolean;
  dkimVerified?: boolean;
  dmarcVerified?: boolean;
};

type SendGovernancePolicyApiItem = SendGovernancePolicy & {
  policy_key?: string;
  publication_policy?: string;
  unsubscribe_policy?: string;
  send_log_retention_days?: number;
  provider_id?: string;
  sending_domain?: string;
  created_at?: string;
  updated_at?: string;
  is_active?: boolean;
};

type ReadinessTone = 'ready' | 'warning' | 'blocked';

type ReadinessCheck = {
  label: string;
  tone: ReadinessTone;
  message: string;
};

type CloneCampaign = Campaign & {
  contentId?: string;
};

type ApiErrorLike = {
  normalized?: { message?: unknown };
  response?: { data?: { error?: { message?: unknown } } };
  message?: unknown;
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function asArray<T>(value: unknown): T[] {
  if (Array.isArray(value)) {
    return value as T[];
  }
  if (!isRecord(value)) {
    return [];
  }
  if (Array.isArray(value.content)) {
    return value.content as T[];
  }
  if (Array.isArray(value.data)) {
    return value.data as T[];
  }
  return [];
}

function getErrorMessage(error: unknown, fallback: string) {
  if (!isRecord(error)) {
    return fallback;
  }
  const candidate = error as ApiErrorLike;
  const message =
    candidate.normalized?.message ??
    candidate.response?.data?.error?.message ??
    candidate.message;
  return typeof message === 'string' && message.trim() ? message : fallback;
}

function asText(value: unknown, fallback = '') {
  return typeof value === 'string' && value.trim() ? value.trim() : fallback;
}

function asBoolean(value: unknown, fallback = true) {
  if (typeof value === 'boolean') {
    return value;
  }
  if (typeof value === 'string') {
    if (value.toLowerCase() === 'true') return true;
    if (value.toLowerCase() === 'false') return false;
  }
  return fallback;
}

function asNumber(value: unknown, fallback = 0) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  }
  return fallback;
}

function normalizeProvider(provider: Provider): Provider {
  const record = provider as Provider & { active?: boolean };
  return {
    ...provider,
    isActive: asBoolean(record.isActive ?? record.active, true),
    healthStatus: asText(record.healthStatus, 'UNKNOWN'),
  };
}

function normalizeDomain(domain: DeliverabilityDomain): SenderDomainOption | null {
  const id = asText(domain.id);
  const domainName = asText(domain.domainName ?? domain.domain_name ?? domain.domain);
  if (!domainName) {
    return null;
  }
  return {
    id: id || domainName,
    domainName,
    status: asText(domain.status, 'UNKNOWN').toUpperCase(),
    active: asBoolean(domain.isActive ?? domain.active, true),
    spfVerified: typeof domain.spfVerified === 'boolean' ? domain.spfVerified : undefined,
    dkimVerified: typeof domain.dkimVerified === 'boolean' ? domain.dkimVerified : undefined,
    dmarcVerified: typeof domain.dmarcVerified === 'boolean' ? domain.dmarcVerified : undefined,
  };
}

function normalizeSendGovernancePolicy(policy: SendGovernancePolicy): SendGovernancePolicy {
  const record = policy as SendGovernancePolicyApiItem;
  const retentionDays = asNumber(policy.sendLogRetentionDays ?? record.send_log_retention_days);
  const version = asNumber(policy.version);
  return {
    ...policy,
    policyKey: asText(policy.policyKey ?? record.policy_key, policy.id),
    name: asText(policy.name, policy.policyKey ?? record.policy_key ?? policy.id),
    publicationPolicy: asText(policy.publicationPolicy ?? record.publication_policy, 'APPROVED_CONTENT_REQUIRED'),
    unsubscribePolicy: asText(policy.unsubscribePolicy ?? record.unsubscribe_policy, 'REQUIRED'),
    sendLogRetentionDays: retentionDays || undefined,
    providerId: asText(policy.providerId ?? record.provider_id, ''),
    sendingDomain: asText(policy.sendingDomain ?? record.sending_domain, ''),
    active: asBoolean(policy.active ?? record.is_active, true),
    trackingAllowed: asBoolean(policy.trackingAllowed, true),
    suppressionRequired: asBoolean(policy.suppressionRequired, true),
    consentRequired: asBoolean(policy.consentRequired, false),
    version: version || undefined,
    createdAt: asText(policy.createdAt ?? record.created_at, ''),
    updatedAt: asText(policy.updatedAt ?? record.updated_at, ''),
  };
}

function healthForProvider(provider: Provider | undefined, providerHealth: ProviderHealthItem[]) {
  if (!provider) {
    return undefined;
  }
  return providerHealth.find((item) => item.id === provider.id);
}

function evaluateProviderReadiness(providerId: string, provider: Provider | undefined, health: ProviderHealthItem | undefined, catalogDegraded: boolean): ReadinessCheck {
  if (!providerId) {
    return { label: 'Provider missing', tone: 'blocked', message: 'Choose an active provider before approval or launch.' };
  }
  if (!provider) {
    return {
      label: 'Provider unknown',
      tone: catalogDegraded ? 'warning' : 'blocked',
      message: catalogDegraded ? 'Provider catalog could not be refreshed; launch readiness must verify it.' : 'Selected provider is not in the current workspace catalog.',
    };
  }
  if (!provider.isActive || health?.isActive === false || health?.active === false) {
    return { label: 'Provider blocked', tone: 'blocked', message: 'Selected provider is inactive.' };
  }
  const status = String(health?.healthStatus ?? provider.healthStatus ?? 'UNKNOWN').toUpperCase();
  if (status === 'HEALTHY') {
    return { label: 'Provider ready', tone: 'ready', message: `${provider.name || provider.id} is active and healthy.` };
  }
  if (status === 'DEGRADED' || status === 'UNKNOWN') {
    return { label: 'Provider warning', tone: 'warning', message: `${provider.name || provider.id} status is ${status.toLowerCase()}; launch preflight must pass.` };
  }
  return { label: 'Provider blocked', tone: 'blocked', message: `${provider.name || provider.id} health is ${status.toLowerCase()}.` };
}

function evaluateDomainReadiness(domainName: string, domain: SenderDomainOption | undefined, catalogDegraded: boolean): ReadinessCheck {
  if (!domainName) {
    return { label: 'Domain missing', tone: 'blocked', message: 'Choose a verified sending domain before approval or launch.' };
  }
  if (!domain) {
    return {
      label: 'Domain unknown',
      tone: catalogDegraded ? 'warning' : 'blocked',
      message: catalogDegraded ? 'Domain catalog could not be refreshed; launch readiness must verify it.' : 'Selected domain is not in the current deliverability catalog.',
    };
  }
  if (!domain.active) {
    return { label: 'Domain blocked', tone: 'blocked', message: `${domain.domainName} is inactive.` };
  }
  if (domain.status !== 'VERIFIED') {
    return { label: 'Domain blocked', tone: 'blocked', message: `${domain.domainName} is ${domain.status.toLowerCase()}, not verified.` };
  }
  const dnsChecks = [domain.spfVerified, domain.dkimVerified, domain.dmarcVerified].filter((value) => value !== undefined);
  if (dnsChecks.some((value) => value === false)) {
    return { label: 'Domain warning', tone: 'warning', message: `${domain.domainName} is verified, but at least one DNS authentication check is incomplete.` };
  }
  return { label: 'Domain ready', tone: 'ready', message: `${domain.domainName} is verified for this workspace.` };
}

function senderAlignmentReadiness(senderEmail: string, sendingDomain: string): ReadinessCheck {
  const senderDomain = senderEmail.includes('@') ? senderEmail.split('@').pop()?.toLowerCase() : '';
  if (!senderEmail || !sendingDomain || !senderDomain) {
    return { label: 'Sender alignment pending', tone: 'warning', message: 'Add a sender email using the selected sending domain before approval.' };
  }
  if (senderDomain === sendingDomain.toLowerCase()) {
    return { label: 'Sender aligned', tone: 'ready', message: `Sender email aligns with ${sendingDomain}.` };
  }
  return { label: 'Sender warning', tone: 'warning', message: `Sender email domain ${senderDomain} does not match ${sendingDomain}.` };
}

function policyValueLabel(value: unknown, fallback = 'Not set') {
  const text = asText(value, fallback);
  return text.replaceAll('_', ' ');
}

function policyDisplayName(policy: SendGovernancePolicy | undefined) {
  if (!policy) {
    return '';
  }
  const label = asText(policy.name, policy.policyKey || policy.id);
  return policy.version ? `${label} v${policy.version}` : label;
}

function evaluatePolicyReadiness(
  policyId: string,
  policy: SendGovernancePolicy | undefined,
  catalogDegraded: boolean,
  trackingEnabled: boolean,
  complianceEnabled: boolean,
  approvalRequired: boolean
): ReadinessCheck {
  if (!policyId) {
    return { label: 'Policy missing', tone: 'blocked', message: 'Choose an active send-governance policy before approval or launch.' };
  }
  if (!policy) {
    return {
      label: 'Policy unknown',
      tone: catalogDegraded ? 'warning' : 'blocked',
      message: catalogDegraded ? 'Policy catalog could not be refreshed; launch readiness must verify it.' : 'Selected policy is not in the current workspace catalog.',
    };
  }
  if (policy.active === false) {
    return { label: 'Policy blocked', tone: 'blocked', message: `${policyDisplayName(policy)} is inactive.` };
  }
  if (policy.suppressionRequired && !complianceEnabled) {
    return { label: 'Policy blocked', tone: 'blocked', message: 'Selected policy requires suppression checks, but campaign compliance checks are disabled.' };
  }
  if (policy.trackingAllowed === false && trackingEnabled) {
    return { label: 'Policy blocked', tone: 'blocked', message: 'Selected policy disallows tracking, but campaign tracking is enabled.' };
  }
  if (asText(policy.publicationPolicy).includes('APPROVED') && !approvalRequired) {
    return { label: 'Policy warning', tone: 'warning', message: `${policyDisplayName(policy)} expects approved content; approval is optional on this campaign.` };
  }
  return {
    label: 'Policy ready',
    tone: 'ready',
    message: `${policyDisplayName(policy)} is active with ${policyValueLabel(policy.publicationPolicy, 'policy')} publication rules.`,
  };
}

function readinessVariant(tone: ReadinessTone): 'success' | 'warning' | 'danger' {
  if (tone === 'ready') return 'success';
  if (tone === 'warning') return 'warning';
  return 'danger';
}

export default function CampaignWizardPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const cloneId = searchParams.get('clone');
  const { addToast } = useToast();
  const uiMode = useUIStore((state) => state.uiMode);

  const [step, setStep] = useState(0);
  const [loading, setLoading] = useState(false);
  const [bootLoading, setBootLoading] = useState(true);
  const [templates, setTemplates] = useState<TemplateItem[]>([]);
  const [audiences, setAudiences] = useState<AudienceItem[]>([]);
  const [selectedAudienceId, setSelectedAudienceId] = useState('');
  const [providers, setProviders] = useState<Provider[]>([]);
  const [providerHealth, setProviderHealth] = useState<ProviderHealthItem[]>([]);
  const [domains, setDomains] = useState<SenderDomainOption[]>([]);
  const [policies, setPolicies] = useState<SendGovernancePolicy[]>([]);
  const [readinessDegraded, setReadinessDegraded] = useState(false);
  const [policyCatalogDegraded, setPolicyCatalogDegraded] = useState(false);

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
    sendGovernancePolicyId: '',
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
  const showBudgetGuard = isModeFeatureVisible(CAMPAIGN_WORKFLOW_MODE_FEATURES.budgetGuard, uiMode);
  const showFrequencyPolicy = isModeFeatureVisible(CAMPAIGN_WORKFLOW_MODE_FEATURES.frequencyPolicy, uiMode);
  const showExperimentEngine = isModeFeatureVisible(CAMPAIGN_WORKFLOW_MODE_FEATURES.experimentEngine, uiMode);
  const experimentEnabled = showExperimentEngine && form.experimentEnabled;

  useEffect(() => {
    const load = async () => {
      setBootLoading(true);
      try {
        const [templateRes, listsRes, segmentsRes] = await Promise.all([
          get<PagedApiResponse<TemplateApiItem> | TemplateApiItem[]>('/templates?page=0&size=100'),
          get<PagedApiResponse<AudienceApiItem> | AudienceApiItem[]>('/lists?page=0&size=100'),
          get<PagedApiResponse<AudienceApiItem> | AudienceApiItem[]>('/segments?page=0&size=100'),
        ]);
        const [providerRes, healthRes, domainRes, policyRes] = await Promise.allSettled([
          listProviders(false),
          listProviderHealth(),
          listDomains(),
          listSendGovernancePolicies(0, 100),
        ]);

        const templateItems = asArray<TemplateApiItem>(templateRes).map((item) => ({
          id: item.id,
          name: item.name ?? '',
          subject: item.subject,
        }));
        setTemplates(templateItems);

        const listItems = asArray<AudienceApiItem>(listsRes).map((item) => ({
          id: item.id,
          name: item.name || item.id,
          type: 'LIST' as const,
        }));
        const segmentItems = asArray<AudienceApiItem>(segmentsRes).map((item) => ({
          id: item.id,
          name: item.name || item.id,
          type: 'SEGMENT' as const,
        }));
        setAudiences([...listItems, ...segmentItems]);
        const providerItems = providerRes.status === 'fulfilled' ? asArray<Provider>(providerRes.value).map(normalizeProvider) : [];
        const healthItems = healthRes.status === 'fulfilled' ? asArray<ProviderHealthItem>(healthRes.value) : [];
        const domainItems = domainRes.status === 'fulfilled'
          ? asArray<DeliverabilityDomain>(domainRes.value).flatMap((item) => {
            const domain = normalizeDomain(item);
            return domain ? [domain] : [];
          })
          : [];
        const policyItems = policyRes.status === 'fulfilled'
          ? sendGovernancePolicyItems(policyRes.value).map(normalizeSendGovernancePolicy)
          : [];
        setProviders(providerItems);
        setProviderHealth(healthItems);
        setDomains(domainItems);
        setPolicies(policyItems);
        setReadinessDegraded([providerRes, healthRes, domainRes].some((result) => result.status === 'rejected'));
        setPolicyCatalogDegraded(policyRes.status === 'rejected');

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
            templateId: campaign.templateId || (campaign as CloneCampaign).contentId || '',
            providerId: campaign.providerId || '',
            sendingDomain: campaign.sendingDomain || '',
            sendGovernancePolicyId: campaign.sendGovernancePolicyId || '',
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
      } catch (error) {
        addToast({
          type: 'error',
          title: 'Failed to initialize wizard',
          message: getErrorMessage(error, 'Unable to load templates and audiences.'),
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
  const selectedProvider = useMemo(
    () => providers.find((provider) => provider.id === form.providerId),
    [providers, form.providerId]
  );
  const selectedProviderHealth = useMemo(
    () => healthForProvider(selectedProvider, providerHealth),
    [selectedProvider, providerHealth]
  );
  const selectedDomain = useMemo(
    () => domains.find((domain) => domain.domainName === form.sendingDomain),
    [domains, form.sendingDomain]
  );
  const selectedPolicy = useMemo(
    () => policies.find((policy) => policy.id === form.sendGovernancePolicyId),
    [form.sendGovernancePolicyId, policies]
  );
  const providerReadiness = useMemo(
    () => evaluateProviderReadiness(form.providerId, selectedProvider, selectedProviderHealth, readinessDegraded),
    [form.providerId, readinessDegraded, selectedProvider, selectedProviderHealth]
  );
  const domainReadiness = useMemo(
    () => evaluateDomainReadiness(form.sendingDomain, selectedDomain, readinessDegraded),
    [form.sendingDomain, readinessDegraded, selectedDomain]
  );
  const senderReadiness = useMemo(
    () => senderAlignmentReadiness(form.senderEmail, form.sendingDomain),
    [form.senderEmail, form.sendingDomain]
  );
  const policyReadiness = useMemo(
    () => evaluatePolicyReadiness(
      form.sendGovernancePolicyId,
      selectedPolicy,
      policyCatalogDegraded,
      form.trackingEnabled,
      form.complianceEnabled,
      form.approvalRequired
    ),
    [
      form.approvalRequired,
      form.complianceEnabled,
      form.sendGovernancePolicyId,
      form.trackingEnabled,
      policyCatalogDegraded,
      selectedPolicy,
    ]
  );
  const deliveryReadinessBlocked = [providerReadiness, domainReadiness, policyReadiness].some((check) => check.tone === 'blocked');

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
      const payload: Partial<Campaign> = {
        name: form.name,
        subject: form.subject,
        preheader: form.preheader,
        senderName: form.senderName || undefined,
        senderEmail: form.senderEmail || undefined,
        replyToEmail: form.replyToEmail || undefined,
        providerId: form.providerId || undefined,
        sendingDomain: form.sendingDomain || undefined,
        sendGovernancePolicyId: form.sendGovernancePolicyId || undefined,
        timezone: form.timezone || 'UTC',
        frequencyCap: form.frequencyCap || 0,
        approvalRequired: form.approvalRequired,
        trackingEnabled: form.trackingEnabled,
        complianceEnabled: form.complianceEnabled,
        templateId: form.templateId || undefined,
        audiences: form.audiences,
      };
      const created = await createCampaign(payload);

      const postCreateTasks: Promise<unknown>[] = [];

      if (showBudgetGuard) {
        postCreateTasks.push(updateCampaignBudget(created.id, {
          currency: 'USD',
          enforced: form.budgetEnforced,
          budgetLimit: Number(form.budgetLimit || 0),
          costPerSend: Number(form.costPerSend || 0),
        }));
      }

      if (showFrequencyPolicy) {
        postCreateTasks.push(updateFrequencyPolicy(created.id, {
          enabled: Number(form.frequencyCap || 0) > 0,
          maxSends: Number(form.frequencyCap || 0),
          windowHours: Number(form.frequencyWindowHours || 24),
          includeJourneys: form.includeJourneyFrequency,
        }));
      }

      await Promise.all(postCreateTasks);

      if (experimentEnabled) {
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
    } catch (error) {
      addToast({
        type: 'error',
        title: 'Campaign action failed',
        message: getErrorMessage(error, 'Unable to complete campaign action.'),
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
              <div>
                <label className="mb-1 block text-sm font-medium text-content-primary">Delivery Provider</label>
                <select
                  aria-label="Delivery Provider"
                  value={form.providerId}
                  onChange={(event) => setForm((current) => ({ ...current, providerId: event.target.value }))}
                  className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-sm text-content-primary"
                >
                  <option value="">Select active provider</option>
                  {providers.map((provider) => (
                    <option key={provider.id} value={provider.id}>
                      {provider.name || provider.id} ({provider.type}{provider.healthStatus ? `, ${provider.healthStatus}` : ''})
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-content-primary">Sending Domain</label>
                <select
                  aria-label="Sending Domain"
                  value={form.sendingDomain}
                  onChange={(event) => setForm((current) => ({ ...current, sendingDomain: event.target.value }))}
                  className="w-full rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-sm text-content-primary"
                >
                  <option value="">Select verified domain</option>
                  {domains.map((domain) => (
                    <option key={domain.id} value={domain.domainName}>
                      {domain.domainName} ({domain.status})
                    </option>
                  ))}
                </select>
              </div>
              <Input
                label="Timezone"
                value={form.timezone}
                onChange={(event) => setForm((current) => ({ ...current, timezone: event.target.value }))}
                placeholder="UTC"
              />
            </div>
            <div className="px-6 pb-6">
              <div data-testid="campaign-delivery-readiness" className="grid gap-3 md:grid-cols-3">
                {[providerReadiness, domainReadiness, senderReadiness].map((check) => (
                  <div key={check.label} className="rounded-lg border border-border-default bg-surface-secondary/60 p-3">
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-sm font-semibold text-content-primary">{check.label}</p>
                      <Badge variant={readinessVariant(check.tone)}>
                        {check.tone === 'ready' ? 'READY' : check.tone === 'warning' ? 'CHECK' : 'BLOCKED'}
                      </Badge>
                    </div>
                    <p className="mt-2 text-xs leading-5 text-content-secondary">{check.message}</p>
                  </div>
                ))}
              </div>
              {readinessDegraded && (
                <p className="mt-3 text-xs text-warning">Provider or domain readiness data could not be fully refreshed. Launch preflight remains authoritative.</p>
              )}
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
              <div
                data-testid="campaign-governance-policy"
                className="space-y-3 rounded-lg border border-border-default bg-surface-secondary/50 p-4 md:col-span-2"
              >
                <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                  <div className="min-w-0 flex-1">
                    <label className="mb-1 block text-sm font-medium text-content-primary">Send Governance Policy</label>
                    <select
                      aria-label="Send Governance Policy"
                      value={form.sendGovernancePolicyId}
                      onChange={(event) => setForm((current) => ({ ...current, sendGovernancePolicyId: event.target.value }))}
                      className="w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary"
                    >
                      <option value="">Select active policy</option>
                      {policies.map((policy) => (
                        <option key={policy.id} value={policy.id} disabled={policy.active === false}>
                          {policyDisplayName(policy)} ({policyValueLabel(policy.publicationPolicy)})
                        </option>
                      ))}
                    </select>
                  </div>
                  <Badge variant={readinessVariant(policyReadiness.tone)}>
                    {policyReadiness.tone === 'ready' ? 'READY' : policyReadiness.tone === 'warning' ? 'CHECK' : 'BLOCKED'}
                  </Badge>
                </div>
                <div className="grid gap-2 text-xs md:grid-cols-4">
                  <div className="rounded-lg border border-border-default bg-surface-primary p-3">
                    <p className="font-semibold uppercase tracking-[0.12em] text-content-muted">Version</p>
                    <p className="mt-1 font-medium text-content-primary">{selectedPolicy?.version ? `v${selectedPolicy.version}` : 'Not selected'}</p>
                  </div>
                  <div className="rounded-lg border border-border-default bg-surface-primary p-3">
                    <p className="font-semibold uppercase tracking-[0.12em] text-content-muted">Approval</p>
                    <p className="mt-1 font-medium text-content-primary">{policyValueLabel(selectedPolicy?.publicationPolicy)}</p>
                  </div>
                  <div className="rounded-lg border border-border-default bg-surface-primary p-3">
                    <p className="font-semibold uppercase tracking-[0.12em] text-content-muted">Retention</p>
                    <p className="mt-1 font-medium text-content-primary">{selectedPolicy?.sendLogRetentionDays ? `${selectedPolicy.sendLogRetentionDays} days` : 'Not selected'}</p>
                  </div>
                  <div className="rounded-lg border border-border-default bg-surface-primary p-3">
                    <p className="font-semibold uppercase tracking-[0.12em] text-content-muted">Updated</p>
                    <p className="mt-1 font-medium text-content-primary">
                      {selectedPolicy?.updatedAt ? new Date(selectedPolicy.updatedAt).toLocaleDateString() : 'Not selected'}
                    </p>
                  </div>
                </div>
                <p className="text-xs leading-5 text-content-secondary">{policyReadiness.message}</p>
                {policyCatalogDegraded && (
                  <p className="text-xs text-warning">Policy catalog could not be fully refreshed. Launch preflight remains authoritative.</p>
                )}
              </div>
              {showBudgetGuard && (
                <div
                  className="grid gap-4 rounded-lg border border-border-default bg-surface-secondary/50 p-4 md:col-span-2 md:grid-cols-3"
                  data-mode-feature={CAMPAIGN_WORKFLOW_MODE_FEATURES.budgetGuard.id}
                  data-mode-visibility={CAMPAIGN_WORKFLOW_MODE_FEATURES.budgetGuard.visibility}
                >
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
              )}
              {showFrequencyPolicy && (
                <div
                  className="grid gap-4 rounded-lg border border-border-default bg-surface-secondary/50 p-4 md:col-span-2 md:grid-cols-3"
                  data-mode-feature={CAMPAIGN_WORKFLOW_MODE_FEATURES.frequencyPolicy.id}
                  data-mode-visibility={CAMPAIGN_WORKFLOW_MODE_FEATURES.frequencyPolicy.visibility}
                >
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
              )}
              {showExperimentEngine && (
                <div
                  className="space-y-4 rounded-lg border border-border-default bg-surface-secondary/50 p-4 md:col-span-2"
                  data-mode-feature={CAMPAIGN_WORKFLOW_MODE_FEATURES.experimentEngine.id}
                  data-mode-visibility={CAMPAIGN_WORKFLOW_MODE_FEATURES.experimentEngine.visibility}
                >
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
              )}
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
                <span className="text-content-secondary">Provider</span>
                <span className="font-medium text-content-primary">
                  {selectedProvider?.name || form.providerId || 'Not selected'}
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-content-secondary">Sending Domain</span>
                <span className="font-medium text-content-primary">{form.sendingDomain || 'Not selected'}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-content-secondary">Governance Policy</span>
                <span className="font-medium text-content-primary">
                  {policyDisplayName(selectedPolicy) || 'Not selected'}
                </span>
              </div>
              <div className="grid gap-2 rounded-lg border border-border-default bg-surface-secondary/50 p-3 md:grid-cols-4">
                {[providerReadiness, domainReadiness, senderReadiness, policyReadiness].map((check) => (
                  <div key={check.label} className="flex items-center justify-between gap-2">
                    <span className="text-content-secondary">{check.label}</span>
                    <Badge variant={readinessVariant(check.tone)}>
                      {check.tone === 'ready' ? 'READY' : check.tone === 'warning' ? 'CHECK' : 'BLOCKED'}
                    </Badge>
                  </div>
                ))}
              </div>
              <div className="flex items-center justify-between">
                <span className="text-content-secondary">Approval Policy</span>
                <span className="font-medium text-content-primary">{form.approvalRequired ? 'Required' : 'Optional'}</span>
              </div>
              {showBudgetGuard && (
                <div className="flex items-center justify-between">
                  <span className="text-content-secondary">Budget Guard</span>
                  <span className="font-medium text-content-primary">
                    {form.budgetEnforced ? `Enforced ${form.budgetLimit || 0} USD` : 'Not enforced'}
                  </span>
                </div>
              )}
              <div className="flex items-center justify-between">
                <span className="text-content-secondary">Frequency Cap</span>
                <span className="font-medium text-content-primary">
                  {form.frequencyCap > 0 ? `${form.frequencyCap} per ${form.frequencyWindowHours}h` : 'Off'}
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-content-secondary">Experiment</span>
                <span className="font-medium text-content-primary">
                  {experimentEnabled ? `${form.experimentType} by ${form.experimentMetric}` : 'Off'}
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
              disabled={loading || deliveryReadinessBlocked}
            >
              Submit Approval
            </Button>
            <Button
              onClick={() => void handleCreate('SEND')}
              disabled={loading || deliveryReadinessBlocked || form.approvalRequired || (form.scheduleType === 'LATER' && !form.scheduleTime)}
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

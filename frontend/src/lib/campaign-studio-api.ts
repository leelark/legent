import { get, post, put, del } from '@/lib/api-client';

export type CampaignStatus =
  | 'DRAFT'
  | 'REVIEW_PENDING'
  | 'APPROVED'
  | 'SCHEDULED'
  | 'SENDING'
  | 'PAUSED'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'
  | 'ARCHIVED';

export type Campaign = {
  id: string;
  tenantId?: string;
  workspaceId?: string;
  teamId?: string;
  ownershipScope?: string;
  name: string;
  subject?: string;
  preheader?: string;
  senderProfileId?: string;
  templateId?: string;
  senderName?: string;
  senderEmail?: string;
  replyToEmail?: string;
  brandId?: string;
  trackingEnabled?: boolean;
  complianceEnabled?: boolean;
  providerId?: string;
  sendingDomain?: string;
  timezone?: string;
  frequencyCap?: number;
  approvalRequired?: boolean;
  approvedBy?: string;
  approvedAt?: string;
  triggerSource?: string;
  triggerReference?: string;
  experimentConfig?: string;
  status: CampaignStatus;
  type?: string;
  scheduledAt?: string;
  createdAt?: string;
  updatedAt?: string;
  audiences?: Array<{
    id?: string;
    audienceType: 'LIST' | 'SEGMENT';
    audienceId: string;
    action: 'INCLUDE' | 'EXCLUDE';
  }>;
};

export type SendJob = {
  id: string;
  campaignId: string;
  status: 'PENDING' | 'RESOLVING' | 'BATCHING' | 'SENDING' | 'PAUSED' | 'RETRYING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  scheduledAt?: string;
  startedAt?: string;
  completedAt?: string;
  pausedAt?: string;
  cancelledAt?: string;
  totalTarget?: number;
  totalSent?: number;
  totalFailed?: number;
  totalBounced?: number;
  totalSuppressed?: number;
  errorMessage?: string;
  triggerSource?: string;
  triggerReference?: string;
  idempotencyKey?: string;
  createdAt?: string;
  updatedAt?: string;
};

export type CampaignApproval = {
  id: string;
  campaignId: string;
  requestedBy?: string;
  requestedAt?: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
  approvedBy?: string;
  approvedAt?: string;
  rejectionReason?: string;
  comments?: string;
  updatedAt?: string;
};

export type ExperimentType = 'AB' | 'MULTIVARIATE';
export type ExperimentStatus = 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'PROMOTED';
export type WinnerMetric = 'OPENS' | 'CLICKS' | 'CONVERSIONS' | 'REVENUE' | 'CUSTOM';

export type CampaignVariant = {
  id?: string;
  experimentId?: string;
  variantKey: string;
  name: string;
  weight: number;
  controlVariant?: boolean;
  holdoutVariant?: boolean;
  active?: boolean;
  winner?: boolean;
  contentId?: string;
  subjectOverride?: string;
  metadata?: string;
};

export type CampaignExperiment = {
  id: string;
  campaignId: string;
  name: string;
  experimentType: ExperimentType;
  status: ExperimentStatus;
  winnerMetric: WinnerMetric;
  customMetricName?: string;
  autoPromotion: boolean;
  minRecipientsPerVariant: number;
  evaluationWindowHours: number;
  holdoutPercentage: number;
  winnerVariantId?: string;
  factors?: string;
  startsAt?: string;
  endsAt?: string;
  completedAt?: string;
  variants: CampaignVariant[];
  createdAt?: string;
  updatedAt?: string;
};

export type CampaignExperimentRequest = {
  name: string;
  experimentType: ExperimentType;
  winnerMetric: WinnerMetric;
  customMetricName?: string;
  autoPromotion?: boolean;
  minRecipientsPerVariant?: number;
  evaluationWindowHours?: number;
  holdoutPercentage?: number;
  factors?: string;
  status?: ExperimentStatus;
  variants: CampaignVariant[];
};

export type CampaignBudget = {
  id?: string;
  campaignId: string;
  currency: string;
  budgetLimit: number;
  costPerSend: number;
  reservedSpend: number;
  actualSpend: number;
  enforced: boolean;
  status: string;
};

export type CampaignBudgetRequest = {
  currency?: string;
  budgetLimit?: number;
  costPerSend?: number;
  enforced?: boolean;
};

export type FrequencyPolicy = {
  id?: string;
  campaignId: string;
  enabled: boolean;
  maxSends: number;
  windowHours: number;
  includeJourneys: boolean;
};

export type FrequencyPolicyRequest = {
  enabled?: boolean;
  maxSends?: number;
  windowHours?: number;
  includeJourneys?: boolean;
};

export type SendPreflightReport = {
  campaignId: string;
  sendAllowed: boolean;
  errors: string[];
  warnings: string[];
  checks: Record<string, unknown>;
};

export type ResendPlan = {
  campaignId: string;
  resendMode: 'FAILED_ONLY' | 'NOT_SENT' | 'SUPPRESSED_RECHECK' | 'ALL_REQUIRES_CONFIRMATION';
  requiresConfirmation: boolean;
  idempotencyKey: string;
  eligibleRecipients: number;
  warnings: string[];
};

export type DeadLetterEntry = {
  id: string;
  campaignId: string;
  jobId: string;
  batchId?: string;
  subscriberId?: string;
  email?: string;
  reason?: string;
  payload?: string;
  retryCount?: number;
  status: string;
  nextRetryAt?: string;
  replayedAt?: string;
  createdAt?: string;
};

export type VariantMetrics = {
  campaignId: string;
  experimentId: string;
  variantId?: string;
  targetCount: number;
  holdoutCount: number;
  sentCount: number;
  failedCount: number;
  openCount: number;
  clickCount: number;
  conversionCount: number;
  revenue: number;
  customMetricCount: number;
  score: number;
};

export type CampaignListResponse = {
  content: Campaign[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
};

export type LaunchAction = 'AUTO' | 'PREVIEW' | 'SAFE_FIX' | 'SUBMIT_APPROVAL' | 'SCHEDULE' | 'LAUNCH';

export type LaunchStepResult = {
  key: string;
  label: string;
  status: 'PASS' | 'WARN' | 'BLOCKED' | 'EXECUTED' | 'SKIPPED' | 'FAILED';
  score: number;
  message?: string;
  details?: Record<string, unknown>;
};

export type LaunchRecommendation = {
  key: string;
  severity: 'BLOCKER' | 'WARN' | 'INFO' | string;
  title: string;
  detail: string;
  autoFixAvailable?: boolean;
};

export type LaunchPlanResponse = {
  planId?: string;
  campaignId: string;
  idempotencyKey: string;
  status: 'PREVIEWED' | 'READY' | 'BLOCKED' | 'EXECUTED' | 'FAILED' | string;
  readinessScore: number;
  blockerCount: number;
  warningCount: number;
  primaryAction: string;
  message?: string;
  auditId?: string;
  affectedResourceIds?: Record<string, unknown>;
  blockers: string[];
  warnings: string[];
  recommendations: LaunchRecommendation[];
  steps: LaunchStepResult[];
  createdAt?: string;
  updatedAt?: string;
};

export type LaunchPlanRequest = {
  campaignId: string;
  idempotencyKey: string;
  action?: LaunchAction;
  scheduledAt?: string;
  confirmLaunch?: boolean;
  metadata?: Record<string, unknown>;
};

export const listCampaigns = async (params?: { page?: number; size?: number; search?: string }) => {
  const qp = new URLSearchParams();
  qp.set('page', String(params?.page ?? 0));
  qp.set('size', String(params?.size ?? 20));
  if (params?.search?.trim()) {
    qp.set('search', params.search.trim());
  }
  return get<CampaignListResponse>(`/campaigns?${qp.toString()}`);
};

export const previewLaunchPlan = async (payload: LaunchPlanRequest) =>
  post<LaunchPlanResponse>('/campaigns/launch-plans/preview', payload);

export const executeLaunchPlan = async (payload: LaunchPlanRequest) =>
  post<LaunchPlanResponse>('/campaigns/launch-plans/execute', payload);

export const getCampaignLaunchReadiness = async (campaignId: string) =>
  get<LaunchPlanResponse>(`/campaigns/${campaignId}/launch-readiness`);

export const getCampaign = async (id: string) => get<Campaign>(`/campaigns/${id}`);

export const createCampaign = async (payload: Partial<Campaign>) => post<Campaign>('/campaigns', payload);

export const updateCampaign = async (id: string, payload: Partial<Campaign>) => put<Campaign>(`/campaigns/${id}`, payload);

export const deleteCampaign = async (id: string) => del<void>(`/campaigns/${id}`);

export const cloneCampaign = async (id: string) => post<Campaign>(`/campaigns/${id}/clone`);

export const pauseCampaign = async (id: string, reason?: string) => post<Campaign>(`/campaigns/${id}/pause`, { reason });

export const resumeCampaign = async (id: string, comments?: string) => post<Campaign>(`/campaigns/${id}/resume`, { comments });

export const cancelCampaign = async (id: string, reason?: string) => post<Campaign>(`/campaigns/${id}/cancel`, { reason });

export const archiveCampaign = async (id: string, reason?: string) => post<Campaign>(`/campaigns/${id}/archive`, { reason });

export const restoreCampaign = async (id: string, comments?: string) => post<Campaign>(`/campaigns/${id}/restore`, { comments });

export const scheduleCampaign = async (id: string, scheduledAt: string) =>
  post<Campaign>(`/campaigns/${id}/schedule`, { scheduledAt });

export const triggerCampaignSend = async (
  id: string,
  payload?: { scheduledAt?: string; triggerSource?: string; triggerReference?: string; idempotencyKey?: string }
) => post<SendJob>(`/campaigns/${id}/send`, payload ?? {});

export const listCampaignExperiments = async (campaignId: string) =>
  get<CampaignExperiment[]>(`/campaigns/${campaignId}/experiments`);

export const createCampaignExperiment = async (campaignId: string, payload: CampaignExperimentRequest) =>
  post<CampaignExperiment>(`/campaigns/${campaignId}/experiments`, payload);

export const updateCampaignExperiment = async (
  campaignId: string,
  experimentId: string,
  payload: CampaignExperimentRequest
) => put<CampaignExperiment>(`/campaigns/${campaignId}/experiments/${experimentId}`, payload);

export const deleteCampaignExperiment = async (campaignId: string, experimentId: string) =>
  del<void>(`/campaigns/${campaignId}/experiments/${experimentId}`);

export const promoteExperimentWinner = async (campaignId: string, experimentId: string) =>
  post<CampaignExperiment>(`/campaigns/${campaignId}/experiments/${experimentId}/promote-winner`);

export const getExperimentMetrics = async (campaignId: string, experimentId: string) =>
  get<VariantMetrics[]>(`/campaigns/${campaignId}/experiments/${experimentId}/metrics`);

export const getCampaignBudget = async (campaignId: string) =>
  get<CampaignBudget>(`/campaigns/${campaignId}/budget`);

export const updateCampaignBudget = async (campaignId: string, payload: CampaignBudgetRequest) =>
  put<CampaignBudget>(`/campaigns/${campaignId}/budget`, payload);

export const getFrequencyPolicy = async (campaignId: string) =>
  get<FrequencyPolicy>(`/campaigns/${campaignId}/frequency-policy`);

export const updateFrequencyPolicy = async (campaignId: string, payload: FrequencyPolicyRequest) =>
  put<FrequencyPolicy>(`/campaigns/${campaignId}/frequency-policy`, payload);

export const preflightCampaignSend = async (campaignId: string) =>
  post<SendPreflightReport>(`/campaigns/${campaignId}/send/preflight`, {});

export const createResendPlan = async (
  campaignId: string,
  payload: { resendMode: ResendPlan['resendMode']; confirmed?: boolean; reason?: string }
) => post<ResendPlan>(`/campaigns/${campaignId}/send/resend-plans`, payload);

export const listSendJobDeadLetters = async (jobId: string) =>
  get<DeadLetterEntry[]>(`/send-jobs/${jobId}/dead-letters`);

export const replayDeadLetter = async (jobId: string, deadLetterId: string) =>
  post<DeadLetterEntry>(`/send-jobs/${jobId}/dead-letters/${deadLetterId}/replay`, {});

export const createRequestKey = (scope: string) => {
  if (typeof window !== 'undefined' && window.crypto && 'randomUUID' in window.crypto) {
    return `${scope}-${window.crypto.randomUUID()}`;
  }
  return `${scope}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`;
};

export const getCampaignJobs = async (id: string, page = 0, size = 20) =>
  get<{ content: SendJob[]; totalElements: number; totalPages: number; page: number; size: number }>(
    `/campaigns/${id}/jobs?page=${page}&size=${size}`
  );

export const getSendJob = async (jobId: string) => get<SendJob>(`/send-jobs/${jobId}`);

export const pauseCampaignSend = async (id: string, reason?: string) =>
  post<SendJob>(`/campaigns/${id}/send/pause`, { reason });

export const resumeCampaignSend = async (id: string, comments?: string) =>
  post<SendJob>(`/campaigns/${id}/send/resume`, { comments });

export const cancelCampaignSend = async (id: string, reason?: string) =>
  post<SendJob>(`/campaigns/${id}/send/cancel`, { reason });

export const retrySendJob = async (jobId: string, reason?: string) =>
  post<SendJob>(`/send-jobs/${jobId}/retry`, { reason });

export const resendCampaign = async (id: string, reason?: string) =>
  post<SendJob>(`/campaigns/${id}/send/resend`, { reason });

export const submitCampaignApproval = async (campaignId: string, comments?: string) =>
  post<CampaignApproval>(`/campaigns/${campaignId}/submit-approval`, { comments });

export const getCampaignApprovals = async (campaignId: string) =>
  get<CampaignApproval[]>(`/campaigns/${campaignId}/approvals`);

export const getPendingCampaignApprovals = async () =>
  get<CampaignApproval[]>(`/campaigns/approvals/pending`);

export const approveCampaignApproval = async (approvalId: string, comments?: string) =>
  post<CampaignApproval>(`/campaigns/approvals/${approvalId}/approve`, { comments });

export const rejectCampaignApproval = async (approvalId: string, reason?: string, comments?: string) =>
  post<CampaignApproval>(`/campaigns/approvals/${approvalId}/reject`, { reason, comments });

export const cancelCampaignApproval = async (approvalId: string) =>
  post<CampaignApproval>(`/campaigns/approvals/${approvalId}/cancel`);

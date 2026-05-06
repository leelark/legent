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

export type CampaignListResponse = {
  content: Campaign[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
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

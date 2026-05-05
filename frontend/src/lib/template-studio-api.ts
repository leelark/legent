import apiClient, { del, get, post, put } from '@/lib/api-client';

export type Template = {
  id: string;
  name: string;
  subject?: string;
  htmlContent?: string;
  textContent?: string;
  status: string;
  templateType?: string;
  category?: string;
  tags?: string[];
  metadata?: string;
  draftSubject?: string;
  draftHtmlContent?: string;
  draftTextContent?: string;
  approvalRequired?: boolean;
  currentApprover?: string;
  lastPublishedVersion?: number;
  lastPublishedAt?: string;
  createdAt?: string;
  updatedAt?: string;
};

export type TemplateVersion = {
  id?: string;
  versionNumber: number;
  subject?: string;
  htmlContent?: string;
  textContent?: string;
  changes?: string;
  isPublished?: boolean;
  createdAt?: string;
  updatedAt?: string;
};

export type TemplateApproval = {
  id: string;
  templateId: string;
  versionNumber: number;
  requestedBy?: string;
  requestedAt?: string;
  status?: string;
  approvedBy?: string;
  approvedAt?: string;
  rejectionReason?: string;
  comments?: string;
  updatedAt?: string;
};

export type Asset = {
  id: string;
  name: string;
  fileName?: string;
  contentType: string;
  sizeBytes?: number;
  storagePath?: string;
  createdAt?: string;
};

export type PreviewResponse = {
  subject: string;
  htmlContent: string;
  textContent: string;
  warnings: string[];
};

export type ValidationResponse = {
  linkCount: number;
  brokenLinkCount: number;
  imageCount: number;
  imagesMissingAlt: number;
  brokenLinks: string[];
  warnings: string[];
};

export type VersionCompareResponse = {
  leftVersion: number;
  rightVersion: number;
  subjectChanged: boolean;
  htmlChanged: boolean;
  textChanged: boolean;
  leftSubject?: string;
  rightSubject?: string;
  leftHtmlLength?: number;
  rightHtmlLength?: number;
  leftTextLength?: number;
  rightTextLength?: number;
};

export const listTemplates = async (page = 0, size = 20) =>
  get<any>(`/templates?page=${page}&size=${size}`);

export const searchTemplates = async (query: string) =>
  get<Template[]>(`/templates/search?q=${encodeURIComponent(query)}`);

export const createTemplate = async (payload: {
  name: string;
  subject: string;
  body: string;
  textContent?: string;
  category?: string;
  tags?: string[];
  metadata?: string;
}) => post<Template>('/templates', payload);

export const importTemplateHtml = async (payload: {
  name: string;
  subject: string;
  htmlContent: string;
  textContent?: string;
  category?: string;
  tags?: string[];
  metadata?: string;
  publish?: boolean;
}) => post<Template>('/templates/import/html', payload);

export const getTemplate = async (id: string) => get<Template>(`/templates/${id}`);

export const updateTemplate = async (
  id: string,
  payload: Partial<{
    name: string;
    subject: string;
    htmlContent: string;
    textContent: string;
    category: string;
    tags: string[];
    metadata: string;
  }>
) => put<Template>(`/templates/${id}`, payload);

export const deleteTemplate = async (id: string) => del<void>(`/templates/${id}`);

export const previewTemplate = async (
  id: string,
  payload: { variables?: Record<string, unknown>; mode?: string; darkMode?: boolean }
) => post<PreviewResponse>(`/templates/${id}/preview`, payload);

export const validateTemplate = async (htmlContent: string) =>
  post<ValidationResponse>('/templates/validate', { htmlContent });

export const sendTemplateTestEmail = async (
  id: string,
  payload: { email: string; subjectOverride?: string; variables?: Record<string, unknown> }
) => post<{ status: string; message: string }>(`/templates/${id}/test-send`, payload);

export const exportTemplateHtml = async (id: string) =>
  get<{
    id: string;
    name: string;
    subject: string;
    htmlContent: string;
    textContent: string;
    exportedAt: string;
  }>(`/templates/${id}/export/html`);

export const listTemplateVersions = async (templateId: string) =>
  get<TemplateVersion[]>(`/templates/${templateId}/versions`);

export const compareTemplateVersions = async (templateId: string, left: number, right: number) =>
  get<VersionCompareResponse>(`/templates/${templateId}/versions/compare?left=${left}&right=${right}`);

export const publishTemplateVersion = async (templateId: string, versionNumber: number) =>
  post<TemplateVersion>(`/templates/${templateId}/versions/${versionNumber}/publish`);

export const saveTemplateDraft = async (
  templateId: string,
  payload: { subject?: string; htmlContent?: string; textContent?: string }
) => post<{ templateId: string; status: string }>(`/templates/${templateId}/draft`, payload);

export const submitTemplateApproval = async (templateId: string, comments?: string) =>
  post<TemplateApproval>(`/templates/${templateId}/submit-approval`, { comments });

export const getTemplateApprovals = async (templateId: string) =>
  get<TemplateApproval[]>(`/templates/${templateId}/approvals`);

export const getPendingTemplateApprovals = async () =>
  get<TemplateApproval[]>('/templates/approvals/pending');

export const approveTemplateApproval = async (approvalId: string, comments?: string) =>
  post<TemplateApproval>(`/templates/approvals/${approvalId}/approve`, { comments });

export const rejectTemplateApproval = async (approvalId: string, reason: string) =>
  post<TemplateApproval>(`/templates/approvals/${approvalId}/reject`, { reason });

export const cancelTemplateApproval = async (approvalId: string) =>
  post<TemplateApproval>(`/templates/approvals/${approvalId}/cancel`);

export const publishTemplate = async (templateId: string, versionNumber?: number) =>
  post<TemplateVersion>(`/templates/${templateId}/publish`, { versionNumber });

export const rollbackTemplate = async (
  templateId: string,
  versionNumber: number,
  payload: { reason?: string; publish?: boolean }
) => post<TemplateVersion>(`/templates/${templateId}/rollback/${versionNumber}`, payload);

export const listAssets = async (params?: { page?: number; size?: number; q?: string; contentType?: string }) => {
  const qp = new URLSearchParams();
  qp.set('page', String(params?.page ?? 0));
  qp.set('size', String(params?.size ?? 30));
  if (params?.q) qp.set('q', params.q);
  if (params?.contentType) qp.set('contentType', params.contentType);
  return get<any>(`/assets?${qp.toString()}`);
};

export const uploadAsset = async (file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  const response = await apiClient.post('/assets', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data?.data ?? response.data;
};

export const uploadAssetsBulk = async (files: File[]) => {
  const formData = new FormData();
  files.forEach((file) => formData.append('files', file));
  const response = await apiClient.post('/assets/bulk', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data?.data ?? response.data;
};

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
  status?: string;
  sanitizedHtml?: string;
  textContent?: string;
  linkCount: number;
  brokenLinkCount?: number;
  imageCount: number;
  imagesMissingAlt: number;
  brokenLinks?: string[];
  tokenKeys?: string[];
  dynamicSlots?: string[];
  errors?: string[];
  warnings: string[];
  compatibilityWarnings?: string[];
  ampUnsupported?: boolean;
};

export type ContentSnippet = {
  id: string;
  snippetKey: string;
  name: string;
  snippetType: string;
  content: string;
  description?: string;
  isGlobal?: boolean;
  createdAt?: string;
  updatedAt?: string;
};

export type PersonalizationToken = {
  id: string;
  tokenKey: string;
  displayName: string;
  description?: string;
  sourceType?: string;
  dataPath?: string;
  defaultValue?: string;
  sampleValue?: string;
  required?: boolean;
  createdAt?: string;
  updatedAt?: string;
};

export type DynamicContentRule = {
  id: string;
  templateId: string;
  slotKey: string;
  name: string;
  priority: number;
  conditionField?: string;
  operator: string;
  conditionValue?: string;
  htmlContent?: string;
  textContent?: string;
  active?: boolean;
  createdAt?: string;
  updatedAt?: string;
};

export type BrandKit = {
  id: string;
  name: string;
  logoUrl?: string;
  primaryColor?: string;
  secondaryColor?: string;
  fontFamily?: string;
  footerHtml?: string;
  legalText?: string;
  defaultFromName?: string;
  defaultFromEmail?: string;
  isDefault?: boolean;
  createdAt?: string;
  updatedAt?: string;
};

export type LandingPage = {
  id: string;
  name: string;
  slug: string;
  status: string;
  htmlContent?: string;
  metadata?: string;
  publishedAt?: string;
  createdAt?: string;
  updatedAt?: string;
};

export type RenderResponse = {
  subject: string;
  htmlContent: string;
  textContent: string;
  validationStatus: string;
  versionNumber?: number;
  tokenKeys?: string[];
  dynamicSlots?: string[];
  errors?: string[];
  warnings?: string[];
  compatibilityWarnings?: string[];
  metrics?: Record<string, unknown>;
};

export type TestSendRecord = {
  id: string;
  templateId: string;
  recipientEmail: string;
  recipientGroup?: string;
  subject?: string;
  status: string;
  messageId?: string;
  variablesJson?: string;
  errorMessage?: string;
  createdAt?: string;
  updatedAt?: string;
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
export const cloneTemplate = async (id: string) => post<Template>(`/templates/${id}/clone`);
export const archiveTemplate = async (id: string, reason?: string) => post<Template>(`/templates/${id}/archive`, { reason });
export const restoreTemplate = async (id: string, comments?: string) => post<Template>(`/templates/${id}/restore`, { comments });

export const previewTemplate = async (
  id: string,
  payload: { variables?: Record<string, unknown>; mode?: string; darkMode?: boolean }
) => post<PreviewResponse>(`/templates/${id}/preview`, payload);

export const validateTemplate = async (htmlContent: string) =>
  post<ValidationResponse>('/templates/validate', { htmlContent });

export const renderTemplateEnterprise = async (
  id: string,
  payload: { variables?: Record<string, unknown>; publishedOnly?: boolean; versionNumber?: number; brandKitId?: string }
) => post<RenderResponse>(`/templates/${id}/render`, payload);

export const validateTemplateEnterprise = async (
  id: string,
  payload: { variables?: Record<string, unknown>; publishedOnly?: boolean; versionNumber?: number; brandKitId?: string } = {}
) => post<ValidationResponse>(`/templates/${id}/validate`, payload);

export const sendTemplateTestEmail = async (
  id: string,
  payload: { email: string; recipientGroup?: string; subjectOverride?: string; variables?: Record<string, unknown>; brandKitId?: string }
) => post<{ status: string; message: string }>(`/templates/${id}/test-send`, payload);

export const createTemplateTestSend = async (
  id: string,
  payload: { email: string; recipientGroup?: string; subjectOverride?: string; variables?: Record<string, unknown>; brandKitId?: string }
) => post<TestSendRecord>(`/templates/${id}/test-sends`, payload);

export const listTemplateTestSends = async (id: string) =>
  get<TestSendRecord[]>(`/templates/${id}/test-sends`);

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

export const publishTemplate = async (
  templateId: string,
  versionNumber?: number,
  options?: { adminBypass?: boolean; bypassReason?: string }
) => post<TemplateVersion>(`/templates/${templateId}/publish`, { versionNumber, ...options });

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

export const listContentSnippets = async (page = 0, size = 50) =>
  get<any>(`/content/snippets?page=${page}&size=${size}`);

export const createContentSnippet = async (payload: {
  snippetKey: string;
  name: string;
  snippetType?: string;
  content: string;
  description?: string;
  isGlobal?: boolean;
}) => post<ContentSnippet>('/content/snippets', payload);

export const updateContentSnippet = async (id: string, payload: Partial<ContentSnippet>) =>
  put<ContentSnippet>(`/content/snippets/${id}`, payload);

export const deleteContentSnippet = async (id: string) => del<void>(`/content/snippets/${id}`);

export const listPersonalizationTokens = async (page = 0, size = 100) =>
  get<any>(`/personalization-tokens?page=${page}&size=${size}`);

export const createPersonalizationToken = async (payload: {
  tokenKey: string;
  displayName: string;
  description?: string;
  sourceType?: string;
  dataPath?: string;
  defaultValue?: string;
  sampleValue?: string;
  required?: boolean;
}) => post<PersonalizationToken>('/personalization-tokens', payload);

export const updatePersonalizationToken = async (id: string, payload: Partial<PersonalizationToken>) =>
  put<PersonalizationToken>(`/personalization-tokens/${id}`, payload);

export const deletePersonalizationToken = async (id: string) => del<void>(`/personalization-tokens/${id}`);

export const listDynamicContentRules = async (templateId: string) =>
  get<DynamicContentRule[]>(`/templates/${templateId}/dynamic-content`);

export const createDynamicContentRule = async (
  templateId: string,
  payload: {
    slotKey: string;
    name: string;
    priority?: number;
    conditionField?: string;
    operator?: string;
    conditionValue?: string;
    htmlContent?: string;
    textContent?: string;
    active?: boolean;
  }
) => post<DynamicContentRule>(`/templates/${templateId}/dynamic-content`, payload);

export const updateDynamicContentRule = async (templateId: string, id: string, payload: Partial<DynamicContentRule>) =>
  put<DynamicContentRule>(`/templates/${templateId}/dynamic-content/${id}`, payload);

export const deleteDynamicContentRule = async (templateId: string, id: string) =>
  del<void>(`/templates/${templateId}/dynamic-content/${id}`);

export const listBrandKits = async (page = 0, size = 50) =>
  get<any>(`/brand-kits?page=${page}&size=${size}`);

export const createBrandKit = async (payload: {
  name: string;
  logoUrl?: string;
  primaryColor?: string;
  secondaryColor?: string;
  fontFamily?: string;
  footerHtml?: string;
  legalText?: string;
  defaultFromName?: string;
  defaultFromEmail?: string;
  isDefault?: boolean;
}) => post<BrandKit>('/brand-kits', payload);

export const updateBrandKit = async (id: string, payload: Partial<BrandKit>) =>
  put<BrandKit>(`/brand-kits/${id}`, payload);

export const deleteBrandKit = async (id: string) => del<void>(`/brand-kits/${id}`);

export const listLandingPages = async (page = 0, size = 50) =>
  get<any>(`/landing-pages?page=${page}&size=${size}`);

export const createLandingPage = async (payload: {
  name: string;
  slug: string;
  htmlContent?: string;
  metadata?: string;
  publish?: boolean;
}) => post<LandingPage>('/landing-pages', payload);

export const updateLandingPage = async (id: string, payload: Partial<LandingPage> & { publish?: boolean }) =>
  put<LandingPage>(`/landing-pages/${id}`, payload);

export const publishLandingPage = async (id: string) => post<LandingPage>(`/landing-pages/${id}/publish`);
export const archiveLandingPage = async (id: string) => post<LandingPage>(`/landing-pages/${id}/archive`);
export const deleteLandingPage = async (id: string) => del<void>(`/landing-pages/${id}`);
export const getPublicLandingPage = async (slug: string) =>
  get<LandingPage>(`/public/landing-pages/${encodeURIComponent(slug)}`);

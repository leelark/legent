import { get, post } from '@/lib/api-client';

export const complianceApi = {
  listAuditEvidence: () => get<any[]>('/compliance/audit-evidence'),
  recordAuditEvidence: (payload: any) => post('/compliance/audit-evidence', payload),
  listRetentionMatrix: () => get<any[]>('/compliance/retention-matrix'),
  upsertRetentionPolicy: (payload: any) => post('/compliance/retention-matrix', payload),
  listConsentLedger: (subjectId?: string) =>
    get<any[]>(`/compliance/consent-ledger${subjectId ? `?subjectId=${encodeURIComponent(subjectId)}` : ''}`),
  recordConsent: (payload: any) => post('/compliance/consent-ledger', payload),
  listPrivacyRequests: () => get<any[]>('/compliance/privacy-requests'),
  createPrivacyRequest: (payload: any) => post('/compliance/privacy-requests', payload),
  updatePrivacyRequest: (id: string, payload: any) => post(`/compliance/privacy-requests/${id}/status`, payload),
  listExports: () => get<any[]>('/compliance/exports'),
  createExport: (payload: any) => post('/compliance/exports', payload),
};

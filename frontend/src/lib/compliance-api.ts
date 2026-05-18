import { get, post } from '@/lib/api-client';

export type ComplianceRecord = Record<string, unknown>;
export type ComplianceJsonRecord = Record<string, unknown>;

export type AuditEvidenceRequest = {
  workspaceId?: string;
  eventType: string;
  resourceType: string;
  resourceId?: string;
  retentionCategory?: string;
  legalHold?: boolean;
  payload?: ComplianceJsonRecord;
};

export type RetentionPolicyRequest = {
  workspaceId?: string;
  dataDomain: string;
  resourceType: string;
  retentionDays?: number;
  disposition?: string;
  legalBasis?: string;
  policyVersion?: string;
  active?: boolean;
  metadata?: ComplianceJsonRecord;
};

export type ConsentLedgerRequest = {
  workspaceId?: string;
  subjectId: string;
  email?: string;
  channel: string;
  purpose: string;
  status: string;
  source?: string;
  evidenceRef?: string;
  occurredAt?: string | Date;
  metadata?: ComplianceJsonRecord;
};

export type PrivacyRequest = {
  workspaceId?: string;
  subjectId: string;
  email?: string;
  requestType: string;
  dueAt?: string | Date;
  assignedTo?: string;
  evidence?: ComplianceJsonRecord;
  notes?: string;
};

export type PrivacyStatusRequest = {
  status: string;
  resultUri?: string;
  evidence?: ComplianceJsonRecord;
  notes?: string;
};

export type ComplianceExportRequest = {
  workspaceId?: string;
  exportType: string;
  format?: string;
  filters?: ComplianceJsonRecord;
};

export const complianceApi = {
  listAuditEvidence: () => get<ComplianceRecord[]>('/compliance/audit-evidence'),
  recordAuditEvidence: (payload: AuditEvidenceRequest) => post<ComplianceRecord>('/compliance/audit-evidence', payload),
  listRetentionMatrix: () => get<ComplianceRecord[]>('/compliance/retention-matrix'),
  upsertRetentionPolicy: (payload: RetentionPolicyRequest) => post<ComplianceRecord>('/compliance/retention-matrix', payload),
  listConsentLedger: (subjectId?: string) =>
    get<ComplianceRecord[]>(`/compliance/consent-ledger${subjectId ? `?subjectId=${encodeURIComponent(subjectId)}` : ''}`),
  recordConsent: (payload: ConsentLedgerRequest) => post<ComplianceRecord>('/compliance/consent-ledger', payload),
  listPrivacyRequests: () => get<ComplianceRecord[]>('/compliance/privacy-requests'),
  createPrivacyRequest: (payload: PrivacyRequest) => post<ComplianceRecord>('/compliance/privacy-requests', payload),
  updatePrivacyRequest: (id: string, payload: PrivacyStatusRequest) => post<ComplianceRecord>(`/compliance/privacy-requests/${id}/status`, payload),
  listExports: () => get<ComplianceRecord[]>('/compliance/exports'),
  createExport: (payload: ComplianceExportRequest) => post<ComplianceRecord>('/compliance/exports', payload),
};

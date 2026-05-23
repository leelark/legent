import { del, get, post, put } from './api-client';

export type SendGovernancePolicy = {
  id: string;
  policyKey?: string;
  name?: string;
  description?: string;
  classification?: string;
  commercial?: boolean;
  senderProfileId?: string;
  deliveryProfileId?: string;
  sendingDomain?: string;
  providerId?: string;
  unsubscribePolicy?: string;
  suppressionRequired?: boolean;
  consentRequired?: boolean;
  trackingAllowed?: boolean;
  sendLogRetentionDays?: number;
  publicationPolicy?: string;
  active?: boolean;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
};

export type SendGovernancePolicyRequest = {
  policyKey: string;
  name: string;
  description?: string;
  classification?: string;
  senderProfileId?: string;
  deliveryProfileId?: string;
  sendingDomain?: string;
  providerId?: string;
  unsubscribePolicy?: string;
  suppressionRequired?: boolean;
  consentRequired?: boolean;
  trackingAllowed?: boolean;
  sendLogRetentionDays?: number;
  publicationPolicy?: string;
  active?: boolean;
};

export type SendGovernancePolicyListResponse = {
  content?: SendGovernancePolicy[];
  data?: SendGovernancePolicy[];
  totalElements?: number;
  totalPages?: number;
  page?: number;
  size?: number;
};

const BASE_PATH = '/content/send-governance-policies';

export const listSendGovernancePolicies = (page = 0, size = 100) =>
  get<SendGovernancePolicyListResponse>(`${BASE_PATH}?page=${page}&size=${size}`);

export const getSendGovernancePolicy = (id: string) =>
  get<SendGovernancePolicy>(`${BASE_PATH}/${encodeURIComponent(id)}`);

export const createSendGovernancePolicy = (payload: SendGovernancePolicyRequest) =>
  post<SendGovernancePolicy>(BASE_PATH, payload);

export const updateSendGovernancePolicy = (id: string, payload: SendGovernancePolicyRequest) =>
  put<SendGovernancePolicy>(`${BASE_PATH}/${encodeURIComponent(id)}`, payload);

export const deleteSendGovernancePolicy = (id: string) =>
  del<void>(`${BASE_PATH}/${encodeURIComponent(id)}`);

export function sendGovernancePolicyItems(response: SendGovernancePolicyListResponse | SendGovernancePolicy[]) {
  if (Array.isArray(response)) {
    return response;
  }
  return response.content ?? response.data ?? [];
}

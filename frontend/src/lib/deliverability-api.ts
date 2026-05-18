import { get, post } from './api-client';

export type DeliverabilityDomain = Record<string, unknown>;

export type ReputationScore = {
  score: number;
  lastUpdated: string;
};

export const listDomains = async () => get<DeliverabilityDomain[]>('/deliverability/domains');
export const addDomain = async (domain: string) =>
  post<DeliverabilityDomain>('/deliverability/domains', { domainName: domain });
export const validateDomain = async (domainId: string) =>
  post<DeliverabilityDomain>(`/deliverability/domains/${domainId}/verify`);
export const getReputation = async (domain: string) =>
  get<ReputationScore>(`/reputation/${encodeURIComponent(domain)}`);

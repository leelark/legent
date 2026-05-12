import { get, post } from './api-client';

export const listDomains = async () => get('/deliverability/domains');
export const addDomain = async (domain: string) => post('/deliverability/domains', { domainName: domain });
export const validateDomain = async (domainId: string) => post(`/deliverability/domains/${domainId}/verify`);
export const getReputation = async (domain: string) => get(`/reputation/${encodeURIComponent(domain)}`);
export const getPredictiveDeliverabilityRisk = async (params: { domain?: string; plannedVolume?: number; isp?: string } = {}) => {
  const query = new URLSearchParams();
  if (params.domain) {
    query.set('domain', params.domain);
  }
  if (params.plannedVolume !== undefined) {
    query.set('plannedVolume', String(params.plannedVolume));
  }
  if (params.isp) {
    query.set('isp', params.isp);
  }
  const suffix = query.toString();
  return get(`/deliverability/predictive-risk${suffix ? `?${suffix}` : ''}`);
};

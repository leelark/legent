import { get, post } from './api-client';

export const listDomains = async () => get('/deliverability/domains');
export const addDomain = async (domain: string) => post('/deliverability/domains', { domainName: domain });
export const validateDomain = async (domainId: string) => post(`/deliverability/domains/${domainId}/verify`);
export const getReputation = async (domain: string) => get(`/reputation/${encodeURIComponent(domain)}`);

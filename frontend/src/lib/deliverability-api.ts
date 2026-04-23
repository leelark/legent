import apiClient from './api-client';

export const listDomains = async () => (await apiClient.get('/api/v1/deliverability/domains')).data;
export const addDomain = async (domain: string) => (await apiClient.post('/api/v1/deliverability/domains', { domainName: domain })).data;
export const validateDomain = async (domainId: string) => (await apiClient.post(`/api/v1/deliverability/domains/${domainId}/verify`)).data;
export const getReputation = async (domain: string) => (await apiClient.get('/api/v1/reputation', { params: { domain } })).data;

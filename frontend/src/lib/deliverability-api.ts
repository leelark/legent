import apiClient from './api-client';

export const listDomains = async () => (await apiClient.get('/domains')).data;
export const addDomain = async (domain: string) => (await apiClient.post('/domains', { domain })).data;
export const validateDomain = async (domain: string) => (await apiClient.post('/domains/validate', null, { params: { domain } })).data;
export const getReputation = async (domain: string) => (await apiClient.get('/reputation', { params: { domain } })).data;

import apiClient from './api-client';

export const getDmarcReports = async (domain: string) => (await apiClient.get('/dmarc/reports', { params: { domain } })).data;
export const ingestDmarcReport = async (domain: string, type: string, xml: string, summary?: string) => (await apiClient.post('/dmarc/ingest', xml, { params: { domain, type, summary } })).data;

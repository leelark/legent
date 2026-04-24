import { get, post } from './api-client';

export const getDmarcReports = async (domain: string) => get('/dmarc/reports', { params: { domain } });
export const ingestDmarcReport = async (domain: string, type: string, xml: string, summary?: string) =>
  post('/dmarc/ingest', xml, { params: { domain, type, summary } });

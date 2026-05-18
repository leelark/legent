import { get } from './api-client';

export const getDmarcReports = async (domain: string) => get('/dmarc/reports', { params: { domain } });

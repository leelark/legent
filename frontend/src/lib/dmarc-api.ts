import { get } from './api-client';

export type DmarcReport = {
  id: number | string;
  reportType: string;
  receivedAt: string;
  parsedSummary: unknown;
};

export const getDmarcReports = async (domain: string) =>
  get<DmarcReport[]>('/dmarc/reports', { params: { domain } });

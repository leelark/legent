export const APP_NAME = 'Legent: Email Studio';
export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ||
  process.env.NEXT_PUBLIC_API_URL ||
  '';
export const DEFAULT_PAGE_SIZE = 20;
export const MAX_PAGE_SIZE = 100;

export const ROUTES = {
  EMAIL: '/email',
  AUDIENCE: '/audience',
  CAMPAIGNS: '/campaigns',
  AUTOMATION: '/automation',
  TRACKING: '/tracking',
  DELIVERABILITY: '/deliverability',
  ADMIN: '/admin',
  LOGIN: '/login',
} as const;

export const HEADER_TENANT_ID = 'X-Tenant-Id';
export const HEADER_CORRELATION_ID = 'X-Correlation-Id';

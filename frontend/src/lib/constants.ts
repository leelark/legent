export const APP_NAME = 'Legent: Email Studio';
export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ||
  process.env.NEXT_PUBLIC_API_URL ||
  '';
export const DEFAULT_PAGE_SIZE = 20;
export const MAX_PAGE_SIZE = 100;

export const ROUTES = {
  EMAIL: '/app/email',
  AUDIENCE: '/app/audience',
  CAMPAIGNS: '/app/campaigns',
  AUTOMATION: '/app/automation',
  TRACKING: '/app/tracking',
  DELIVERABILITY: '/app/deliverability',
  ADMIN: '/app/admin',
  LOGIN: '/login',
  SIGNUP: '/signup',
  FORGOT_PASSWORD: '/forgot-password',
  RESET_PASSWORD: '/reset-password',
  ONBOARDING: '/onboarding',
} as const;

export const HEADER_TENANT_ID = 'X-Tenant-Id';
export const HEADER_CORRELATION_ID = 'X-Correlation-Id';

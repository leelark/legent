import { post, postPublic } from './api-client';

export interface LoginRequest {
  email: string;
  password?: string;
}

export interface SignupRequest {
  email: string;
  password?: string;
  firstName: string;
  lastName: string;
  companyName: string;
  slug?: string;
}

export interface ForgotPasswordRequest {
  email: string;
  tenantId?: string | null;
  workspaceId?: string | null;
}

export interface LoginResponse {
  status: string;
  userId: string;
  tenantId: string;
  roles: string[];
}

type AuthActionResponse = {
  status: string;
  message: string;
};

function readOptionalHint(value: string | null | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function buildForgotPasswordPayload(request: ForgotPasswordRequest): ForgotPasswordRequest {
  const payload: ForgotPasswordRequest = {
    email: request.email.trim(),
  };
  const tenantId = readOptionalHint(request.tenantId);
  const workspaceId = readOptionalHint(request.workspaceId);

  if (tenantId) payload.tenantId = tenantId;
  if (workspaceId) payload.workspaceId = workspaceId;

  return payload;
}

export const authApi = {
  login: (request: LoginRequest, tenantId?: string | null) =>
    post<LoginResponse>('/auth/login', request, tenantId ? {
      headers: { 'X-Tenant-Id': tenantId }
    } : undefined),
    
  signup: (request: SignupRequest) => 
    post<LoginResponse>('/auth/signup', request),

  logout: () =>
    post<void>('/auth/logout'),

  refresh: () =>
    post<LoginResponse>('/auth/refresh'),

  forgotPassword: (request: ForgotPasswordRequest) =>
    postPublic<AuthActionResponse>('/auth/forgot-password', buildForgotPasswordPayload(request)),

  resetPassword: (token: string, newPassword: string) =>
    post<AuthActionResponse>('/auth/reset-password', { token, newPassword }),

  startOnboarding: (payload: { workspaceId?: string; stepKey?: string; payload?: Record<string, unknown> }) =>
    post<Record<string, unknown>>('/auth/onboarding/start', payload),

  completeOnboarding: (payload: { workspaceId?: string; payload?: Record<string, unknown> }) =>
    post<Record<string, unknown>>('/auth/onboarding/complete', payload),
};

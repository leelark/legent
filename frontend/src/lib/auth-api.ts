import { post } from './api-client';

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

export interface LoginResponse {
  status: string;
  userId: string;
  tenantId: string;
  roles: string[];
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

  forgotPassword: (email: string) =>
    post<{ status: string; message: string }>('/auth/forgot-password', { email }),

  resetPassword: (token: string, newPassword: string) =>
    post<{ status: string; message: string }>('/auth/reset-password', { token, newPassword }),

  startOnboarding: (payload: { workspaceId?: string; stepKey?: string; payload?: Record<string, unknown> }) =>
    post<Record<string, unknown>>('/auth/onboarding/start', payload),

  completeOnboarding: (payload: { workspaceId?: string; payload?: Record<string, unknown> }) =>
    post<Record<string, unknown>>('/auth/onboarding/complete', payload),
};

interface ApiResponse<T> {
  success: boolean;
  data: T;
  error?: string;
}

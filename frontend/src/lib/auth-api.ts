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
  token: string;
}

export const authApi = {
  login: (tenantId: string, request: LoginRequest) => 
    post<LoginResponse>('/auth/login', request, {
      headers: { 'X-Tenant-Id': tenantId }
    }),
    
  signup: (request: SignupRequest) => 
    post<LoginResponse>('/auth/signup', request),
};

interface ApiResponse<T> {
  success: boolean;
  data: T;
  error?: string;
}

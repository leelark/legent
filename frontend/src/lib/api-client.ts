import axios from 'axios';
import type { AxiosInstance, AxiosRequestConfig } from 'axios';
import {
  getStoredTenantId,
  WORKSPACE_STORAGE_KEY,
  ENVIRONMENT_STORAGE_KEY,
} from '@/lib/auth';

const API_BASE_URL = (
  process.env.NEXT_PUBLIC_API_BASE_URL ||
  process.env.NEXT_PUBLIC_API_URL ||
  ''
).replace(/\/$/, '');

function resolveApiUrl(url: string | undefined) {
  if (!url) {
    return `${API_BASE_URL}/api/v1`;
  }

  if (/^https?:\/\//.test(url)) {
    return url;
  }

  if (url.startsWith('/api/')) {
    return `${API_BASE_URL}${url}`;
  }

  const normalizedUrl = url.startsWith('/') ? url : `/${url}`;
  return `${API_BASE_URL}/api/v1${normalizedUrl}`;
}

function getApiPath(url: string | undefined): string {
  if (!url) {
    return '';
  }

  try {
    const path = new URL(url, 'http://legent.local').pathname;
    const apiIndex = path.indexOf('/api/v1');
    return (apiIndex >= 0 ? path.slice(apiIndex) : path).replace(/\/+$/, '') || '/';
  } catch {
    return '';
  }
}

function matchesPath(path: string, route: string): boolean {
  return path === route || path.startsWith(`${route}/`);
}

function isAuthEndpoint(url: string | undefined): boolean {
  return matchesPath(getApiPath(url), '/api/v1/auth');
}

function isTenantFreeEndpoint(url: string | undefined): boolean {
  const path = getApiPath(url);
  return (
    matchesPath(path, '/api/v1/health') ||
    matchesPath(path, '/api/v1/public') ||
    path === '/api/v1/tracking/o.gif' ||
    matchesPath(path, '/api/v1/tracking/c')
  );
}

function isWorkspaceOptionalEndpoint(url: string | undefined): boolean {
  const path = getApiPath(url);
  return [
    '/api/v1/users/preferences',
    '/api/v1/admin/bootstrap',
    '/api/v1/admin/branding',
    '/api/v1/admin/configs',
    '/api/v1/admin/contact-requests',
    '/api/v1/admin/operations',
    '/api/v1/admin/public-content',
    '/api/v1/admin/settings',
    '/api/v1/core',
    '/api/v1/differentiation',
    '/api/v1/federation',
    '/api/v1/global',
    '/api/v1/performance-intelligence',
    '/api/v1/platform/notifications',
    '/api/v1/platform/search',
    '/api/v1/platform/webhooks',
  ].some((route) => matchesPath(path, route));
}

function hasHeader(headers: NonNullable<AxiosRequestConfig['headers']>, name: string): boolean {
  const axiosHeaders = headers as { get?: (headerName: string) => unknown };
  const value =
    typeof axiosHeaders.get === 'function'
      ? axiosHeaders.get(name)
      : (headers as Record<string, unknown>)[name] ?? (headers as Record<string, unknown>)[name.toLowerCase()];
  return value !== undefined && value !== null && String(value).trim() !== '';
}

function setHeader(headers: NonNullable<AxiosRequestConfig['headers']>, name: string, value: string): void {
  const axiosHeaders = headers as { set?: (headerName: string, headerValue: string) => void };
  if (typeof axiosHeaders.set === 'function') {
    axiosHeaders.set(name, value);
    return;
  }
  (headers as Record<string, string>)[name] = value;
}

function parseApiError(error: any) {
  const response = error?.response;
  const payload = response?.data;
  const errorDetail = payload?.error ?? {};
  return {
    status: response?.status,
    errorCode: errorDetail?.errorCode || errorDetail?.code || 'UNKNOWN_ERROR',
    message: errorDetail?.message || payload?.message || error?.message || 'Request failed',
    details: errorDetail?.details,
  };
}

/**
 * Pre-configured Axios instance for API calls.
 * SECURITY: Uses HTTP-only cookies for authentication (immune to XSS).
 * The browser automatically sends cookies with withCredentials: true.
 */
const apiClient: AxiosInstance = axios.create({
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true, // Important: sends HTTP-only cookies with requests
});

const publicApiClient: AxiosInstance = axios.create({
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: false,
});

// -- Request Interceptor --
apiClient.interceptors.request.use(async (config) => {
  const resolvedUrl = resolveApiUrl(config.url);
  config.url = resolvedUrl;

  if (typeof window !== 'undefined') {
    const tenantId = getStoredTenantId();
    const workspaceId = localStorage.getItem(WORKSPACE_STORAGE_KEY);
    const environmentId = localStorage.getItem(ENVIRONMENT_STORAGE_KEY);
    const isAuthRequest = isAuthEndpoint(resolvedUrl);
    const isTenantFreeRequest = isTenantFreeEndpoint(resolvedUrl);
    const requestId =
      (window.crypto && 'randomUUID' in window.crypto)
        ? window.crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(16).slice(2)}`;

    const needsTenantContext = !isAuthRequest && !isTenantFreeRequest;
    const needsWorkspaceContext =
      needsTenantContext &&
      !isWorkspaceOptionalEndpoint(resolvedUrl);

    config.headers = config.headers ?? {};
    if (!isAuthRequest && tenantId && !hasHeader(config.headers, 'X-Tenant-Id')) {
      setHeader(config.headers, 'X-Tenant-Id', tenantId);
    }
    if (!isAuthRequest && workspaceId && !hasHeader(config.headers, 'X-Workspace-Id')) {
      setHeader(config.headers, 'X-Workspace-Id', workspaceId);
    }
    if (!isAuthRequest && environmentId && !hasHeader(config.headers, 'X-Environment-Id')) {
      setHeader(config.headers, 'X-Environment-Id', environmentId);
    }
    if (!hasHeader(config.headers, 'X-Request-Id')) {
      setHeader(config.headers, 'X-Request-Id', requestId);
    }

    if (needsTenantContext && !hasHeader(config.headers, 'X-Tenant-Id')) {
      console.warn('[context-check] Missing tenant header', {
        url: resolvedUrl,
      });
      const contextError: any = new Error('Tenant context is required. Please select a tenant.');
      contextError.response = {
        status: 400,
        data: {
          success: false,
          error: {
            errorCode: 'MISSING_TENANT',
            message: 'Tenant context is required',
            details: 'X-Tenant-Id is missing from request context.',
          },
        },
      };
      throw contextError;
    }

    if (needsWorkspaceContext && !hasHeader(config.headers, 'X-Workspace-Id')) {
      console.warn('[context-check] Missing workspace header', {
        url: resolvedUrl,
        tenantId,
      });
      const contextError: any = new Error('Workspace context is required. Please select a workspace.');
      contextError.response = {
        status: 400,
        data: {
          success: false,
          error: {
            errorCode: 'MISSING_WORKSPACE',
            message: 'Workspace context is required',
            details: 'X-Workspace-Id is missing from request context.',
          },
        },
      };
      throw contextError;
    }
  }

  return config;
});

// -- Response Interceptor --
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    error.normalized = parseApiError(error);
    if (error.response?.status === 401 && !isAuthEndpoint(error.config?.url)) {
      // Handle unauthorized - redirect to login
      if (typeof window !== 'undefined') {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export default apiClient;
export const getApiError = parseApiError;

// -- Convenience helpers --
export async function get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await apiClient.get<any>(url, config);
  const resData = response.data;
  if (resData?.pagination) {
    return {
      content: resData.data,
      data: resData.data,
      ...resData.pagination
    } as any;
  }
  return resData?.data ?? resData;
}

export async function getPublic<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await publicApiClient.get<any>(resolveApiUrl(url), config);
  const resData = response.data;
  return resData?.data ?? resData;
}

export async function post<T = any>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const response = await apiClient.post<any>(url, data, config);
  return response.data?.data ?? response.data;
}

export async function put<T = any>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const response = await apiClient.put<any>(url, data, config);
  return response.data?.data ?? response.data;
}

export async function del<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await apiClient.delete<any>(url, config);
  return response.data?.data ?? response.data;
}

import axios from 'axios';
import type { AxiosInstance, AxiosRequestConfig } from 'axios';
import {
  getStoredTenantId,
  TENANT_STORAGE_KEY,
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

function isAuthEndpoint(url: string | undefined): boolean {
  if (!url) {
    return false;
  }
  return /\/api\/v1\/auth(?:\/|$)/.test(url);
}

function isTenantFreeEndpoint(url: string | undefined): boolean {
  if (!url) {
    return false;
  }
  return /\/api\/v1\/(?:health|track\/open\.gif|track\/click|tracking\/o\.gif|tracking\/c)/.test(url);
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

const contextBootstrapClient: AxiosInstance = axios.create({
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
});

type AuthContextRecord = {
  tenantId: string;
  workspaceId?: string | null;
  environmentId?: string | null;
  default?: boolean;
};

async function bootstrapWorkspaceContext(preferredTenantId?: string | null): Promise<{
  tenantId: string;
  workspaceId: string;
  environmentId?: string | null;
} | null> {
  if (typeof window === 'undefined') {
    return null;
  }

  const contextsUrl = resolveApiUrl('/auth/contexts');
  const switchUrl = resolveApiUrl('/auth/context/switch');
  const contextsResponse = await contextBootstrapClient.get<any>(contextsUrl);
  const contexts = contextsResponse?.data?.data;
  if (!Array.isArray(contexts) || contexts.length === 0) {
    return null;
  }

  const records = contexts as AuthContextRecord[];
  const target =
    records.find((ctx) => ctx.tenantId === preferredTenantId && Boolean(ctx.workspaceId)) ??
    records.find((ctx) => Boolean(ctx.default) && Boolean(ctx.workspaceId)) ??
    records.find((ctx) => Boolean(ctx.workspaceId));
  if (!target?.tenantId || !target.workspaceId) {
    return null;
  }

  await contextBootstrapClient.post(switchUrl, {
    tenantId: target.tenantId,
    workspaceId: target.workspaceId,
    environmentId: target.environmentId ?? null,
  });

  localStorage.setItem(TENANT_STORAGE_KEY, target.tenantId);
  localStorage.setItem(WORKSPACE_STORAGE_KEY, target.workspaceId);
  if (target.environmentId) {
    localStorage.setItem(ENVIRONMENT_STORAGE_KEY, target.environmentId);
  } else {
    localStorage.removeItem(ENVIRONMENT_STORAGE_KEY);
  }

  return {
    tenantId: target.tenantId,
    workspaceId: target.workspaceId,
    environmentId: target.environmentId ?? null,
  };
}

// ── Request Interceptor ──
apiClient.interceptors.request.use(async (config) => {
  const resolvedUrl = resolveApiUrl(config.url);
  config.url = resolvedUrl;

  if (typeof window !== 'undefined') {
    let tenantId = getStoredTenantId();
    let workspaceId = localStorage.getItem(WORKSPACE_STORAGE_KEY);
    let environmentId = localStorage.getItem(ENVIRONMENT_STORAGE_KEY);
    const requestId =
      (window.crypto && 'randomUUID' in window.crypto)
        ? window.crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(16).slice(2)}`;

    const needsWorkspaceContext = !isAuthEndpoint(resolvedUrl) && !isTenantFreeEndpoint(resolvedUrl);
    if (needsWorkspaceContext && !workspaceId) {
      try {
        const activeContext = await bootstrapWorkspaceContext(tenantId);
        if (activeContext) {
          tenantId = activeContext.tenantId;
          workspaceId = activeContext.workspaceId;
          environmentId = activeContext.environmentId ?? null;
        }
      } catch {
        // Explicit missing context error below.
      }
    }

    config.headers = config.headers ?? {};
    if (tenantId && !config.headers['X-Tenant-Id']) {
      config.headers['X-Tenant-Id'] = tenantId;
    }
    if (workspaceId && !config.headers['X-Workspace-Id']) {
      config.headers['X-Workspace-Id'] = workspaceId;
    }
    if (environmentId && !config.headers['X-Environment-Id']) {
      config.headers['X-Environment-Id'] = environmentId;
    }
    if (!config.headers['X-Request-Id']) {
      config.headers['X-Request-Id'] = requestId;
    }

    if (needsWorkspaceContext && !workspaceId) {
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

// ── Response Interceptor ──
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    error.normalized = parseApiError(error);
    if (error.response?.status === 401 && !isAuthEndpoint(error.config?.url)) {
      // Handle unauthorized — redirect to login
      if (typeof window !== 'undefined') {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export default apiClient;
export const getApiError = parseApiError;

// ── Convenience helpers ──
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

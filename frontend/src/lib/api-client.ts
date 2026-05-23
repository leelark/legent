import axios from 'axios';
import type { AxiosInstance, AxiosRequestConfig } from 'axios';
import {
  clearStoredAuth,
  getStoredTenantId,
  WORKSPACE_STORAGE_KEY,
  ENVIRONMENT_STORAGE_KEY,
} from '@/lib/auth';

const API_BASE_URL = (
  process.env.NEXT_PUBLIC_API_BASE_URL ||
  process.env.NEXT_PUBLIC_API_URL ||
  ''
).replace(/\/$/, '');

const ABSOLUTE_URL_PATTERN = /^[a-z][a-z\d+\-.]*:/i;

type ApiErrorDetail = {
  errorCode?: unknown;
  code?: unknown;
  message?: unknown;
  details?: unknown;
};

type ApiErrorPayload = {
  data?: unknown;
  error?: ApiErrorDetail;
  message?: unknown;
  pagination?: unknown;
  success?: boolean;
};

type ApiClientError = Error & {
  config?: AxiosRequestConfig;
  normalized?: NormalizedApiError;
  response?: {
    status?: number;
    data?: ApiErrorPayload;
  };
};

type NormalizedApiError = {
  status?: number;
  errorCode: string;
  message: string;
  details?: unknown;
};

function readString(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value : undefined;
}

function readRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}

function createApiClientError(errorCode: string, message: string, details: string): ApiClientError {
  const contextError = new Error(message) as ApiClientError;
  contextError.response = {
    status: 400,
    data: {
      success: false,
      error: {
        errorCode,
        message,
        details,
      },
    },
  };
  return contextError;
}

function getRuntimeOrigin(): string | null {
  if (typeof window === 'undefined' || !window.location?.href) {
    return null;
  }

  try {
    return new URL(window.location.href).origin;
  } catch {
    return null;
  }
}

function getConfiguredApiOrigin(): string | null {
  if (!API_BASE_URL) {
    return null;
  }

  try {
    return new URL(API_BASE_URL).origin;
  } catch {
    const runtimeOrigin = getRuntimeOrigin();
    if (!runtimeOrigin) {
      return null;
    }

    try {
      return new URL(API_BASE_URL, runtimeOrigin).origin;
    } catch {
      return null;
    }
  }
}

function isAbsoluteRequestUrl(url: string): boolean {
  return ABSOLUTE_URL_PATTERN.test(url) || url.startsWith('//');
}

function parseRequestUrl(url: string): URL | null {
  const base = getRuntimeOrigin() ?? getConfiguredApiOrigin() ?? 'http://legent.local';
  try {
    return new URL(url, base);
  } catch {
    return null;
  }
}

function assertApiUrlAllowed(url: string | undefined, details: string): void {
  const rawUrl = url?.trim();
  if (!rawUrl || !isAbsoluteRequestUrl(rawUrl)) {
    return;
  }

  const parsedUrl = parseRequestUrl(rawUrl);
  const allowedOrigin = getConfiguredApiOrigin() ?? getRuntimeOrigin();
  const allowedHttpUrl =
    parsedUrl &&
    (parsedUrl.protocol === 'http:' || parsedUrl.protocol === 'https:') &&
    allowedOrigin &&
    parsedUrl.origin === allowedOrigin;

  if (allowedHttpUrl) {
    return;
  }

  throw createApiClientError(
    'EXTERNAL_API_URL',
    'External API URLs are not allowed',
    details
  );
}

function assertCredentialedApiUrlAllowed(url: string | undefined): void {
  assertApiUrlAllowed(url, 'Credentialed API requests cannot target an external absolute URL.');
}

function assertPublicApiUrlAllowed(url: string | undefined): void {
  assertApiUrlAllowed(url, 'Public API requests cannot target an external absolute URL.');
}

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

const CONTEXT_FREE_AUTH_ENDPOINTS = [
  '/api/v1/auth/login',
  '/api/v1/auth/signup',
  '/api/v1/auth/session',
  '/api/v1/auth/refresh',
  '/api/v1/auth/logout',
  '/api/v1/auth/contexts',
  '/api/v1/auth/context/switch',
];

function isContextFreeAuthEndpoint(url: string | undefined): boolean {
  const path = getApiPath(url);
  return CONTEXT_FREE_AUTH_ENDPOINTS.some((route) => matchesPath(path, route));
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
    '/api/v1/admin/contact-requests',
    '/api/v1/federation',
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

function parseApiError(error: unknown): NormalizedApiError {
  const clientError = error as ApiClientError | null | undefined;
  const response = clientError?.response;
  const payload = response?.data;
  const errorDetail = payload?.error ?? {};
  return {
    status: response?.status,
    errorCode: readString(errorDetail.errorCode) ?? readString(errorDetail.code) ?? 'UNKNOWN_ERROR',
    message: readString(errorDetail.message) ?? readString(payload?.message) ?? readString(clientError?.message) ?? 'Request failed',
    details: errorDetail?.details,
  };
}

function unwrapApiData<T>(data: unknown): T {
  const record = readRecord(data);
  if (!record) {
    return data as T;
  }

  if (record.pagination) {
    const pagination = readRecord(record.pagination) ?? {};
    return {
      content: record.data,
      data: record.data,
      ...pagination,
    } as T;
  }

  return (record.data ?? record) as T;
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
  assertCredentialedApiUrlAllowed(config.baseURL);
  assertCredentialedApiUrlAllowed(config.url);

  const resolvedUrl = resolveApiUrl(config.url);
  config.url = resolvedUrl;
  config.baseURL = undefined;

  if (typeof window !== 'undefined') {
    const tenantId = getStoredTenantId();
    const workspaceId = localStorage.getItem(WORKSPACE_STORAGE_KEY);
    const environmentId = localStorage.getItem(ENVIRONMENT_STORAGE_KEY);
    const isContextFreeAuthRequest = isContextFreeAuthEndpoint(resolvedUrl);
    const isTenantFreeRequest = isTenantFreeEndpoint(resolvedUrl);
    const requestId =
      (window.crypto && 'randomUUID' in window.crypto)
        ? window.crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(16).slice(2)}`;

    const needsTenantContext = !isContextFreeAuthRequest && !isTenantFreeRequest;
    const needsWorkspaceContext =
      needsTenantContext &&
      !isWorkspaceOptionalEndpoint(resolvedUrl);

    config.headers = config.headers ?? {};
    if (!isContextFreeAuthRequest && tenantId && !hasHeader(config.headers, 'X-Tenant-Id')) {
      setHeader(config.headers, 'X-Tenant-Id', tenantId);
    }
    if (!isContextFreeAuthRequest && workspaceId && !hasHeader(config.headers, 'X-Workspace-Id')) {
      setHeader(config.headers, 'X-Workspace-Id', workspaceId);
    }
    if (!isContextFreeAuthRequest && environmentId && !hasHeader(config.headers, 'X-Environment-Id')) {
      setHeader(config.headers, 'X-Environment-Id', environmentId);
    }
    if (!hasHeader(config.headers, 'X-Request-Id')) {
      setHeader(config.headers, 'X-Request-Id', requestId);
    }

    if (needsTenantContext && !hasHeader(config.headers, 'X-Tenant-Id')) {
      console.warn('[context-check] Missing tenant header', {
        url: resolvedUrl,
      });
      throw createApiClientError(
        'MISSING_TENANT',
        'Tenant context is required',
        'X-Tenant-Id is missing from request context.'
      );
    }

    if (needsWorkspaceContext && !hasHeader(config.headers, 'X-Workspace-Id')) {
      console.warn('[context-check] Missing workspace header', {
        url: resolvedUrl,
        tenantId,
      });
      throw createApiClientError(
        'MISSING_WORKSPACE',
        'Workspace context is required',
        'X-Workspace-Id is missing from request context.'
      );
    }
  }

  return config;
});

// -- Response Interceptor --
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const clientError = error as ApiClientError;
    clientError.normalized = parseApiError(clientError);
    if (clientError.response?.status === 401 && !isContextFreeAuthEndpoint(clientError.config?.url)) {
      if (typeof window !== 'undefined') {
        clearStoredAuth();
        window.location.href = '/login';
      }
    }
    return Promise.reject(clientError);
  }
);

export default apiClient;

// -- Convenience helpers --
export async function get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await apiClient.get<unknown>(url, config);
  return unwrapApiData<T>(response.data);
}

export async function getPublic<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
  assertPublicApiUrlAllowed(config?.baseURL);
  assertPublicApiUrlAllowed(url);

  const response = await publicApiClient.get<unknown>(resolveApiUrl(url), config);
  return unwrapApiData<T>(response.data);
}

export async function postPublic<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  assertPublicApiUrlAllowed(config?.baseURL);
  assertPublicApiUrlAllowed(url);

  const response = await publicApiClient.post<unknown>(resolveApiUrl(url), data, config);
  return unwrapApiData<T>(response.data);
}

export async function post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const response = await apiClient.post<unknown>(url, data, config);
  return unwrapApiData<T>(response.data);
}

export async function put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const response = await apiClient.put<unknown>(url, data, config);
  return unwrapApiData<T>(response.data);
}

export async function del<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await apiClient.delete<unknown>(url, config);
  return unwrapApiData<T>(response.data);
}

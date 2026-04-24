import axios from 'axios';
import type { AxiosInstance, AxiosRequestConfig } from 'axios';

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

/**
 * Pre-configured Axios instance for API calls.
 * Automatically injects tenant and auth headers.
 */
const apiClient: AxiosInstance = axios.create({
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// ── Request Interceptor ──
apiClient.interceptors.request.use((config) => {
  config.url = resolveApiUrl(config.url);

  // Inject tenant header
  // In production, this comes from the tenant store
  const tenantId = typeof window !== 'undefined'
    ? localStorage.getItem('legent_tenant_id')
    : null;

  if (tenantId) {
    config.headers['X-Tenant-Id'] = tenantId;
  }

  // Inject auth token
  const token = typeof window !== 'undefined'
    ? localStorage.getItem('legent_token')
    : null;

  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`;
  }

  return config;
});

// ── Response Interceptor ──
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
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

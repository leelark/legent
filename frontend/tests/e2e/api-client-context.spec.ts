import { expect, test } from '@playwright/test';
import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import apiClient from '../../src/lib/api-client';
import { TENANT_STORAGE_KEY, WORKSPACE_STORAGE_KEY } from '../../src/lib/auth';

function installBrowserContext(values: Record<string, string> = {}) {
  const store = new Map(Object.entries(values));
  const storage: Storage = {
    get length() {
      return store.size;
    },
    clear: () => store.clear(),
    getItem: (key) => store.get(key) ?? null,
    key: (index) => Array.from(store.keys())[index] ?? null,
    removeItem: (key) => {
      store.delete(key);
    },
    setItem: (key, value) => {
      store.set(key, value);
    },
  };

  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: storage });
  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    value: {
      crypto: { randomUUID: () => 'test-request-id' },
      location: { href: 'http://127.0.0.1/app' },
    },
  });
}

function captureAdapter(seen: InternalAxiosRequestConfig[]): AxiosAdapter {
  return async (config) => {
    seen.push(config);
    return {
      config,
      data: { success: true, data: { ok: true } },
      headers: {},
      request: {},
      status: 200,
      statusText: 'OK',
    } satisfies AxiosResponse;
  };
}

function getHeader(config: InternalAxiosRequestConfig, name: string): string | undefined {
  const headers = config.headers as { get?: (headerName: string) => unknown } & Record<string, unknown>;
  const value = typeof headers.get === 'function' ? headers.get(name) : headers[name] ?? headers[name.toLowerCase()];
  return value === undefined || value === null ? undefined : String(value);
}

function getErrorCode(error: unknown): string | undefined {
  return (error as { response?: { data?: { error?: { errorCode?: string } } } }).response?.data?.error?.errorCode;
}

async function expectBlocked(url: string, errorCode: string) {
  const seen: InternalAxiosRequestConfig[] = [];
  let thrown: unknown;

  try {
    await apiClient.get(url, { adapter: captureAdapter(seen) });
  } catch (error) {
    thrown = error;
  }

  expect(seen).toHaveLength(0);
  expect(getErrorCode(thrown)).toBe(errorCode);
}

test.describe('api client context preflight', () => {
  let warn: typeof console.warn;

  test.beforeEach(() => {
    warn = console.warn;
    console.warn = () => undefined;
  });

  test.afterEach(() => {
    console.warn = warn;
    delete (globalThis as Record<string, unknown>).localStorage;
    delete (globalThis as Record<string, unknown>).window;
  });

  test('requires tenant for bounded non-public endpoints before dispatch', async () => {
    installBrowserContext({ [WORKSPACE_STORAGE_KEY]: 'workspace-1' });

    await expectBlocked('/authz', 'MISSING_TENANT');
    await expectBlocked('/publicity', 'MISSING_TENANT');
    await expectBlocked('/healthz', 'MISSING_TENANT');
    await expectBlocked('/tracking/campaigns', 'MISSING_TENANT');
  });

  test('allows auth, public, health, and signed tracking endpoints without tenant context', async () => {
    installBrowserContext();
    const seen: InternalAxiosRequestConfig[] = [];

    for (const url of ['/auth/session', '/public/contact', '/health', '/tracking/o.gif', '/tracking/c/signed-click']) {
      await apiClient.get(url, { adapter: captureAdapter(seen) });
    }

    expect(seen).toHaveLength(5);
    for (const config of seen) {
      expect(getHeader(config, 'X-Tenant-Id')).toBeUndefined();
    }
  });

  test('preserves workspace requirements for workspace-scoped endpoints', async () => {
    installBrowserContext({ [TENANT_STORAGE_KEY]: 'tenant-1' });
    const seen: InternalAxiosRequestConfig[] = [];

    await apiClient.get('/users/preferences', { adapter: captureAdapter(seen) });
    expect(seen).toHaveLength(1);
    expect(getHeader(seen[0], 'X-Tenant-Id')).toBe('tenant-1');
    expect(getHeader(seen[0], 'X-Workspace-Id')).toBeUndefined();

    await expectBlocked('/subscribers', 'MISSING_WORKSPACE');
  });
});

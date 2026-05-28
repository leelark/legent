import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ensureActiveContext, type AccountContext } from '../../src/lib/context-bootstrap';
import {
  ENVIRONMENT_STORAGE_KEY,
  TENANT_STORAGE_KEY,
  WORKSPACE_STORAGE_KEY,
} from '../../src/lib/auth';
import { get, post } from '@/lib/api-client';

vi.mock('@/lib/api-client', () => ({
  get: vi.fn(),
  post: vi.fn(),
}));

const mockedGet = vi.mocked(get);
const mockedPost = vi.mocked(post);

const createStorage = () => {
  const values = new Map<string, string>();
  return {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
  };
};

const installBrowserStorage = () => {
  const storage = createStorage();
  Object.defineProperty(globalThis, 'window', { configurable: true, value: {} });
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: storage });
  return storage;
};

beforeEach(() => {
  mockedGet.mockReset();
  mockedPost.mockReset();
});

afterEach(() => {
  delete (globalThis as Record<string, unknown>).window;
  delete (globalThis as Record<string, unknown>).localStorage;
});

describe('ensureActiveContext', () => {
  it('returns null without a browser runtime', async () => {
    await expect(ensureActiveContext()).resolves.toBeNull();
    expect(mockedGet).not.toHaveBeenCalled();
    expect(mockedPost).not.toHaveBeenCalled();
  });

  it('switches to the preferred tenant, workspace, and environment when present', async () => {
    const storage = installBrowserStorage();
    storage.setItem(TENANT_STORAGE_KEY, 'tenant-1');
    storage.setItem(WORKSPACE_STORAGE_KEY, 'workspace-1');
    storage.setItem(ENVIRONMENT_STORAGE_KEY, 'prod');
    const contexts: AccountContext[] = [
      { tenantId: 'tenant-1', workspaceId: 'workspace-1', environmentId: 'local', default: true },
      { tenantId: 'tenant-1', workspaceId: 'workspace-1', environmentId: 'prod' },
    ];
    mockedGet.mockResolvedValue(contexts);

    await expect(ensureActiveContext()).resolves.toEqual({
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      environmentId: 'prod',
    });

    expect(mockedPost).toHaveBeenCalledWith('/auth/context/switch', {
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      environmentId: 'prod',
    });
    expect(storage.getItem(TENANT_STORAGE_KEY)).toBe('tenant-1');
    expect(storage.getItem(WORKSPACE_STORAGE_KEY)).toBe('workspace-1');
    expect(storage.getItem(ENVIRONMENT_STORAGE_KEY)).toBe('prod');
  });

  it('fails closed when supplied preferred workspace is unavailable', async () => {
    installBrowserStorage();
    mockedGet.mockResolvedValue([{ tenantId: 'tenant-1', workspaceId: 'workspace-1', default: true }]);

    await expect(ensureActiveContext({ preferredWorkspaceId: 'missing-workspace' })).resolves.toBeNull();

    expect(mockedPost).not.toHaveBeenCalled();
  });

  it('falls back to the first usable context and clears missing environment metadata', async () => {
    const storage = installBrowserStorage();
    storage.setItem(ENVIRONMENT_STORAGE_KEY, 'stale');
    mockedGet.mockResolvedValue([
      { tenantId: 'tenant-1', workspaceId: 'workspace-1' },
      { tenantId: 'tenant-2', workspaceId: 'workspace-2', default: true },
    ]);

    await expect(ensureActiveContext()).resolves.toEqual({
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      environmentId: null,
    });

    expect(mockedPost).toHaveBeenCalledWith('/auth/context/switch', {
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      environmentId: null,
    });
    expect(storage.getItem(TENANT_STORAGE_KEY)).toBe('tenant-1');
    expect(storage.getItem(WORKSPACE_STORAGE_KEY)).toBe('workspace-1');
    expect(storage.getItem(ENVIRONMENT_STORAGE_KEY)).toBeNull();
  });
});

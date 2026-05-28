import { afterEach, describe, expect, it } from 'vitest';
import {
  clearStoredAuth,
  ENVIRONMENT_STORAGE_KEY,
  getStoredTenantId,
  initializeAuthState,
  ROLES_STORAGE_KEY,
  TENANT_STORAGE_KEY,
  USER_STORAGE_KEY,
  WORKSPACE_STORAGE_KEY,
} from '../../src/lib/auth';

const createStorage = () => {
  const values = new Map<string, string>();
  return {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
    clear: () => values.clear(),
  };
};

const installBrowserStorage = () => {
  const storage = createStorage();
  Object.defineProperty(globalThis, 'window', { configurable: true, value: {} });
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: storage });
  return storage;
};

afterEach(() => {
  delete (globalThis as Record<string, unknown>).window;
  delete (globalThis as Record<string, unknown>).localStorage;
});

describe('auth metadata storage helpers', () => {
  it('returns empty state without a browser runtime', () => {
    expect(getStoredTenantId()).toBeNull();
    expect(initializeAuthState()).toEqual({ userId: null, roles: [] });
    expect(() => clearStoredAuth()).not.toThrow();
  });

  it('reads trimmed tenant context with legacy fallback', () => {
    const storage = installBrowserStorage();

    storage.setItem('legent_tenant_id_legacy', ' legacy-tenant ');
    expect(getStoredTenantId()).toBe('legacy-tenant');

    storage.setItem(TENANT_STORAGE_KEY, ' tenant-1 ');
    expect(getStoredTenantId()).toBe('tenant-1');
  });

  it('initializes only non-sensitive user metadata', () => {
    const storage = installBrowserStorage();

    storage.setItem(USER_STORAGE_KEY, ' user-1 ');
    storage.setItem(ROLES_STORAGE_KEY, JSON.stringify(['ADMIN']));
    storage.setItem('access_token', 'must-not-be-read');

    expect(initializeAuthState()).toEqual({ userId: ' user-1 ', roles: [] });
  });

  it('clears auth and routing context without touching unrelated local preferences', () => {
    const storage = installBrowserStorage();
    for (const [key, value] of [
      [USER_STORAGE_KEY, 'user-1'],
      [ROLES_STORAGE_KEY, '["ADMIN"]'],
      [TENANT_STORAGE_KEY, 'tenant-1'],
      [WORKSPACE_STORAGE_KEY, 'workspace-1'],
      [ENVIRONMENT_STORAGE_KEY, 'local'],
      ['legent_tenant_id_legacy', 'tenant-legacy'],
      ['legent_public_theme', 'dark'],
    ]) {
      storage.setItem(key, value);
    }

    clearStoredAuth();

    expect(storage.getItem(USER_STORAGE_KEY)).toBeNull();
    expect(storage.getItem(ROLES_STORAGE_KEY)).toBeNull();
    expect(storage.getItem(TENANT_STORAGE_KEY)).toBeNull();
    expect(storage.getItem(WORKSPACE_STORAGE_KEY)).toBeNull();
    expect(storage.getItem(ENVIRONMENT_STORAGE_KEY)).toBeNull();
    expect(storage.getItem('legent_tenant_id_legacy')).toBeNull();
    expect(storage.getItem('legent_public_theme')).toBe('dark');
  });
});

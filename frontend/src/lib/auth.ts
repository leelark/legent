// SECURITY: Session credentials are stored in HTTP-only, Secure, SameSite cookies
// set by the backend. JavaScript keeps only non-sensitive UI/session metadata
// and tenant/workspace/environment routing context needed for request headers.
export const USER_STORAGE_KEY = 'legent_user_id';
export const ROLES_STORAGE_KEY = 'legent_roles';
export const THEME_STORAGE_KEY = 'legent_theme';
export const TENANT_STORAGE_KEY = 'legent_tenant_id';
export const WORKSPACE_STORAGE_KEY = 'legent_workspace_id';
export const ENVIRONMENT_STORAGE_KEY = 'legent_environment_id';
const LEGACY_TENANT_STORAGE_KEY = 'legent_tenant_id_legacy';

/**
 * Returns the locally cached tenant routing context used by the API client.
 * Authentication remains cookie-backed; this value is only used as request context.
 */
export function getStoredTenantId(): string | null {
  if (typeof window === 'undefined') {
    return null;
  }
  const tenantId = localStorage.getItem(TENANT_STORAGE_KEY) ?? localStorage.getItem(LEGACY_TENANT_STORAGE_KEY);
  return tenantId && tenantId.trim() ? tenantId.trim() : null;
}

/**
 * AUDIT-020: Centralized auth state management.
 * Clears locally cached UI/session metadata and routing context.
 * Note: HTTP-only session cookies must be cleared by the backend /logout endpoint.
 */
export function clearStoredAuth(): void {
  if (typeof window === 'undefined') {
    return;
  }
  localStorage.removeItem(USER_STORAGE_KEY);
  localStorage.removeItem(ROLES_STORAGE_KEY);
  localStorage.removeItem(TENANT_STORAGE_KEY);
  localStorage.removeItem(WORKSPACE_STORAGE_KEY);
  localStorage.removeItem(ENVIRONMENT_STORAGE_KEY);
  localStorage.removeItem(LEGACY_TENANT_STORAGE_KEY);
  // AUDIT-020: Sync with Zustand store to ensure state consistency
  // HTTP-only cookies cannot be cleared from JavaScript.
  // Call /api/v1/auth/logout endpoint to clear cookies
}

/**
 * AUDIT-020: Initialize auth state from localStorage on app startup.
 * Returns initial state for Zustand store.
 */
export function initializeAuthState(): { userId: string | null; roles: string[] } {
  if (typeof window === 'undefined') {
    return { userId: null, roles: [] };
  }
  const userId = localStorage.getItem(USER_STORAGE_KEY);
  return { userId: userId && userId.trim() ? userId : null, roles: [] };
}

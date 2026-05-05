// SECURITY: JWT and tenantId are now stored in HTTP-only, Secure, SameSite=Strict cookies
// These cookies are set by the backend (AuthController) and cannot be accessed by JavaScript
// This prevents XSS attacks from stealing authentication tokens

// Only non-sensitive data is stored in localStorage
export const USER_STORAGE_KEY = 'legent_user_id';
export const ROLES_STORAGE_KEY = 'legent_roles';
export const THEME_STORAGE_KEY = 'legent_theme';
// AUDIT-021: Removed TOKEN_STORAGE_KEY - tokens are in HTTP-only cookies only
// AUDIT-021: TENANT_STORAGE_KEY kept for backward compatibility (returns null - tenant is in HTTP-only cookies)
export const TENANT_STORAGE_KEY = 'legent_tenant_id_legacy';

/**
 * Stub for backward compatibility. Tenant ID is now stored in HTTP-only cookies.
 * @returns null - tenant must be retrieved from cookie via API call
 */
export function getStoredTenantId(): string | null {
  return null;
}

export interface JwtClaims {
  sub?: string;
  tenantId?: string;
  roles?: string[];
}

/**
 * Parses JWT claims from a token string.
 * Note: In production, the token is in an HTTP-only cookie and not accessible to JavaScript.
 * This function is kept for development/debugging purposes only.
 */
export function parseJwtClaims(token: string): JwtClaims | null {
  try {
    const payload = token.split('.')[1];
    if (!payload) {
      return null;
    }
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), '=');
    const json = decodeURIComponent(
      atob(padded)
        .split('')
        .map((char) => {
          const code = char.charCodeAt(0).toString(16).padStart(2, '0');
          return `%${code}`;
        })
        .join('')
    );
    return JSON.parse(json) as JwtClaims;
  } catch {
    return null;
  }
}

export function getStoredRoles(): string[] {
  if (typeof window === 'undefined') {
    return [];
  }
  const raw = localStorage.getItem(ROLES_STORAGE_KEY);
  if (!raw) {
    return [];
  }
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.map(String) : [];
  } catch {
    return [];
  }
}

/**
 * AUDIT-020: Centralized auth state management.
 * Clears all stored authentication data from localStorage.
 * Note: HTTP-only cookies must be cleared by the backend /logout endpoint.
 */
export function clearStoredAuth(): void {
  if (typeof window === 'undefined') {
    return;
  }
  localStorage.removeItem(USER_STORAGE_KEY);
  localStorage.removeItem(ROLES_STORAGE_KEY);
  // AUDIT-020: Sync with Zustand store to ensure state consistency
  // Note: HTTP-only cookies cannot be cleared from JavaScript
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
  const rolesRaw = localStorage.getItem(ROLES_STORAGE_KEY);
  let roles: string[] = [];
  if (rolesRaw) {
    try {
      const parsed = JSON.parse(rolesRaw);
      roles = Array.isArray(parsed) ? parsed.map(String) : [];
    } catch {
      roles = [];
    }
  }
  return { userId, roles };
}

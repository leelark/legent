// SECURITY: JWT and tenantId are now stored in HTTP-only, Secure, SameSite=Strict cookies
// These cookies are set by the backend (AuthController) and cannot be accessed by JavaScript
// This prevents XSS attacks from stealing authentication tokens

// Only non-sensitive data is stored in localStorage
export const USER_STORAGE_KEY = 'legent_user_id';
export const ROLES_STORAGE_KEY = 'legent_roles';
export const THEME_STORAGE_KEY = 'legent_theme';
export const TOKEN_STORAGE_KEY = 'legent_token';
export const TENANT_STORAGE_KEY = 'legent_tenant_id';

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

/**
 * Gets the stored token from localStorage (DEPRECATED).
 * @deprecated Tokens are now stored in HTTP-only cookies set by the backend.
 * This function is kept for backward compatibility and will return null.
 */
export function getStoredToken(): string | null {
  // Token is now in HTTP-only cookie - not accessible to JavaScript
  // Browser automatically sends cookie with requests to the same domain
  return null;
}

/**
 * Gets the stored tenantId from localStorage (DEPRECATED).
 * @deprecated TenantId is now stored in HTTP-only cookie set by the backend.
 * The API client will handle adding the X-Tenant-Id header from the cookie.
 */
export function getStoredTenantId(): string | null {
  if (typeof window === 'undefined') {
    return null;
  }
  return localStorage.getItem(TENANT_STORAGE_KEY);
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
 * Clears all stored authentication data from localStorage.
 * Note: HTTP-only cookies must be cleared by the backend /logout endpoint.
 */
export function clearStoredAuth(): void {
  if (typeof window === 'undefined') {
    return;
  }
  localStorage.removeItem(USER_STORAGE_KEY);
  localStorage.removeItem(ROLES_STORAGE_KEY);
  // Note: HTTP-only cookies cannot be cleared from JavaScript
  // Call /api/v1/auth/logout endpoint to clear cookies
}

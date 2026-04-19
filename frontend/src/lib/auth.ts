export const TOKEN_STORAGE_KEY = 'legent_token';
export const TENANT_STORAGE_KEY = 'legent_tenant_id';
export const USER_STORAGE_KEY = 'legent_user_id';
export const ROLES_STORAGE_KEY = 'legent_roles';
export const THEME_STORAGE_KEY = 'legent_theme';

export interface JwtClaims {
  sub?: string;
  tenantId?: string;
  roles?: string[];
}

export function parseJwtClaims(token: string): JwtClaims | null {
  try {
    const payload = token.split('.')[1];
    if (!payload) {
      return null;
    }
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(normalized)
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

export function getStoredToken(): string | null {
  return typeof window !== 'undefined' ? localStorage.getItem(TOKEN_STORAGE_KEY) : null;
}

export function getStoredTenantId(): string | null {
  return typeof window !== 'undefined' ? localStorage.getItem(TENANT_STORAGE_KEY) : null;
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

'use client';

import { useAuthStore } from '@/stores/authStore';
import { useCallback } from 'react';

/**
 * Hook for authentication state and actions.
 */
export function useAuth() {
  const { isAuthenticated, userId, token, roles, login, logout } = useAuthStore();

  const hasRole = useCallback(
    (role: string) => roles.includes(role),
    [roles]
  );

  const isAdmin = useCallback(
    () => roles.some((role) => ['ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN'].includes(role)),
    [roles]
  );

  return {
    isAuthenticated,
    userId,
    token,
    roles,
    login,
    logout,
    hasRole,
    isAdmin,
  };
}

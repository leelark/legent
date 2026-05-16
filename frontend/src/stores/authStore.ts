import { create } from 'zustand';
import {
  USER_STORAGE_KEY,
  ROLES_STORAGE_KEY,
  TENANT_STORAGE_KEY,
  WORKSPACE_STORAGE_KEY,
  ENVIRONMENT_STORAGE_KEY,
  initializeAuthState,
} from '@/lib/auth';

interface AuthState {
  isAuthenticated: boolean;
  userId: string | null;
  token: string | null;
  roles: string[];

  login: (userId: string, roles: string[]) => void;
  logout: () => void;
}

// AUDIT-020: Initialize state from centralized source to prevent drift
const initialState = initializeAuthState();
const hasAuthData = initialState.userId !== null;

export const useAuthStore = create<AuthState>((set) => ({
  // AUDIT-020: Initialize from centralized source instead of hardcoded defaults
  isAuthenticated: hasAuthData,
  userId: initialState.userId,
  token: null, // Token is in HTTP-only cookie
  roles: initialState.roles,

  login: (userId, roles) => {
    if (typeof window !== 'undefined') {
      localStorage.setItem(USER_STORAGE_KEY, userId);
      localStorage.setItem(ROLES_STORAGE_KEY, JSON.stringify(roles));
    }
    set({ isAuthenticated: true, userId, token: null, roles });
  },

  logout: () => {
    if (typeof window !== 'undefined') {
      localStorage.removeItem(USER_STORAGE_KEY);
      localStorage.removeItem(ROLES_STORAGE_KEY);
      localStorage.removeItem(TENANT_STORAGE_KEY);
      localStorage.removeItem(WORKSPACE_STORAGE_KEY);
      localStorage.removeItem(ENVIRONMENT_STORAGE_KEY);
    }
    set({ isAuthenticated: false, userId: null, token: null, roles: [] });
  },
}));

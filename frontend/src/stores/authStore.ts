import { create } from 'zustand';
import { USER_STORAGE_KEY, ROLES_STORAGE_KEY } from '@/lib/auth';

interface AuthState {
  isAuthenticated: boolean;
  userId: string | null;
  token: string | null;
  roles: string[];

  login: (userId: string, roles: string[]) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  isAuthenticated: false,
  userId: null,
  token: null,
  roles: [],

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
    }
    set({ isAuthenticated: false, userId: null, token: null, roles: [] });
  },
}));

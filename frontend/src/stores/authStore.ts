import { create } from 'zustand';
import { TOKEN_STORAGE_KEY, USER_STORAGE_KEY, ROLES_STORAGE_KEY } from '@/lib/auth';

interface AuthState {
  isAuthenticated: boolean;
  userId: string | null;
  token: string | null;
  roles: string[];

  login: (userId: string, token: string, roles: string[]) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  isAuthenticated: false,
  userId: null,
  token: null,
  roles: [],

  login: (userId, token, roles) => {
    if (typeof window !== 'undefined') {
      localStorage.setItem(TOKEN_STORAGE_KEY, token);
      localStorage.setItem(USER_STORAGE_KEY, userId);
      localStorage.setItem(ROLES_STORAGE_KEY, JSON.stringify(roles));
    }
    set({ isAuthenticated: true, userId, token, roles });
  },

  logout: () => {
    if (typeof window !== 'undefined') {
      localStorage.removeItem(TOKEN_STORAGE_KEY);
      localStorage.removeItem(USER_STORAGE_KEY);
      localStorage.removeItem(ROLES_STORAGE_KEY);
    }
    set({ isAuthenticated: false, userId: null, token: null, roles: [] });
  },
}));

import { create } from 'zustand';
import { THEME_STORAGE_KEY } from '@/lib/auth';

type Theme = 'light' | 'dark';
type UiMode = 'BASIC' | 'ADVANCED';

interface UIState {
  theme: Theme;
  uiMode: UiMode;
  density: string;
  sidebarCollapsed: boolean;
  rightPanelOpen: boolean;
  globalLoading: boolean;

  toggleTheme: () => void;
  setTheme: (theme: Theme) => void;
  toggleUiMode: () => void;
  setUiMode: (mode: UiMode) => void;
  setDensity: (density: string) => void;
  toggleSidebar: () => void;
  setSidebarCollapsed: (collapsed: boolean) => void;
  toggleRightPanel: () => void;
  setGlobalLoading: (loading: boolean) => void;
}

export const useUIStore = create<UIState>((set, get) => ({
  theme: 'light',
  uiMode: 'BASIC',
  density: 'comfortable',
  sidebarCollapsed: false,
  rightPanelOpen: false,
  globalLoading: false,

  toggleTheme: () => {
    const next = get().theme === 'light' ? 'dark' : 'light';
    if (typeof window !== 'undefined') {
      localStorage.setItem(THEME_STORAGE_KEY, next);
    }
    document.documentElement.classList.toggle('dark', next === 'dark');
    set({ theme: next });
  },

  setTheme: (theme) => {
    if (typeof window !== 'undefined') {
      localStorage.setItem(THEME_STORAGE_KEY, theme);
    }
    document.documentElement.classList.toggle('dark', theme === 'dark');
    set({ theme });
  },

  toggleUiMode: () => {
    const next = get().uiMode === 'BASIC' ? 'ADVANCED' : 'BASIC';
    if (typeof window !== 'undefined') {
      localStorage.setItem('legent_ui_mode', next);
      document.documentElement.classList.toggle('mode-basic', next === 'BASIC');
      document.documentElement.classList.toggle('mode-advanced', next === 'ADVANCED');
    }
    set({ uiMode: next });
  },
  setUiMode: (mode) => {
    if (typeof window !== 'undefined') {
      localStorage.setItem('legent_ui_mode', mode);
      document.documentElement.classList.toggle('mode-basic', mode === 'BASIC');
      document.documentElement.classList.toggle('mode-advanced', mode === 'ADVANCED');
    }
    set({ uiMode: mode });
  },
  setDensity: (density) => set({ density }),

  toggleSidebar: () => set((s) => ({ sidebarCollapsed: !s.sidebarCollapsed })),
  setSidebarCollapsed: (collapsed) => set({ sidebarCollapsed: collapsed }),
  toggleRightPanel: () => set((s) => ({ rightPanelOpen: !s.rightPanelOpen })),
  setGlobalLoading: (loading) => set({ globalLoading: loading }),
}));

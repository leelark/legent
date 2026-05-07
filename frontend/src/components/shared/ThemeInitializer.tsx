'use client';

import { useEffect } from 'react';
import { THEME_STORAGE_KEY } from '@/lib/auth';
import { useUIStore } from '@/stores/uiStore';

export function ThemeInitializer() {
  const setTheme = useUIStore((state) => state.setTheme);
  const setUiMode = useUIStore((state) => state.setUiMode);

  useEffect(() => {
    const storedTheme = localStorage.getItem(THEME_STORAGE_KEY);
    setTheme(storedTheme === 'dark' ? 'dark' : 'light');

    const storedMode = localStorage.getItem('legent_ui_mode');
    setUiMode(storedMode === 'ADVANCED' ? 'ADVANCED' : 'BASIC');
  }, [setTheme, setUiMode]);

  return null;
}

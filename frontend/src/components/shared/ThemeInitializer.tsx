'use client';

import { useEffect } from 'react';
import { THEME_STORAGE_KEY } from '@/lib/auth';
import { normalizeUiMode, UI_MODE_STORAGE_KEY } from '@/lib/ui-mode-contract';
import { useUIStore } from '@/stores/uiStore';

export function ThemeInitializer() {
  const setTheme = useUIStore((state) => state.setTheme);
  const setUiMode = useUIStore((state) => state.setUiMode);

  useEffect(() => {
    const storedTheme = localStorage.getItem(THEME_STORAGE_KEY);
    setTheme(storedTheme === 'dark' ? 'dark' : 'light');

    const storedMode = localStorage.getItem(UI_MODE_STORAGE_KEY);
    setUiMode(normalizeUiMode(storedMode));
  }, [setTheme, setUiMode]);

  return null;
}

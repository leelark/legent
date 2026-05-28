import { afterEach, describe, expect, it } from 'vitest';
import {
  applyUiModeClass,
  getNextUiMode,
  isModeFeatureVisible,
  normalizeUiMode,
  persistUiMode,
  TEMPLATE_STUDIO_MODE_FEATURES,
  UI_MODE_STORAGE_KEY,
  WORKSPACE_NAV_MODE_FEATURES,
} from '../../src/lib/ui-mode-contract';

const createClassList = () => {
  const classes = new Set<string>();
  return {
    classes,
    toggle: (name: string, enabled: boolean) => {
      if (enabled) {
        classes.add(name);
      } else {
        classes.delete(name);
      }
    },
  };
};

const createStorage = () => {
  const values = new Map<string, string>();
  return {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
  };
};

afterEach(() => {
  delete (globalThis as Record<string, unknown>).window;
  delete (globalThis as Record<string, unknown>).localStorage;
  delete (globalThis as Record<string, unknown>).document;
});

describe('ui mode contract helpers', () => {
  it('normalizes invalid modes to basic and toggles modes predictably', () => {
    expect(normalizeUiMode('ADVANCED')).toBe('ADVANCED');
    expect(normalizeUiMode('invalid')).toBe('BASIC');
    expect(getNextUiMode('BASIC')).toBe('ADVANCED');
    expect(getNextUiMode('ADVANCED')).toBe('BASIC');
  });

  it('keeps advanced-only features hidden in basic mode', () => {
    expect(isModeFeatureVisible(WORKSPACE_NAV_MODE_FEATURES.admin, 'BASIC')).toBe(true);
    expect(isModeFeatureVisible(WORKSPACE_NAV_MODE_FEATURES.settings, 'BASIC')).toBe(false);
    expect(isModeFeatureVisible(TEMPLATE_STUDIO_MODE_FEATURES.dynamicContent, 'ADVANCED')).toBe(true);
  });

  it('applies mode classes and dataset without requiring the global document', () => {
    const classList = createClassList();
    const root = { classList, dataset: {} as Record<string, string> };

    applyUiModeClass('ADVANCED', root as unknown as HTMLElement);

    expect(classList.classes.has('mode-basic')).toBe(false);
    expect(classList.classes.has('mode-advanced')).toBe(true);
    expect(root.dataset.uiMode).toBe('advanced');
  });

  it('persists mode metadata and updates the document root class', () => {
    const storage = createStorage();
    const classList = createClassList();
    const documentElement = { classList, dataset: {} as Record<string, string> };
    Object.defineProperty(globalThis, 'window', { configurable: true, value: {} });
    Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: storage });
    Object.defineProperty(globalThis, 'document', {
      configurable: true,
      value: { documentElement },
    });

    persistUiMode('BASIC');

    expect(storage.getItem(UI_MODE_STORAGE_KEY)).toBe('BASIC');
    expect(classList.classes.has('mode-basic')).toBe(true);
    expect(classList.classes.has('mode-advanced')).toBe(false);
    expect(documentElement.dataset.uiMode).toBe('basic');
  });
});

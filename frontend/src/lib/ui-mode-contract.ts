import type { UiMode } from '@/lib/user-preferences-api';

export type { UiMode };

export const UI_MODE_STORAGE_KEY = 'legent_ui_mode';

export type ModeVisibility = 'BASIC_AND_ADVANCED' | 'ADVANCED_ONLY';
export type RoleGate = 'ADMIN';

export type ModeFeature = {
  id: string;
  label: string;
  visibility: ModeVisibility;
  roleGate?: RoleGate;
};

export const WORKSPACE_NAV_MODE_FEATURES = {
  settings: {
    id: 'workspace.nav.settings',
    label: 'Settings',
    visibility: 'ADVANCED_ONLY',
  },
  admin: {
    id: 'workspace.nav.admin',
    label: 'Admin',
    visibility: 'BASIC_AND_ADVANCED',
    roleGate: 'ADMIN',
  },
} as const satisfies Record<string, ModeFeature>;

export const CAMPAIGN_WORKFLOW_MODE_FEATURES = {
  experimentEngine: {
    id: 'campaign.workflow.experiment-engine',
    label: 'Experiment Engine',
    visibility: 'ADVANCED_ONLY',
  },
} as const satisfies Record<string, ModeFeature>;

export function isUiMode(value: unknown): value is UiMode {
  return value === 'BASIC' || value === 'ADVANCED';
}

export function normalizeUiMode(value: unknown): UiMode {
  return isUiMode(value) ? value : 'BASIC';
}

export function getNextUiMode(mode: UiMode): UiMode {
  return mode === 'BASIC' ? 'ADVANCED' : 'BASIC';
}

export function isModeFeatureVisible(feature: ModeFeature, mode: UiMode) {
  return feature.visibility !== 'ADVANCED_ONLY' || mode === 'ADVANCED';
}

export function applyUiModeClass(mode: UiMode, root?: HTMLElement | null) {
  const target = root ?? (typeof document !== 'undefined' ? document.documentElement : null);
  if (!target) {
    return;
  }
  target.classList.toggle('mode-basic', mode === 'BASIC');
  target.classList.toggle('mode-advanced', mode === 'ADVANCED');
  target.dataset.uiMode = mode.toLowerCase();
}

export function persistUiMode(mode: UiMode) {
  if (typeof window !== 'undefined') {
    localStorage.setItem(UI_MODE_STORAGE_KEY, mode);
  }
  applyUiModeClass(mode);
}

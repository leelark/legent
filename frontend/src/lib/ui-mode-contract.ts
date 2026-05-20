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
  budgetGuard: {
    id: 'campaign.workflow.budget-guard',
    label: 'Budget Guard',
    visibility: 'ADVANCED_ONLY',
  },
  frequencyPolicy: {
    id: 'campaign.workflow.frequency-policy',
    label: 'Workspace Frequency Policy',
    visibility: 'ADVANCED_ONLY',
  },
  experimentEngine: {
    id: 'campaign.workflow.experiment-engine',
    label: 'Experiment Engine',
    visibility: 'ADVANCED_ONLY',
  },
} as const satisfies Record<string, ModeFeature>;

export const AUTOMATION_WORKFLOW_MODE_FEATURES = {
  activityAuthoring: {
    id: 'automation.workflow.activity-authoring',
    label: 'Automation Activity Authoring',
    visibility: 'ADVANCED_ONLY',
  },
  activityExecution: {
    id: 'automation.workflow.activity-execution',
    label: 'Automation Activity Execution',
    visibility: 'ADVANCED_ONLY',
  },
  manualTrigger: {
    id: 'automation.workflow.manual-trigger',
    label: 'Manual Workflow Trigger',
    visibility: 'ADVANCED_ONLY',
  },
  draftNodeTypes: {
    id: 'automation.workflow.draft-node-types',
    label: 'Draft Journey Node Types',
    visibility: 'ADVANCED_ONLY',
  },
} as const satisfies Record<string, ModeFeature>;

export const TEMPLATE_STUDIO_MODE_FEATURES = {
  advancedBlocks: {
    id: 'template.studio.advanced-blocks',
    label: 'Advanced Builder Blocks',
    visibility: 'ADVANCED_ONLY',
  },
  conditionalRules: {
    id: 'template.studio.conditional-rules',
    label: 'Conditional Rules',
    visibility: 'ADVANCED_ONLY',
  },
  reusableContent: {
    id: 'template.studio.reusable-content',
    label: 'Reusable Content',
    visibility: 'ADVANCED_ONLY',
  },
  dynamicContent: {
    id: 'template.studio.dynamic-content',
    label: 'Dynamic Content',
    visibility: 'ADVANCED_ONLY',
  },
  personalizationTokens: {
    id: 'template.studio.personalization-tokens',
    label: 'Personalization Tokens',
    visibility: 'ADVANCED_ONLY',
  },
  versionOperations: {
    id: 'template.studio.version-operations',
    label: 'Version Operations',
    visibility: 'ADVANCED_ONLY',
  },
  approvalWorkflow: {
    id: 'template.studio.approval-workflow',
    label: 'Approval Workflow',
    visibility: 'ADVANCED_ONLY',
  },
  assetLibrary: {
    id: 'template.studio.asset-library',
    label: 'Asset Library',
    visibility: 'ADVANCED_ONLY',
  },
  brandKit: {
    id: 'template.studio.brand-kit',
    label: 'Brand Kit',
    visibility: 'ADVANCED_ONLY',
  },
  testSends: {
    id: 'template.studio.test-sends',
    label: 'Test Sends',
    visibility: 'ADVANCED_ONLY',
  },
  publishControls: {
    id: 'template.studio.publish-controls',
    label: 'Publish Controls',
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

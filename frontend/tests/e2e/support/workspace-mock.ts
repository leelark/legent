import type { Page, Route } from '@playwright/test';

type UiMode = 'BASIC' | 'ADVANCED';

type WorkspaceMockOptions = {
  roles?: string[];
  uiMode?: UiMode;
  userId?: string;
};

function ok(data: unknown) {
  return {
    success: true,
    data,
    meta: { timestamp: new Date('2026-05-12T00:00:00Z').toISOString() },
  };
}

function pageOf(data: unknown[]) {
  return {
    success: true,
    data,
    pagination: {
      page: 0,
      size: 50,
      totalElements: data.length,
      totalPages: data.length ? 1 : 0,
    },
  };
}

async function fulfill(route: Route, data: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(data),
  });
}

export async function mockWorkspaceApis(page: Page, options: WorkspaceMockOptions = {}) {
  const roles = options.roles ?? ['ADMIN'];
  const uiMode = options.uiMode ?? 'ADVANCED';
  const userId = options.userId ?? 'smoke-user';

  await page.addInitScript(({ roles, userId }) => {
    localStorage.setItem('legent_user_id', userId);
    localStorage.setItem('legent_roles', JSON.stringify(roles));
    localStorage.setItem('legent_tenant_id', 'tenant-1');
    localStorage.setItem('legent_workspace_id', 'workspace-1');
    localStorage.setItem('legent_environment_id', 'local');
  }, { roles, userId });

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');

    if (path === '/auth/session') {
      return fulfill(route, ok({
        status: 'success',
        userId,
        tenantId: 'tenant-1',
        workspaceId: 'workspace-1',
        environmentId: 'local',
        roles,
      }));
    }
    if (path === '/auth/contexts') {
      return fulfill(route, ok([{ tenantId: 'tenant-1', workspaceId: 'workspace-1', environmentId: 'local', default: true }]));
    }
    if (path === '/auth/context/switch') {
      return fulfill(route, ok({ status: 'success' }));
    }
    if (path === '/users/preferences') {
      return fulfill(route, ok({
        tenantId: 'tenant-1',
        userId,
        theme: 'light',
        uiMode,
        density: 'comfortable',
        sidebarCollapsed: false,
        metadata: {},
      }));
    }
    if (path === '/subscribers/count') {
      return fulfill(route, ok(0));
    }
    if (path === '/analytics/events/counts') {
      return fulfill(route, ok([
        { event_type: 'OPEN', count: 1200 },
        { event_type: 'CLICK', count: 320 },
        { event_type: 'CONVERSION', count: 84 },
      ]));
    }
    if (path === '/analytics/campaigns') {
      return fulfill(route, ok([
        { id: 'campaign-1', campaignId: 'Spring launch', totalSends: 2400, totalOpens: 1200, totalClicks: 320, totalConversions: 84 },
      ]));
    }
    if (path === '/workflows') {
      return fulfill(route, ok([
        { id: 'workflow-1', name: 'Lifecycle nurture', status: 'ACTIVE', activeDefinitionVersion: 3 },
      ]));
    }
    if (path === '/workflows/workflow-1/analytics') {
      return fulfill(route, ok({
        workflowId: 'workflow-1',
        runCount: 24,
        runStatusCounts: { COMPLETED: 18, FAILED: 2, WAITING: 4 },
        stepMetrics: [
          { nodeId: 'entry', nodeType: 'ENTRY_TRIGGER', label: 'Entry', entered: 24, completed: 24, failed: 0, completionRate: 1 },
          { nodeId: 'split', nodeType: 'SPLIT', label: 'Offer path test', entered: 24, completed: 23, failed: 1, completionRate: 0.96 },
          { nodeId: 'goal', nodeType: 'EXIT_GOAL', label: 'Purchase', entered: 8, completed: 8, failed: 0, completionRate: 1 },
        ],
        topPaths: [
          { signature: 'entry -> split -> goal', runs: 8, completed: 8, failed: 0, completionRate: 1 },
          { signature: 'entry -> split -> wait -> send', runs: 16, completed: 10, failed: 2, completionRate: 0.63 },
        ],
        pathTests: [
          { nodeId: 'split', nodeType: 'SPLIT', label: 'Offer path test', observedRuns: 24, observedTargets: { goal: 8, wait: 16 }, interpretation: 'Observed path distribution only' },
        ],
        conversionGoals: [
          { goalId: 'goal', label: 'Purchase', hits: 8, observedRunRate: 0.33 },
        ],
        diagnostics: [
          { code: 'STEP_FAILURE_CONCENTRATION', severity: 'warning', message: 'A workflow step crossed the deterministic failure threshold.', sampleSize: 24 },
        ],
        evidenceNotes: ['Journey path tests are observed execution summaries, not causal attribution.'],
      }));
    }
    if (path === '/analytics/journeys/workflow-1/goals') {
      return fulfill(route, ok([
        { goal_id: 'goal', step_id: 'goal', path_id: 'entry-split-goal', experiment_scope: 'JOURNEY', conversions: 8, unique_subscribers: 8, revenue: 1250 },
      ]));
    }
    if (
      path.startsWith('/subscribers') ||
      path.startsWith('/lists') ||
      path.startsWith('/data-extensions') ||
      path.startsWith('/segments') ||
      path.startsWith('/imports') ||
      path.startsWith('/campaigns')
    ) {
      return fulfill(route, pageOf([]));
    }
    if (path === '/admin/operations/dashboard') {
      return fulfill(route, ok({
        health: { status: 'OPERATIONAL', failedActions24h: 0, pendingSyncEvents: 0 },
        stats: { workspaces: 1, memberships: 1, runtimeConfigs: 0, auditEvents24h: 0 },
        modules: [],
        jobs: [],
        alerts: [],
        activity: [],
        syncEvents: [],
      }));
    }
    if (path.startsWith('/admin/') || path.startsWith('/core/') || path.startsWith('/performance-intelligence/')) {
      return fulfill(route, ok([]));
    }
    if (path === '/providers/health' || path === '/deliverability/domains' || path.startsWith('/reputation/') || path.startsWith('/dmarc/')) {
      return fulfill(route, ok([]));
    }

    return fulfill(route, ok([]));
  });
}

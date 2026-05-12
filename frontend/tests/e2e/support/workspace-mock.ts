import type { Page, Route } from '@playwright/test';

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

export async function mockWorkspaceApis(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem('legent_user_id', 'smoke-user');
    localStorage.setItem('legent_roles', JSON.stringify(['ADMIN']));
    localStorage.setItem('legent_tenant_id', 'tenant-1');
    localStorage.setItem('legent_workspace_id', 'workspace-1');
    localStorage.setItem('legent_environment_id', 'local');
  });

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');

    if (path === '/auth/session') {
      return fulfill(route, ok({
        status: 'success',
        userId: 'smoke-user',
        tenantId: 'tenant-1',
        workspaceId: 'workspace-1',
        environmentId: 'local',
        roles: ['ADMIN'],
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
        userId: 'smoke-user',
        theme: 'light',
        uiMode: 'ADVANCED',
        density: 'comfortable',
        sidebarCollapsed: false,
        metadata: {},
      }));
    }
    if (path === '/subscribers/count') {
      return fulfill(route, ok(0));
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

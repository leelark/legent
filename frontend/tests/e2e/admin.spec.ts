import { expect, test, type Page, type Route } from '@playwright/test';

const contact = {
  id: '01HXCONTACTREQUEST000000001',
  name: 'Ada Lovelace',
  workEmail: 'ada@example.com',
  company: 'Analytical Labs',
  interest: 'Enterprise plan',
  message: 'We need a governed email operating model.',
  sourcePage: 'contact',
  status: 'RECEIVED',
  createdAt: '2026-05-07T09:00:00Z',
};

const user = {
  id: 'user-1',
  tenantId: 'tenant-1',
  email: 'ada@example.com',
  firstName: 'Ada',
  lastName: 'Lovelace',
  role: 'ADMIN',
  roles: ['ADMIN'],
  isActive: true,
  lastLoginAt: '2026-05-07T09:00:00Z',
};

function ok(data: unknown) {
  return {
    success: true,
    data,
    meta: { timestamp: new Date('2026-05-07T09:00:00Z').toISOString() },
  };
}

function paged(data: unknown[]) {
  return {
    success: true,
    data,
    pagination: {
      page: 0,
      size: 30,
      totalElements: data.length,
      totalPages: 1,
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

async function mockAdminApis(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem('legent_user_id', 'admin-user');
    localStorage.setItem('legent_roles', JSON.stringify(['ADMIN']));
    localStorage.setItem('legent_tenant_id', 'tenant-1');
    localStorage.setItem('legent_workspace_id', 'workspace-1');
  });

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');
    const method = request.method();

    if (path === '/auth/session') {
      return fulfill(route, ok({ status: 'success', userId: 'admin-user', tenantId: 'tenant-1', workspaceId: 'workspace-1', roles: ['ADMIN'] }));
    }
    if (path === '/auth/contexts') {
      return fulfill(route, ok([{ tenantId: 'tenant-1', workspaceId: 'workspace-1', environmentId: 'local', default: true }]));
    }
    if (path === '/auth/context/switch') {
      return fulfill(route, ok({ status: 'success' }));
    }
    if (path === '/auth/forgot-password' || path === '/auth/logout-all') {
      return fulfill(route, ok({ status: 'accepted', message: 'queued' }));
    }
    if (path === '/users/preferences' && method === 'PUT') {
      const body = JSON.parse(request.postData() || '{}');
      return fulfill(route, ok({ tenantId: 'tenant-1', userId: 'admin-user', theme: body.theme || 'light', uiMode: body.uiMode || 'ADVANCED', density: body.density || 'comfortable', sidebarCollapsed: false, metadata: body.metadata || {} }));
    }
    if (path === '/users/preferences') {
      return fulfill(route, ok({ tenantId: 'tenant-1', userId: 'admin-user', theme: 'light', uiMode: 'ADVANCED', density: 'comfortable', sidebarCollapsed: false, metadata: {} }));
    }
    if (path === '/users' && method === 'POST') {
      const body = JSON.parse(request.postData() || '{}');
      return fulfill(route, ok({ ...user, id: 'user-2', email: body.email, firstName: body.firstName, lastName: body.lastName, role: body.role, roles: [body.role] }));
    }
    if (path.startsWith('/users/') && method === 'PUT') {
      const body = JSON.parse(request.postData() || '{}');
      return fulfill(route, ok({ ...user, ...body, roles: [body.role] }));
    }
    if (path === '/users') {
      return fulfill(route, ok([user]));
    }
    if (path === '/admin/operations/dashboard') {
      return fulfill(route, ok({
        generatedAt: '2026-05-07T09:00:00Z',
        health: { status: 'OPERATIONAL', failedActions24h: 0, pendingSyncEvents: 0 },
        stats: { workspaces: 2, memberships: 8, runtimeConfigs: 14, auditEvents24h: 23 },
        modules: [
          { key: 'delivery', label: 'Delivery', description: 'providers, queues, retries', configs: 3, auditEvents: 4, syncEvents: 2, status: 'OPERATIONAL' },
          { key: 'automation', label: 'Automation', description: 'journey orchestration', configs: 2, auditEvents: 1, syncEvents: 1, status: 'OPERATIONAL' },
        ],
        jobs: [{ name: 'Config propagation', status: 'APPLIED', queued: 0 }],
        alerts: [{ tone: 'success', title: 'No critical alerts', detail: 'Clean' }],
        activity: [{ action: 'CONFIG_APPLY', resourceType: 'AdminSetting', actorId: 'admin-user', status: 'SUCCESS', createdAt: '2026-05-07T09:00:00Z' }],
        syncEvents: [{ eventType: 'CONFIG_APPLIED', sourceModule: 'admin-settings', status: 'APPLIED', createdAt: '2026-05-07T09:00:00Z' }],
      }));
    }
    if (path === '/admin/operations/access') {
      return fulfill(route, ok({
        roles: [{ role_key: 'ADMIN', display_name: 'Administrator', description: 'Full access', is_system: true, permissions: ['*'] }],
        permissionGroups: [{ group_key: 'OPERATIONS', display_name: 'Operations', permissions: ['delivery:*', 'tracking:*'] }],
        delegatedAccess: [],
        propagation: [{ event_type: 'ROLE_DEFINITION_CHANGED', source_module: 'core-platform', status: 'APPLIED', created_at: '2026-05-07T09:00:00Z' }],
      }));
    }
    if (path === '/admin/operations/sync-events') {
      return fulfill(route, ok([{ event_type: 'CONFIG_APPLIED', source_module: 'admin-settings', status: 'APPLIED', created_at: '2026-05-07T09:00:00Z' }]));
    }
    if (path.startsWith('/core/audit')) {
      return fulfill(route, ok([{ action: 'CONFIG_APPLY', resourceType: 'AdminSetting', resourceId: 'delivery.max', workspaceId: 'workspace-1', status: 'SUCCESS', createdAt: '2026-05-07T09:00:00Z' }]));
    }
    if (path === '/core/roles' && method === 'POST') {
      return fulfill(route, ok({ id: 'role-2', role_key: 'OPERATIONS_GOVERNOR' }));
    }
    if (path.startsWith('/core/')) {
      return fulfill(route, ok([{ id: `${path.slice(6)}-1`, name: path.slice(6) }]));
    }
    if (path === '/providers/health') {
      return fulfill(route, ok([{ id: 'provider-1', name: 'Primary SMTP', type: 'SMTP', isActive: true, healthStatus: 'HEALTHY', priority: 1 }]));
    }
    if (path === '/deliverability/domains' && method === 'POST') {
      return fulfill(route, ok({ id: 'domain-2', domainName: 'new.example.com', status: 'PENDING' }));
    }
    if (path === '/deliverability/domains') {
      return fulfill(route, ok([{ id: 'domain-1', domainName: 'example.com', status: 'VERIFIED', isActive: true, spfVerified: true, dkimVerified: true, dmarcVerified: false }]));
    }
    if (path.startsWith('/deliverability/domains/') && path.endsWith('/verify')) {
      return fulfill(route, ok({ status: 'VERIFIED' }));
    }
    if (path.startsWith('/reputation/')) {
      return fulfill(route, ok({ score: 92, lastUpdated: '2026-05-07T09:00:00Z' }));
    }
    if (path.startsWith('/dmarc/reports')) {
      return fulfill(route, ok([{ id: 'report-1', reportType: 'aggregate', receivedAt: '2026-05-07T09:00:00Z', parsedSummary: { pass: 12 } }]));
    }
    if (path === '/admin/bootstrap/status') {
      return fulfill(route, ok({
        tenantId: 'tenant-1',
        workspaceId: 'workspace-1',
        environmentId: 'local',
        status: 'COMPLETED',
        message: 'Default setup complete.',
        retryCount: 0,
        modules: { workspace: { status: 'COMPLETED' }, roles: { status: 'COMPLETED' } },
        completedAt: '2026-05-07T09:00:00Z',
      }));
    }
    if (path === '/admin/settings/validate') {
      return fulfill(route, ok({ valid: true, errors: [] }));
    }
    if (path === '/admin/settings/impact') {
      return fulfill(route, ok({ key: 'delivery.max', module: 'delivery', impactedModules: ['delivery'], notices: ['Cache invalidation required.'] }));
    }
    if (path === '/admin/settings/apply' && method === 'POST') {
      return fulfill(route, ok({ key: 'delivery.max', value: '10', configType: 'INTEGER', scope: 'WORKSPACE', updatedAt: '2026-05-07T09:00:00Z' }));
    }
    if (path.startsWith('/admin/settings')) {
      return fulfill(route, ok([{ key: 'delivery.max', value: '10', configType: 'INTEGER', scope: 'WORKSPACE', updatedAt: '2026-05-07T09:00:00Z' }]));
    }
    if (path === '/admin/branding') {
      return fulfill(route, ok({ name: 'Legent', logoUrl: '', primaryColor: '#6B21A8', secondaryColor: '#DB2777' }));
    }
    if (path === '/platform/webhooks') {
      return fulfill(route, ok([{ id: 'wh-1', name: 'Delivery events', endpointUrl: 'https://example.com/webhook', eventsSubscribed: '["delivery.failed"]', isActive: true }]));
    }
    if (path === '/admin/public-content') {
      return fulfill(route, ok([{ id: 'content-1', contentType: 'PAGE', pageKey: 'home', title: 'Home', status: 'PUBLISHED', updatedAt: '2026-05-07T09:00:00Z' }]));
    }
    if (path.startsWith('/admin/contact-requests/') && path.endsWith('/status')) {
      return fulfill(route, ok({ ...contact, status: 'CONTACTED', updatedAt: '2026-05-07T09:10:00Z' }));
    }
    if (path === '/admin/contact-requests') {
      return fulfill(route, paged([contact]));
    }

    return fulfill(route, ok([]));
  });
}

test('admin console shows operations, users, and role engine', async ({ page }) => {
  await mockAdminApis(page);
  await page.goto('/app/admin');

  await expect(page.getByRole('heading', { name: /Govern users, runtime policy/ })).toBeVisible();
  await expect(page.getByText('Module Activity Graph')).toBeVisible();

  await page.getByRole('button', { name: /Users/ }).click();
  await expect(page.getByText('User Management')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Reset' })).toBeVisible();
  await page.getByRole('button', { name: 'Reset' }).click();
  await expect(page.getByText('Reset workflow queued')).toBeVisible();

  await page.getByRole('button', { name: /Role Engine/ }).click();
  await expect(page.getByText('Permission Resolution Engine')).toBeVisible();
  await page.getByRole('button', { name: 'Seed operator role' }).click();
  await expect(page.getByText('Role created')).toBeVisible();
});

test('settings console persists preferences and supports deliverability', async ({ page }) => {
  await mockAdminApis(page);
  await page.goto('/app/settings/platform');

  await expect(page.getByRole('heading', { name: /Settings that reshape/ })).toBeVisible();
  await page.getByRole('button', { name: /^Dark$/ }).click();
  await page.getByRole('button', { name: 'Save profile' }).click();
  await expect(page.getByText('Settings saved')).toBeVisible();

  await page.getByRole('button', { name: /Deliverability/ }).click();
  await expect(page.getByText('Deliverability Settings')).toBeVisible();
  await expect(page.getByRole('button', { name: 'example.com' })).toBeVisible();
  await page.getByRole('button', { name: 'Verify' }).click();
  await expect(page.getByText('DNS verification refreshed')).toBeVisible();
});

test('admin console keeps mobile navigation usable', async ({ page }) => {
  await mockAdminApis(page);
  await page.setViewportSize({ width: 390, height: 760 });
  await page.goto('/app/admin');

  await expect(page.getByRole('heading', { name: /Govern users, runtime policy/ })).toBeVisible();
  await page.getByRole('button', { name: /Users/ }).click();
  await expect(page.getByText('User Management')).toBeVisible();
  await expect(page.locator('main.app-surface')).not.toHaveCSS('overflow-x', 'visible');
});

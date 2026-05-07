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
    if (path === '/users/preferences') {
      return fulfill(route, ok({ theme: 'light', uiMode: 'ADVANCED', density: 'comfortable', sidebarCollapsed: false }));
    }
    if (path.startsWith('/core/audit')) {
      return fulfill(route, ok([{ action: 'CONFIG_APPLY', resourceType: 'AdminSetting', resourceId: 'delivery.max', workspaceId: 'workspace-1', status: 'SUCCESS', createdAt: '2026-05-07T09:00:00Z' }]));
    }
    if (path.startsWith('/core/')) {
      return fulfill(route, ok([{ id: `${path.slice(6)}-1`, name: path.slice(6) }]));
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
    if (path === '/admin/branding' && method === 'POST') {
      return fulfill(route, ok({ name: 'Legent Pro', logoUrl: '', primaryColor: '#6B21A8', secondaryColor: '#DB2777' }));
    }
    if (path === '/admin/branding') {
      return fulfill(route, ok({ name: 'Legent', logoUrl: '', primaryColor: '#6B21A8', secondaryColor: '#DB2777' }));
    }
    if (path === '/platform/webhooks' && method === 'POST') {
      return fulfill(route, ok({ id: 'wh-1', name: 'Delivery events', endpointUrl: 'https://example.com/webhook', eventsSubscribed: '["delivery.failed"]', isActive: true }));
    }
    if (path === '/platform/webhooks') {
      return fulfill(route, ok([{ id: 'wh-1', name: 'Delivery events', endpointUrl: 'https://example.com/webhook', eventsSubscribed: '["delivery.failed"]', isActive: true }]));
    }
    if (path === '/platform/search') {
      return fulfill(route, ok([{ id: 'workspace-1', type: 'WORKSPACE', name: 'Primary workspace', description: 'Default tenant workspace' }]));
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

test('admin cockpit supports tab switching and contact status update', async ({ page }) => {
  await mockAdminApis(page);
  await page.goto('/app/admin');

  await expect(page.getByRole('heading', { name: 'Admin Studio' })).toBeVisible();
  await expect(page.getByRole('button', { name: /Runtime Config/ })).toBeVisible();

  await page.getByRole('button', { name: /Runtime Config/ }).click();
  await expect(page.getByRole('heading', { name: 'Runtime Config' })).toBeVisible();
  await page.getByPlaceholder('setting.key').fill('delivery.max');
  await page.getByPlaceholder('value').fill('10');
  await page.getByRole('button', { name: 'Validate + Impact' }).click();
  await expect(page.getByText('Cache invalidation required.')).toBeVisible();
  await page.getByRole('button', { name: 'Apply Setting' }).click();
  await expect(page.getByText('Runtime setting applied')).toBeVisible();

  await page.getByRole('button', { name: /Branding/ }).click();
  await expect(page.getByText('Live Preview')).toBeVisible();
  await page.getByRole('button', { name: 'Edit Branding' }).click();
  await page.getByLabel('Brand name').fill('Legent Pro');
  await page.getByRole('button', { name: 'Save Branding' }).click();
  await expect(page.getByText('Branding saved')).toBeVisible();

  await page.getByRole('button', { name: /Contact Requests/ }).click();
  await expect(page.getByRole('table').getByText('Analytical Labs')).toBeVisible();
  await page.getByRole('table').locator('select').selectOption('CONTACTED');
  await expect(page.getByText('Contact request updated')).toBeVisible();
});

test('admin cockpit keeps mobile tabs and content usable', async ({ page }) => {
  await mockAdminApis(page);
  await page.setViewportSize({ width: 390, height: 760 });
  await page.goto('/app/admin');

  await expect(page.getByRole('heading', { name: 'Admin Studio' })).toBeVisible();
  await page.getByRole('button', { name: /Contact Requests/ }).click();
  await expect(page.locator('.md\\:hidden').getByText('Analytical Labs').last()).toBeVisible();
  await expect(page.locator('main')).not.toHaveCSS('overflow-x', 'visible');
});

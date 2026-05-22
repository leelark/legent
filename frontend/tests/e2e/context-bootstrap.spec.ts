import { expect, test } from '@playwright/test';
import type { Route } from '@playwright/test';

function ok(data: unknown) {
  return {
    success: true,
    data,
    meta: { timestamp: new Date('2026-05-12T00:00:00Z').toISOString() },
  };
}

async function fulfill(route: Route, data: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(data),
  });
}

test('login bootstraps workspace context and opens app route', async ({ page }) => {
  const email = process.env.E2E_ADMIN_EMAIL;
  const password = process.env.E2E_ADMIN_PASSWORD;
  test.skip(!email || !password, 'E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD are required for login smoke');
  if (!email || !password) {
    return;
  }

  await page.goto('/login');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill(password);
  await Promise.all([
    page.waitForURL(/\/app\/email$/, { timeout: 45000 }),
    page.getByRole('button', { name: 'Sign in' }).click(),
  ]);

  await expect(page.getByRole('heading', { name: 'Email Studio', level: 1 })).toBeVisible({ timeout: 45000 });
  await expect(page.getByRole('navigation')).toBeVisible();
  await expect(page.getByText('Workspace required')).toHaveCount(0);
});

test('workspace route fails closed when session lacks workspace context', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('legent_user_id', 'context-user');
    localStorage.setItem('legent_roles', JSON.stringify(['ADMIN']));
    localStorage.setItem('legent_tenant_id', 'tenant-1');
    localStorage.setItem('legent_workspace_id', 'workspace-stale');
    localStorage.setItem('legent_environment_id', 'local');
  });

  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace('/api/v1', '');

    if (path === '/auth/session') {
      return fulfill(route, ok({
        status: 'success',
        userId: 'context-user',
        tenantId: 'tenant-1',
        roles: ['ADMIN'],
      }));
    }

    if (path === '/auth/contexts') {
      return fulfill(route, ok([]));
    }

    return fulfill(route, ok([]));
  });

  await page.goto('/app/email');

  await expect(page).toHaveURL(/\/login$/, { timeout: 15000 });
  await expect(page.getByRole('navigation')).toHaveCount(0);

  await expect.poll(async () => page.evaluate(() => ({
    userId: localStorage.getItem('legent_user_id'),
    roles: localStorage.getItem('legent_roles'),
    tenantId: localStorage.getItem('legent_tenant_id'),
    workspaceId: localStorage.getItem('legent_workspace_id'),
    environmentId: localStorage.getItem('legent_environment_id'),
  }))).toEqual({
    userId: null,
    roles: null,
    tenantId: null,
    workspaceId: null,
    environmentId: null,
  });
});

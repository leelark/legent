import { expect, test, type Page, type Route } from '@playwright/test';

async function waitForPublicHydration(page: Page) {
  await expect(page.locator('.public-site')).toHaveAttribute('data-hydrated', 'true', { timeout: 15000 });
}

function ok(data: unknown) {
  return {
    success: true,
    data,
    meta: { timestamp: new Date('2026-05-22T00:00:00Z').toISOString() },
  };
}

async function fulfill(route: Route, data: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(data),
  });
}

async function readLocalAuthContext(page: Page) {
  return page.evaluate(() => ({
    userId: localStorage.getItem('legent_user_id'),
    roles: localStorage.getItem('legent_roles'),
    tenantId: localStorage.getItem('legent_tenant_id'),
    workspaceId: localStorage.getItem('legent_workspace_id'),
    environmentId: localStorage.getItem('legent_environment_id'),
  }));
}

test('login page links to recovery and signup', async ({ page }) => {
  await page.goto('/login', { waitUntil: 'domcontentloaded' });
  await waitForPublicHydration(page);
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
  await Promise.all([
    page.waitForURL(/\/forgot-password$/, { timeout: 30000, waitUntil: 'domcontentloaded' }),
    page.getByRole('link', { name: 'Forgot password?' }).click(),
  ]);
  await page.goto('/login', { waitUntil: 'domcontentloaded' });
  await waitForPublicHydration(page);
  await Promise.all([
    page.waitForURL(/\/signup$/, { timeout: 30000, waitUntil: 'domcontentloaded' }),
    page.getByRole('link', { name: 'Create account' }).click(),
  ]);
});

test('login clears local auth context when workspace bootstrap fails after authentication', async ({ page }) => {
  let contextSwitchCalled = false;
  const unexpectedAuthRequests: string[] = [];

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');

    if (path === '/auth/login') {
      return fulfill(route, ok({
        status: 'success',
        userId: 'login-user',
        tenantId: 'tenant-1',
        workspaceId: 'workspace-1',
        environmentId: 'local',
        roles: ['ADMIN'],
      }));
    }

    if (path === '/auth/contexts') {
      return route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({
          success: false,
          error: { message: 'Context bootstrap unavailable.' },
        }),
      });
    }

    if (path === '/auth/context/switch') {
      contextSwitchCalled = true;
      return fulfill(route, ok({ status: 'success' }));
    }

    unexpectedAuthRequests.push(`${request.method()} ${path}`);
    return route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({ success: false, error: 'Unexpected auth request in test' }),
    });
  });

  await page.goto('/login', { waitUntil: 'domcontentloaded' });
  await waitForPublicHydration(page);
  await page.evaluate(() => {
    localStorage.setItem('legent_user_id', 'stale-user');
    localStorage.setItem('legent_roles', JSON.stringify(['ADMIN']));
    localStorage.setItem('legent_tenant_id', 'stale-tenant');
    localStorage.setItem('legent_workspace_id', 'stale-workspace');
    localStorage.setItem('legent_environment_id', 'stale-env');
  });
  await page.getByLabel('Email address').fill('operator@example.com');
  await page.getByLabel('Password').fill('CorrectHorseBatteryStaple1!');
  await page.getByRole('button', { name: 'Sign in' }).click();

  await expect(page.getByText('Context bootstrap unavailable.')).toBeVisible();
  await expect(page).toHaveURL(/\/login$/);
  expect(contextSwitchCalled).toBe(false);
  expect(unexpectedAuthRequests).toEqual([]);
  await expect.poll(async () => readLocalAuthContext(page)).toEqual({
    userId: null,
    roles: null,
    tenantId: null,
    workspaceId: null,
    environmentId: null,
  });
});

test('signup fails closed when workspace context bootstrap is invalid', async ({ page }) => {
  let contextSwitchCalled = false;
  const unexpectedAuthRequests: string[] = [];

  await page.addInitScript(() => {
    localStorage.setItem('legent_user_id', 'stale-user');
    localStorage.setItem('legent_roles', JSON.stringify(['ADMIN']));
    localStorage.setItem('legent_tenant_id', 'stale-tenant');
    localStorage.setItem('legent_workspace_id', 'stale-workspace');
    localStorage.setItem('legent_environment_id', 'stale-env');
  });

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');

    if (path === '/auth/signup') {
      return fulfill(route, ok({
        status: 'success',
        userId: 'signup-user',
        tenantId: 'tenant-1',
        workspaceId: 'workspace-1',
        environmentId: 'local',
        roles: ['ADMIN'],
      }));
    }

    if (path === '/auth/session') {
      return fulfill(route, ok({
        status: 'success',
        userId: 'signup-user',
        tenantId: 'tenant-1',
        workspaceId: null,
        environmentId: null,
        roles: ['ADMIN'],
      }));
    }

    if (path === '/auth/contexts') {
      return fulfill(route, ok([{ tenantId: 'tenant-1', workspaceId: null, environmentId: null, default: true }]));
    }

    if (path === '/auth/context/switch') {
      contextSwitchCalled = true;
      return fulfill(route, ok({ status: 'success' }));
    }

    unexpectedAuthRequests.push(`${request.method()} ${path}`);
    return route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({ success: false, error: 'Unexpected auth request in test' }),
    });
  });

  await page.goto('/signup', { waitUntil: 'domcontentloaded' });
  await waitForPublicHydration(page);
  await page.getByLabel('First name').fill('Ada');
  await page.getByLabel('Last name').fill('Lovelace');
  await page.getByLabel('Company name').fill('Analytical Labs');
  await page.getByLabel('Work email').fill('ada@example.com');
  await page.getByLabel('Password').fill('CorrectHorseBatteryStaple1!');
  await page.getByRole('button', { name: 'Create account' }).click();

  await expect(page.getByText('Workspace context setup failed. Please sign in again.')).toBeVisible();
  await expect(page).toHaveURL(/\/signup$/);
  expect(contextSwitchCalled).toBe(false);
  expect(unexpectedAuthRequests).toEqual([]);
  await expect.poll(async () => readLocalAuthContext(page)).toEqual({
    userId: null,
    roles: null,
    tenantId: null,
    workspaceId: null,
    environmentId: null,
  });
});

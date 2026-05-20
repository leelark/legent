import { expect, test, type Page, type Route } from '@playwright/test';

function ok(data: unknown) {
  return {
    success: true,
    data,
    meta: { timestamp: new Date('2026-05-20T00:00:00Z').toISOString() },
  };
}

async function fulfill(route: Route, data: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(data),
  });
}

const activities = [
  {
    id: 'activity-1',
    name: 'Nightly SQL sync',
    activityType: 'SQL_QUERY',
    status: 'ACTIVE',
  },
  {
    id: 'activity-2',
    name: 'Dormant audience extract',
    activityType: 'EXTRACT',
    status: 'DRAFT',
  },
  {
    id: 'activity-3',
    name: 'Webhook retry audit',
    activityType: 'WEBHOOK',
    status: 'ACTIVE',
  },
];

async function mockAutomationStudioApis(page: Page) {
  let dryRunPosted = false;

  await page.addInitScript(() => {
    localStorage.setItem('legent_user_id', 'automation-user');
    localStorage.setItem('legent_roles', JSON.stringify(['ADMIN']));
    localStorage.setItem('legent_tenant_id', 'tenant-1');
    localStorage.setItem('legent_workspace_id', 'workspace-1');
    localStorage.setItem('legent_environment_id', 'local');
  });

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');
    const method = request.method();

    if (path === '/auth/session') {
      return fulfill(route, ok({ status: 'success', userId: 'automation-user', tenantId: 'tenant-1', workspaceId: 'workspace-1', roles: ['ADMIN'] }));
    }
    if (path === '/auth/contexts') {
      return fulfill(route, ok([{ tenantId: 'tenant-1', workspaceId: 'workspace-1', environmentId: 'local', default: true }]));
    }
    if (path === '/users/preferences') {
      return fulfill(route, ok({ tenantId: 'tenant-1', userId: 'automation-user', theme: 'light', uiMode: 'ADVANCED', density: 'comfortable', metadata: {} }));
    }
    if (path === '/workflows') {
      return fulfill(route, ok([]));
    }
    if (path === '/automation-studio/activities') {
      return fulfill(route, ok(activities));
    }
    if (path === '/automation-studio/activities/activity-1/runs' && method === 'POST') {
      dryRunPosted = true;
      return fulfill(route, ok({
        id: 'run-after-dry-run',
        activityId: 'activity-1',
        status: 'SUCCEEDED',
        dryRun: true,
        triggerSource: 'MANUAL',
        rowsRead: 9,
        rowsWritten: 8,
        startedAt: '2026-05-20T08:30:00Z',
        completedAt: '2026-05-20T08:31:00Z',
      }));
    }
    if (path === '/automation-studio/activities/activity-1/runs') {
      return fulfill(route, ok(dryRunPosted ? [
        {
          id: 'run-after-dry-run',
          activityId: 'activity-1',
          status: 'SUCCEEDED',
          dryRun: true,
          triggerSource: 'MANUAL',
          rowsRead: 9,
          rowsWritten: 8,
          startedAt: '2026-05-20T08:30:00Z',
          completedAt: '2026-05-20T08:31:00Z',
        },
      ] : [
        {
          id: 'run-1',
          activityId: 'activity-1',
          status: 'SUCCEEDED',
          dryRun: true,
          triggerSource: 'MANUAL',
          rowsRead: 120,
          rowsWritten: 118,
          startedAt: '2026-05-20T07:10:00Z',
          completedAt: '2026-05-20T07:11:00Z',
        },
        {
          id: 'run-2',
          activityId: 'activity-1',
          status: 'FAILED',
          dryRun: false,
          triggerSource: 'SCHEDULED',
          rowsRead: 40,
          rowsWritten: 0,
          errorMessage: 'SMTP connection failed',
          startedAt: '2026-05-19T22:00:00Z',
          completedAt: '2026-05-19T22:01:00Z',
        },
      ]));
    }
    if (path === '/automation-studio/activities/activity-2/runs') {
      return fulfill(route, ok([]));
    }
    if (path === '/automation-studio/activities/activity-3/runs') {
      return fulfill(route, {
        success: false,
        error: { message: 'Run history unavailable' },
      }, 500);
    }
    return fulfill(route, ok([]));
  });
}

test('automation studio shows recent activity runs and refreshes after dry run', async ({ page }) => {
  await mockAutomationStudioApis(page);

  await page.goto('/app/automation');
  await expect(page.getByText('Automation Studio Activities')).toBeVisible();

  await page.getByRole('button', { name: 'Show run history for Nightly SQL sync' }).click();

  const runList = page.getByRole('list', { name: 'Recent runs for Nightly SQL sync' });
  await expect(runList).toBeVisible();
  await expect(runList).toContainText('SUCCEEDED');
  await expect(runList).toContainText('Dry run');
  await expect(runList).toContainText('Rows: 120 read, 118 written');
  await expect(runList).toContainText('FAILED');
  await expect(runList).toContainText('Live');
  await expect(runList).toContainText('SMTP connection failed');

  await page.getByRole('button', { name: 'Dry Run' }).first().click();

  await expect(runList).toContainText('Rows: 9 read, 8 written');
  await expect(runList).not.toContainText('SMTP connection failed');
});

test('automation studio scopes empty and error run-history states to each activity', async ({ page }) => {
  await mockAutomationStudioApis(page);

  await page.goto('/app/automation');
  await page.getByRole('button', { name: 'Show run history for Dormant audience extract' }).click();
  await expect(page.getByText('No recent runs recorded.')).toBeVisible();

  await page.getByRole('button', { name: 'Show run history for Webhook retry audit' }).click();
  await expect(page.getByText('Run history unavailable')).toBeVisible();
  await expect(page.getByText('No recent runs recorded.')).toBeVisible();
});

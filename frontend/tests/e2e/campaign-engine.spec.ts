import { expect, test, type Page, type Route } from '@playwright/test';

function ok(data: unknown) {
  return {
    success: true,
    data,
    meta: { timestamp: new Date('2026-05-08T04:00:00Z').toISOString() },
  };
}

async function fulfill(route: Route, data: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(data),
  });
}

async function mockCampaignApis(page: Page, seen: Record<string, unknown> = {}) {
  await page.addInitScript(() => {
    localStorage.setItem('legent_user_id', 'campaign-user');
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
      return fulfill(route, ok({ status: 'success', userId: 'campaign-user', tenantId: 'tenant-1', workspaceId: 'workspace-1', roles: ['ADMIN'] }));
    }
    if (path === '/auth/contexts') {
      return fulfill(route, ok([{ tenantId: 'tenant-1', workspaceId: 'workspace-1', environmentId: 'local', default: true }]));
    }
    if (path === '/users/preferences') {
      return fulfill(route, ok({ tenantId: 'tenant-1', userId: 'campaign-user', theme: 'light', uiMode: 'ADVANCED', density: 'comfortable', metadata: {} }));
    }
    if (path.startsWith('/templates')) {
      return fulfill(route, ok({ content: [{ id: 'tpl-1', name: 'Launch Template', subject: 'Launch day' }] }));
    }
    if (path.startsWith('/lists')) {
      return fulfill(route, ok({ content: [{ id: 'list-1', name: 'Customers' }] }));
    }
    if (path.startsWith('/segments')) {
      return fulfill(route, ok({ content: [{ id: 'seg-1', name: 'Engaged' }] }));
    }
    if (path === '/campaigns' && method === 'POST') {
      seen.campaign = JSON.parse(request.postData() || '{}');
      return fulfill(route, ok({
        id: 'camp-1',
        name: 'Spring Launch',
        subject: 'Launch day',
        status: 'DRAFT',
        templateId: 'tpl-1',
        audiences: [{ audienceType: 'LIST', audienceId: 'list-1', action: 'INCLUDE' }],
      }));
    }
    if (path === '/campaigns/camp-1') {
      return fulfill(route, ok({
        id: 'camp-1',
        name: 'Spring Launch',
        subject: 'Launch day',
        status: 'APPROVED',
        templateId: 'tpl-1',
      }));
    }
    if (path === '/campaigns/camp-1/budget' && method === 'PUT') {
      seen.budget = JSON.parse(request.postData() || '{}');
      return fulfill(route, ok({
        id: 'budget-1',
        campaignId: 'camp-1',
        currency: 'USD',
        budgetLimit: 100,
        costPerSend: 0.01,
        reservedSpend: 0,
        actualSpend: 0,
        enforced: true,
        status: 'OPEN',
      }));
    }
    if (path === '/campaigns/camp-1/budget') {
      return fulfill(route, ok({
        id: 'budget-1',
        campaignId: 'camp-1',
        currency: 'USD',
        budgetLimit: 100,
        costPerSend: 0.01,
        reservedSpend: 10,
        actualSpend: 25,
        enforced: true,
        status: 'OPEN',
      }));
    }
    if (path === '/campaigns/camp-1/frequency-policy' && method === 'PUT') {
      seen.frequency = JSON.parse(request.postData() || '{}');
      return fulfill(route, ok({ id: 'freq-1', campaignId: 'camp-1', enabled: true, maxSends: 3, windowHours: 48, includeJourneys: true }));
    }
    if (path === '/campaigns/camp-1/frequency-policy') {
      return fulfill(route, ok({ id: 'freq-1', campaignId: 'camp-1', enabled: true, maxSends: 3, windowHours: 48, includeJourneys: true }));
    }
    if (path === '/campaigns/camp-1/experiments' && method === 'POST') {
      seen.experiment = JSON.parse(request.postData() || '{}');
      return fulfill(route, ok({
        id: 'exp-1',
        campaignId: 'camp-1',
        name: 'Spring Launch Experiment',
        experimentType: 'AB',
        status: 'ACTIVE',
        winnerMetric: 'CLICKS',
        autoPromotion: true,
        minRecipientsPerVariant: 100,
        evaluationWindowHours: 24,
        holdoutPercentage: 5,
        variants: [
          { id: 'var-a', variantKey: 'A', name: 'Control', weight: 50, active: true },
          { id: 'var-b', variantKey: 'B', name: 'Variant B', weight: 50, active: true },
        ],
      }));
    }
    if (path === '/campaigns/camp-1/experiments') {
      return fulfill(route, ok([{
        id: 'exp-1',
        campaignId: 'camp-1',
        name: 'Spring Launch Experiment',
        experimentType: 'AB',
        status: 'ACTIVE',
        winnerMetric: 'CLICKS',
        autoPromotion: true,
        minRecipientsPerVariant: 100,
        evaluationWindowHours: 24,
        holdoutPercentage: 5,
        variants: [
          { id: 'var-a', variantKey: 'A', name: 'Control', weight: 50, active: true },
          { id: 'var-b', variantKey: 'B', name: 'Variant B', weight: 50, active: true },
        ],
      }]));
    }
    if (path === '/campaigns/camp-1/experiments/exp-1/metrics') {
      return fulfill(route, ok([
        { campaignId: 'camp-1', experimentId: 'exp-1', variantId: 'var-a', targetCount: 100, sentCount: 98, failedCount: 2, openCount: 40, clickCount: 12, conversionCount: 3, revenue: 250, customMetricCount: 0, score: 0.1224 },
        { campaignId: 'camp-1', experimentId: 'exp-1', variantId: 'var-b', targetCount: 100, sentCount: 99, failedCount: 1, openCount: 42, clickCount: 18, conversionCount: 5, revenue: 400, customMetricCount: 0, score: 0.1818 },
      ]));
    }
    if (path === '/campaigns/camp-1/jobs') {
      return fulfill(route, ok({ content: [{ id: 'job-1', campaignId: 'camp-1', status: 'COMPLETED', totalTarget: 200, totalSent: 197, totalFailed: 3, totalSuppressed: 8, triggerSource: 'MANUAL', createdAt: '2026-05-08T04:00:00Z', updatedAt: '2026-05-08T04:05:00Z' }] }));
    }
    if (path === '/campaigns/camp-1/send/preflight') {
      return fulfill(route, ok({ campaignId: 'camp-1', sendAllowed: true, errors: [], warnings: [], checks: { experiments: 1, budgetEnforced: true, frequencyEnabled: true } }));
    }
    if (path === '/send-jobs/job-1/dead-letters') {
      return fulfill(route, ok([{ id: 'dlq-1', campaignId: 'camp-1', jobId: 'job-1', batchId: 'batch-1', email: 'bad@example.com', reason: 'PROVIDER_TIMEOUT', retryCount: 1, status: 'OPEN', createdAt: '2026-05-08T04:02:00Z' }]));
    }

    return fulfill(route, ok([]));
  });
}

test('campaign wizard saves experiment, budget, and frequency policy', async ({ page }) => {
  const seen: Record<string, unknown> = {};
  await mockCampaignApis(page, seen);
  await page.goto('/app/campaigns/new');

  await expect(page.getByRole('heading', { name: 'Campaign Wizard' })).toBeVisible();
  await page.getByLabel('Campaign Name *').fill('Spring Launch');
  await page.getByLabel('Subject Line *').fill('Launch day');
  await page.getByLabel('Template').selectOption('tpl-1');
  await page.getByRole('button', { name: 'Next', exact: true }).click();

  await page.getByLabel('Audience').selectOption('list-1');
  await page.getByRole('button', { name: 'Include' }).click();
  await page.getByRole('button', { name: 'Next', exact: true }).click();

  await page.getByLabel('Frequency Cap').fill('3');
  await page.getByLabel('Budget Limit').fill('100');
  await page.getByLabel('Cost Per Send').fill('0.01');
  await page.getByLabel('Enforce budget').check();
  await page.getByLabel('Window Hours').first().fill('48');
  await page.getByLabel('Enable', { exact: true }).check();
  await page.getByLabel('Holdout %').fill('5');
  await page.getByLabel('Auto-promote winner').check();
  await page.getByRole('button', { name: 'Next', exact: true }).click();

  await expect(page.getByText('AB by CLICKS')).toBeVisible();
  await page.getByRole('button', { name: 'Save Draft' }).click();
  await expect(page.getByText('Draft saved')).toBeVisible();

  expect(seen.campaign).toMatchObject({ name: 'Spring Launch', templateId: 'tpl-1' });
  expect(seen.budget).toMatchObject({ enforced: true, budgetLimit: 100, costPerSend: 0.01 });
  expect(seen.frequency).toMatchObject({ enabled: true, maxSends: 3, windowHours: 48 });
  expect(seen.experiment).toMatchObject({ experimentType: 'AB', winnerMetric: 'CLICKS', holdoutPercentage: 5, status: 'ACTIVE' });
});

test('campaign tracking exposes safety, DLQ, budget, and variant analytics tabs', async ({ page }) => {
  await mockCampaignApis(page);
  await page.goto('/app/campaigns/camp-1/tracking');

  await expect(page.getByRole('heading', { name: 'Spring Launch' })).toBeVisible();
  await expect(page.getByText('SEND ALLOWED')).toBeVisible();

  await page.getByRole('button', { name: 'Experiments' }).click();
  await expect(page.getByText('Spring Launch Experiment')).toBeVisible();
  await expect(page.getByText('A: 50%')).toBeVisible();

  await page.getByRole('button', { name: 'Budget' }).click();
  await expect(page.getByText('Budget Ledger')).toBeVisible();
  await expect(page.getByText('Cost/Send')).toBeVisible();

  await page.getByRole('button', { name: 'DLQ' }).click();
  await expect(page.getByText('PROVIDER_TIMEOUT')).toBeVisible();

  await page.getByRole('button', { name: 'Variant Analytics' }).click();
  await expect(page.getByText('var-b')).toBeVisible();
  await expect(page.getByText('0.1818')).toBeVisible();
});

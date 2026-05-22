import { expect, test } from '@playwright/test';
import { mockWorkspaceApis } from './support/workspace-mock';

type ImportFixture = {
  id: string;
  fileName: string;
  status: string;
  progressPercent: number;
  totalRows: number;
  processedRows: number;
  successRows: number;
  errorRows: number;
  targetType: string;
};

function apiResponse(data: unknown, pagination?: Record<string, unknown>) {
  return JSON.stringify({
    success: true,
    data,
    ...(pagination ? { pagination } : {}),
  });
}

function importJob(overrides: Partial<ImportFixture> = {}): ImportFixture {
  return {
    id: 'import-1',
    fileName: 'subscribers.csv',
    status: 'PENDING',
    progressPercent: 0,
    totalRows: 5,
    processedRows: 0,
    successRows: 0,
    errorRows: 0,
    targetType: 'SUBSCRIBER',
    ...overrides,
  };
}

async function uploadCsv(page: import('@playwright/test').Page, contents = 'email,firstName\njane@example.com,Jane') {
  await page.locator('input[type="file"]').setInputFiles({
    name: 'subscribers.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(contents),
  });
}

test('validates required email mapping before starting an import', async ({ page }) => {
  await mockWorkspaceApis(page);
  await page.goto('/app/audience/imports/new');

  await uploadCsv(page, 'firstName,lastName\nJane,Doe');
  await page.getByRole('button', { name: 'Next' }).click();

  await expect(page.getByText('Missing Required Fields')).toBeVisible();
  await expect(page.getByText('You must map Email.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Start Import' })).toBeDisabled();
});

test('polls active import statuses until completion', async ({ page }) => {
  let detailCalls = 0;

  await mockWorkspaceApis(page);
  await page.route('**/api/v1/imports**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');

    if (request.method() === 'POST' && path === '/imports') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: apiResponse(importJob({ status: 'VALIDATING', progressPercent: 15, processedRows: 1 })),
      });
    }

    if (request.method() === 'GET' && path === '/imports/import-1') {
      detailCalls += 1;
      const status = detailCalls === 1
        ? importJob({ status: 'PROCESSING', progressPercent: 60, processedRows: 3, successRows: 2 })
        : importJob({ status: 'COMPLETED', progressPercent: 100, processedRows: 5, successRows: 5 });
      return route.fulfill({ status: 200, contentType: 'application/json', body: apiResponse(status) });
    }

    return route.fallback();
  });

  await page.goto('/app/audience/imports/new');
  await uploadCsv(page);
  await page.getByRole('button', { name: 'Next' }).click();
  await page.getByRole('button', { name: 'Start Import' }).click();

  await expect(page.getByText(/VALIDATING: processed 1 of 5 rows/)).toBeVisible();
  await expect(page.getByText('Import Completed', { exact: true })).toBeVisible({ timeout: 7000 });
  await expect(page.getByText('Successfully imported 5 rows.')).toBeVisible();
});

for (const scenario of [
  {
    status: 'COMPLETED_WITH_ERRORS',
    title: 'Import Completed with Errors',
    body: '3 successful, 2 failed.',
  },
  {
    status: 'FAILED',
    title: 'Import Failed',
    body: '2 failed rows. No further polling will run.',
  },
  {
    status: 'CANCELLED',
    title: 'Import Cancelled',
    body: 'Processed 2 of 5 rows before cancellation.',
  },
]) {
  test(`renders terminal wizard state for ${scenario.status}`, async ({ page }) => {
    await mockWorkspaceApis(page);
    await page.route('**/api/v1/imports**', async (route) => {
      const request = route.request();
      const url = new URL(request.url());
      const path = url.pathname.replace('/api/v1', '');

      if (request.method() === 'POST' && path === '/imports') {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: apiResponse(importJob({
            status: scenario.status,
            progressPercent: scenario.status === 'COMPLETED_WITH_ERRORS' ? 100 : 40,
            processedRows: scenario.status === 'CANCELLED' ? 2 : 5,
            successRows: scenario.status === 'COMPLETED_WITH_ERRORS' ? 3 : 0,
            errorRows: scenario.status === 'COMPLETED_WITH_ERRORS' || scenario.status === 'FAILED' ? 2 : 0,
          })),
        });
      }

      return route.fallback();
    });

    await page.goto('/app/audience/imports/new');
    await uploadCsv(page);
    await page.getByRole('button', { name: 'Next' }).click();
    await page.getByRole('button', { name: 'Start Import' }).click();

    await expect(page.getByText(scenario.title, { exact: true })).toBeVisible();
    await expect(page.getByText(scenario.body)).toBeVisible();
  });
}

test('shows backend status badges and polls validating detail views', async ({ page }) => {
  let detailCalls = 0;
  const imports = [
    importJob({ id: 'validating-import', status: 'VALIDATING', progressPercent: 20, processedRows: 1 }),
    importJob({ id: 'processing-import', status: 'PROCESSING', progressPercent: 55, processedRows: 3 }),
    importJob({ id: 'errors-import', status: 'COMPLETED_WITH_ERRORS', progressPercent: 100, processedRows: 5, successRows: 3, errorRows: 2 }),
    importJob({ id: 'failed-import', status: 'FAILED', progressPercent: 100, processedRows: 5, errorRows: 5 }),
    importJob({ id: 'cancelled-import', status: 'CANCELLED', progressPercent: 40, processedRows: 2 }),
  ];

  await mockWorkspaceApis(page);
  await page.route('**/api/v1/imports**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');

    if (request.method() === 'GET' && path === '/imports') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: apiResponse(imports, {
          page: 0,
          size: 50,
          totalElements: imports.length,
          totalPages: 1,
        }),
      });
    }

    if (request.method() === 'GET' && path === '/imports/validating-import') {
      detailCalls += 1;
      const detail = detailCalls === 1
        ? imports[0]
        : importJob({ id: 'validating-import', status: 'COMPLETED', progressPercent: 100, processedRows: 5, successRows: 5 });
      return route.fulfill({ status: 200, contentType: 'application/json', body: apiResponse(detail) });
    }

    return route.fallback();
  });

  await page.goto('/app/audience/imports');

  for (const status of ['VALIDATING', 'PROCESSING', 'COMPLETED_WITH_ERRORS', 'FAILED', 'CANCELLED']) {
    await expect(page.getByText(status, { exact: true })).toBeVisible();
  }

  await page.goto('/app/audience/imports/validating-import');
  await expect(page.getByText('VALIDATING', { exact: true })).toBeVisible();
  await expect(page.getByText('COMPLETED', { exact: true })).toBeVisible({ timeout: 7000 });
});

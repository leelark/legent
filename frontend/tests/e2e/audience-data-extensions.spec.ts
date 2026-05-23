import { expect, test } from '@playwright/test';
import { mockWorkspaceApis } from './support/workspace-mock';

type GovernancePayload = {
  sourceType?: string;
  sourceSystem?: string;
  sourceReference?: string;
  dataClassification?: string;
  governanceNotes?: string;
};

type DataExtensionPayload = {
  name?: string;
  governance?: GovernancePayload;
  fields?: Array<{ fieldName?: string; dataClassification?: string }>;
};

function apiResponse(data: unknown, pagination?: Record<string, unknown>) {
  return JSON.stringify({
    success: true,
    data,
    ...(pagination ? { pagination } : {}),
  });
}

test('manages data extension governance metadata and audit', async ({ page }) => {
  let createPayload: DataExtensionPayload | null = null;
  let updatePayload: GovernancePayload | null = null;
  let deleteCount = 0;
  const nativeDialogs: string[] = [];

  const extension = {
    id: 'de-1',
    name: 'Orders',
    description: 'Commerce order facts',
    sendable: true,
    sendableField: 'email',
    primaryKeyField: 'subscriberKey',
    recordCount: 42,
    governance: {
      sourceType: 'IMPORT',
      sourceSystem: 'ERP',
      sourceReference: 'nightly-orders',
      dataClassification: 'CONFIDENTIAL',
      governanceNotes: 'Finance-owned import.',
      reviewedBy: 'steward-1',
      reviewedAt: '2026-05-20T08:00:00Z',
    },
  };

  await mockWorkspaceApis(page);
  page.on('dialog', async (dialog) => {
    nativeDialogs.push(dialog.message());
    await dialog.dismiss();
  });

  await page.route('**/api/v1/data-extensions**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');

    if (request.method() === 'GET' && path === '/data-extensions') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: apiResponse([extension], {
          page: 0,
          size: 100,
          totalElements: 1,
          totalPages: 1,
        }),
      });
    }

    if (request.method() === 'GET' && path === '/data-extensions/de-1/governance-audit') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: apiResponse([
          {
            id: 'audit-1',
            action: 'GOVERNANCE_UPDATED',
            summary: 'Created with confirmed provenance.',
            createdAt: '2026-05-20T09:00:00Z',
            createdBy: 'steward-1',
          },
        ]),
      });
    }

    if (request.method() === 'POST' && path === '/data-extensions') {
      createPayload = request.postDataJSON() as DataExtensionPayload;
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: apiResponse({ id: 'de-2', ...createPayload }),
      });
    }

    if (request.method() === 'PUT' && path === '/data-extensions/de-1/governance') {
      updatePayload = request.postDataJSON() as GovernancePayload;
      extension.governance = { ...extension.governance, ...updatePayload };
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: apiResponse(extension),
      });
    }

    if (request.method() === 'DELETE' && path === '/data-extensions/de-1') {
      deleteCount += 1;
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: apiResponse({ deleted: true }),
      });
    }

    return route.fallback();
  });

  await page.goto('/app/audience/data-extensions');

  await expect(page.getByText('Orders')).toBeVisible();
  await expect(page.getByText('CONFIDENTIAL')).toBeVisible();
  await expect(page.getByText('IMPORT · ERP')).toBeVisible();

  await page.getByRole('button', { name: 'Create Data Extension' }).click();
  await page.getByLabel('Name *').fill('VIP Contacts');
  await page.getByLabel('Governance source type').selectOption('API');
  await page.getByLabel('Data classification').selectOption('RESTRICTED');
  await page.getByLabel('Source system').fill('CRM');
  await page.getByLabel('Source reference').fill('vip-segment');
  await page.getByLabel('Governance notes').fill('Contains reviewed loyalty profile data.');
  await page.getByRole('button', { name: 'Save' }).click();

  await expect.poll(() => createPayload?.governance?.sourceType).toBe('API');
  await expect.poll(() => createPayload?.governance?.sourceSystem).toBe('CRM');
  await expect.poll(() => createPayload?.governance?.sourceReference).toBe('vip-segment');
  await expect.poll(() => createPayload?.governance?.dataClassification).toBe('RESTRICTED');
  await expect.poll(() => createPayload?.fields?.[1]?.dataClassification).toBe('CONFIDENTIAL');

  await page.getByRole('button', { name: 'Manage governance for Orders' }).click();
  await expect(page.getByText('Created with confirmed provenance.')).toBeVisible();
  await expect(page.getByText('Finance-owned import.')).toBeVisible();

  await page.getByLabel('Data classification').selectOption('INTERNAL');
  await page.getByLabel('Governance notes').fill('Reviewed by lifecycle data steward.');
  await page.getByRole('button', { name: 'Save Governance' }).click();

  await expect.poll(() => updatePayload?.dataClassification).toBe('INTERNAL');
  await expect.poll(() => updatePayload?.governanceNotes).toBe('Reviewed by lifecycle data steward.');

  await page.getByRole('dialog', { name: 'Governance: Orders' }).locator('button').filter({ hasText: 'Close' }).click();
  await page.getByRole('button', { name: 'Delete Orders' }).click();

  const deleteDialog = page.getByRole('dialog', { name: 'Delete data extension?' });
  await expect(deleteDialog).toBeVisible();
  await expect(page.getByText('Delete "Orders"?')).toBeVisible();
  expect(nativeDialogs).toEqual([]);

  await deleteDialog.getByRole('button', { name: 'Cancel' }).click();
  await expect(deleteDialog).toBeHidden();
  expect(deleteCount).toBe(0);

  await page.getByRole('button', { name: 'Delete Orders' }).click();
  await deleteDialog.getByRole('button', { name: 'Delete' }).click();

  await expect.poll(() => deleteCount).toBe(1);
  await expect(page.getByText('Data extension deleted')).toBeVisible();
  expect(nativeDialogs).toEqual([]);
});

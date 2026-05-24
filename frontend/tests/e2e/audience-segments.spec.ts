import { expect, test, type Locator } from '@playwright/test';
import { mockWorkspaceApis } from './support/workspace-mock';

async function operatorValues(operator: Locator) {
  return operator.locator('option').evaluateAll((options) =>
    options.map((option) => (option as HTMLOptionElement).value)
  );
}

type SegmentPayload = {
  rules?: {
    groups?: Array<{
      conditions?: Array<{
        field?: string;
        op?: string;
        value?: string;
      }>;
    }>;
  };
};

test('filters segment rule operators by selected field type', async ({ page }) => {
  let postedPayload: SegmentPayload | null = null;

  await mockWorkspaceApis(page);
  await page.route('**/api/v1/segments', async (route) => {
    if (route.request().method() !== 'POST') {
      return route.fallback();
    }
    postedPayload = route.request().postDataJSON() as SegmentPayload;
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: { id: 'segment-1', ...postedPayload },
      }),
    });
  });

  await page.goto('/app/audience/segments/new');
  await page.getByLabel('Name *').fill('List membership segment');

  const governance = page.getByTestId('segment-governance-panel');
  await expect(governance).toBeVisible();
  await expect(governance).toContainText('ADVANCED');
  await expect(page.getByTestId('segment-advanced-governance')).toBeVisible();
  await expect(page.getByTestId('segment-governance-classification')).toHaveText('CONFIDENTIAL');
  await expect(page.getByTestId('segment-governance-warning').filter({ hasText: 'PII review' })).toBeVisible();
  await expect(page.getByTestId('segment-governance-lock').filter({ hasText: 'Tenant/workspace scope' })).toBeVisible();

  const condition = page.getByTestId('segment-rule-condition').first();
  const field = condition.getByLabel('Rule field');
  const operator = condition.getByLabel('Rule operator');

  await expect.poll(() => operatorValues(operator)).toEqual([
    'EQUALS',
    'NOT_EQUALS',
    'CONTAINS',
    'STARTS_WITH',
    'ENDS_WITH',
    'IS_NULL',
    'IS_NOT_NULL',
  ]);

  await field.selectOption('list_membership');
  await expect(operator).toHaveValue('IN_LIST');
  await expect.poll(() => operatorValues(operator)).toEqual(['IN_LIST', 'NOT_IN_LIST']);
  await expect(page.getByTestId('segment-governance-classification')).toHaveText('INTERNAL');
  await expect(page.getByTestId('segment-governance-warning').filter({ hasText: 'PII review' })).toHaveCount(0);

  await operator.selectOption('NOT_IN_LIST');
  await field.selectOption('email');
  await expect(operator).toHaveValue('EQUALS');
  await expect.poll(() => operatorValues(operator)).toEqual([
    'EQUALS',
    'NOT_EQUALS',
    'CONTAINS',
    'STARTS_WITH',
    'ENDS_WITH',
    'IS_NULL',
    'IS_NOT_NULL',
  ]);

  await field.selectOption('list_membership');
  await condition.getByLabel('Rule value').fill('list-123');
  await expect(governance).toContainText('Clear');
  await page.getByRole('button', { name: 'Save' }).click();

  await expect.poll(() => postedPayload?.rules?.groups?.[0]?.conditions?.[0]?.field).toBe('list_membership');
  await expect.poll(() => postedPayload?.rules?.groups?.[0]?.conditions?.[0]?.op).toBe('IN_LIST');
  await expect.poll(() => postedPayload?.rules?.groups?.[0]?.conditions?.[0]?.value).toBe('list-123');
});

test('keeps segment governance compact in basic mode', async ({ page }) => {
  await mockWorkspaceApis(page, { uiMode: 'BASIC' });

  await page.goto('/app/audience/segments/new');

  const governance = page.getByTestId('segment-governance-panel');
  await expect(governance).toBeVisible();
  await expect(governance).toContainText('BASIC');
  await expect(governance).toContainText('CONFIDENTIAL');
  await expect(page.getByTestId('segment-advanced-governance')).toHaveCount(0);
  await expect(page.getByTestId('segment-draft-only-families')).toHaveCount(0);
});

test('deletes a segment with app modal and no native dialog', async ({ page }) => {
  const nativeDialogs: string[] = [];
  let deletedSegmentId: string | null = null;

  page.on('dialog', async (dialog) => {
    nativeDialogs.push(dialog.message());
    await dialog.dismiss();
  });

  await mockWorkspaceApis(page);
  await page.route('**/api/v1/segments**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');

    if (request.method() === 'GET' && path === '/segments') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: [
            {
              id: 'segment-1',
              name: 'Dormant subscribers',
              status: 'ACTIVE',
              memberCount: 42,
              createdAt: '2026-05-01T00:00:00Z',
            },
          ],
          pagination: {
            page: 0,
            size: 50,
            totalElements: 1,
            totalPages: 1,
          },
        }),
      });
    }

    if (request.method() === 'DELETE' && path === '/segments/segment-1') {
      deletedSegmentId = 'segment-1';
      return route.fulfill({
        status: 204,
        body: '',
      });
    }

    return route.fallback();
  });

  await page.goto('/app/audience/segments');
  await page.getByRole('button', { name: 'Delete Dormant subscribers' }).click();

  const dialog = page.getByRole('dialog', { name: 'Delete segment?' });
  await expect(dialog).toBeVisible();
  await expect(dialog).toContainText('Dormant subscribers');
  await expect(nativeDialogs).toEqual([]);

  await dialog.getByRole('button', { name: 'Delete' }).click();

  await expect.poll(() => deletedSegmentId).toBe('segment-1');
  await expect(page.getByText('Segment deleted')).toBeVisible();
  await expect(nativeDialogs).toEqual([]);
});

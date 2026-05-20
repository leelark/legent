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
  await page.getByRole('button', { name: 'Save' }).click();

  await expect.poll(() => postedPayload?.rules?.groups?.[0]?.conditions?.[0]?.field).toBe('list_membership');
  await expect.poll(() => postedPayload?.rules?.groups?.[0]?.conditions?.[0]?.op).toBe('IN_LIST');
  await expect.poll(() => postedPayload?.rules?.groups?.[0]?.conditions?.[0]?.value).toBe('list-123');
});

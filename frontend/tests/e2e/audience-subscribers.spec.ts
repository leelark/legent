import { expect, test } from '@playwright/test';
import { mockWorkspaceApis } from './support/workspace-mock';

function apiResponse(data: unknown, pagination?: Record<string, unknown>) {
  return JSON.stringify({
    success: true,
    data,
    ...(pagination ? { pagination } : {}),
  });
}

const subscribers = [
  {
    id: 'sub-1',
    email: 'alice@example.com',
    subscriberKey: 'alice',
    firstName: 'Alice',
    lastName: 'A',
    status: 'ACTIVE',
    source: 'CSV',
    createdAt: '2026-05-01T00:00:00Z',
  },
  {
    id: 'sub-2',
    email: 'bob@example.com',
    subscriberKey: 'bob',
    firstName: 'Bob',
    lastName: 'B',
    status: 'UNSUBSCRIBED',
    source: 'FORM',
    createdAt: '2026-05-02T00:00:00Z',
  },
];

test('encodes subscriber search and uses bulk-action endpoint for selected deletes', async ({ page }) => {
  let latestListUrl: URL | undefined;
  let bulkPayload: unknown;
  let singleDeleteCalls = 0;

  page.on('dialog', async (dialog) => dialog.accept());

  await mockWorkspaceApis(page);
  await page.route('**/api/v1/subscribers**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');

    if (request.method() === 'GET' && path === '/subscribers') {
      latestListUrl = url;
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: apiResponse(subscribers, {
          page: 0,
          size: 20,
          totalElements: subscribers.length,
          totalPages: 1,
        }),
      });
    }

    if (request.method() === 'POST' && path === '/subscribers/bulk-actions') {
      bulkPayload = request.postDataJSON();
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: apiResponse(2),
      });
    }

    if (request.method() === 'DELETE' && path.startsWith('/subscribers/')) {
      singleDeleteCalls += 1;
      return route.fulfill({ status: 204 });
    }

    return route.fallback();
  });

  await page.goto('/app/audience/subscribers');
  await expect(page.getByText('alice@example.com')).toBeVisible();

  await page.getByPlaceholder('Search by email, name, or subscriber key...').fill('alice&status=BOUNCED');
  await expect.poll(() => latestListUrl?.searchParams.get('search')).toBe('alice&status=BOUNCED');
  expect(latestListUrl?.searchParams.get('status')).toBeNull();

  await page.getByLabel('Select all rows').check();
  await page.getByRole('button', { name: 'Delete Selected' }).click();

  await expect.poll(() => bulkPayload).toEqual({
    action: 'DELETE',
    subscriberIds: ['sub-1', 'sub-2'],
  });
  expect(singleDeleteCalls).toBe(0);
});

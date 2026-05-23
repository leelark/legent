import { expect, test } from '@playwright/test';
import { mockWorkspaceApis } from './support/workspace-mock';

function apiResponse(data: unknown, pagination?: Record<string, unknown>) {
  return JSON.stringify({
    success: true,
    data,
    ...(pagination ? { pagination } : {}),
  });
}

test('deletes audience lists through app confirmation modal', async ({ page }) => {
  let deletedPath = '';
  const dialogs: string[] = [];

  page.on('dialog', async (dialog) => {
    dialogs.push(dialog.message());
    await dialog.dismiss();
  });

  await mockWorkspaceApis(page);
  await page.route('**/api/v1/lists**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');

    if (request.method() === 'GET' && path === '/lists') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: apiResponse([
          {
            id: 'list-1',
            name: 'Weekly Newsletter',
            listType: 'PUBLICATION',
            memberCount: 42,
            status: 'ACTIVE',
          },
        ], {
          page: 0,
          size: 100,
          totalElements: 1,
          totalPages: 1,
        }),
      });
    }

    if (request.method() === 'DELETE' && path === '/lists/list-1') {
      deletedPath = path;
      return route.fulfill({ status: 204 });
    }

    return route.fallback();
  });

  await page.goto('/app/audience/lists');
  await expect(page.getByText('Weekly Newsletter')).toBeVisible();

  await page.getByRole('button', { name: 'Delete Weekly Newsletter' }).click();
  const deleteDialog = page.getByRole('dialog', { name: 'Delete list?' });
  await expect(deleteDialog).toBeVisible();
  await expect(page.getByText('Delete "Weekly Newsletter"?')).toBeVisible();
  await deleteDialog.getByRole('button', { name: 'Delete' }).click();

  await expect.poll(() => deletedPath).toBe('/lists/list-1');
  await expect(page.getByText('List deleted')).toBeVisible();
  expect(dialogs).toEqual([]);
});

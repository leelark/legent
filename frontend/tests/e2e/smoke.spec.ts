import { expect, test } from '@playwright/test';

test('release smoke: health endpoint and public shell render', async ({ page, request }) => {
  const health = await request.get('/api/health');
  expect(health.ok()).toBeTruthy();

  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await expect(page.locator('.public-site')).toHaveAttribute('data-hydrated', 'true', { timeout: 15_000 });
  await expect(page.getByRole('heading', { name: /Run lifecycle email/i })).toBeVisible();
});

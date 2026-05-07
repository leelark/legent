import { expect, test } from '@playwright/test';

test('marketing navigation and auth entry points', async ({ page }) => {
  await page.goto('/');
  const featuresLink = page.getByRole('link', { name: 'Features', exact: true }).first();
  await expect(featuresLink).toBeVisible();
  await Promise.all([
    page.waitForURL(/\/features$/, { timeout: 15000 }),
    featuresLink.click(),
  ]);
  const startFreeLink = page.getByRole('link', { name: 'Start Free' }).first();
  await expect(startFreeLink).toBeVisible();
  await Promise.all([
    page.waitForURL(/\/signup$/, { timeout: 15000 }),
    startFreeLink.click(),
  ]);
});

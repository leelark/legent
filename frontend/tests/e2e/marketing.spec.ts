import { expect, test } from '@playwright/test';

test('marketing navigation and auth entry points', async ({ page }) => {
  await page.goto('/');
  const featuresLink = page.getByRole('link', { name: 'Features', exact: true }).first();
  await expect(featuresLink).toBeVisible();
  await featuresLink.click();
  await expect(page).toHaveURL(/\/features$/);
  await page.getByRole('link', { name: 'Start Free' }).first().click();
  await expect(page).toHaveURL(/\/signup$/);
});

import { expect, test, type Page } from '@playwright/test';

async function waitForPublicHydration(page: Page) {
  await expect(page.locator('.public-site')).toHaveAttribute('data-hydrated', 'true', { timeout: 15000 });
}

test('login page links to recovery and signup', async ({ page }) => {
  await page.goto('/login', { waitUntil: 'domcontentloaded' });
  await waitForPublicHydration(page);
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
  await Promise.all([
    page.waitForURL(/\/forgot-password$/, { timeout: 30000, waitUntil: 'domcontentloaded' }),
    page.getByRole('link', { name: 'Forgot password?' }).click(),
  ]);
  await page.goto('/login', { waitUntil: 'domcontentloaded' });
  await waitForPublicHydration(page);
  await Promise.all([
    page.waitForURL(/\/signup$/, { timeout: 30000, waitUntil: 'domcontentloaded' }),
    page.getByRole('link', { name: 'Create account' }).click(),
  ]);
});

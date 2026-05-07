import { expect, test } from '@playwright/test';

test('login page links to recovery and signup', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
  await Promise.all([
    page.waitForURL(/\/forgot-password$/, { timeout: 15000 }),
    page.getByRole('link', { name: 'Forgot password?' }).click(),
  ]);
  await page.goto('/login');
  await Promise.all([
    page.waitForURL(/\/signup$/, { timeout: 15000 }),
    page.getByRole('link', { name: 'Create account' }).click(),
  ]);
});

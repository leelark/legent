import { expect, test } from '@playwright/test';

test('login page links to recovery and signup', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
  await page.getByRole('link', { name: 'Forgot password?' }).click();
  await expect(page).toHaveURL(/\/forgot-password$/);
  await page.goto('/login');
  await page.getByRole('link', { name: 'Create account' }).click();
  await expect(page).toHaveURL(/\/signup$/);
});


import { expect, test } from '@playwright/test';

test('login bootstraps workspace context and opens app route', async ({ page }) => {
  const email = process.env.E2E_ADMIN_EMAIL;
  const password = process.env.E2E_ADMIN_PASSWORD;
  test.skip(!email || !password, 'E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD are required for login smoke');

  await page.goto('/login');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill(password);
  await Promise.all([
    page.waitForURL(/\/app\/email$/, { timeout: 45000 }),
    page.getByRole('button', { name: 'Sign in' }).click(),
  ]);

  await expect(page.getByRole('heading', { name: 'Email Studio', level: 1 })).toBeVisible({ timeout: 45000 });
  await expect(page.getByRole('navigation')).toBeVisible();
  await expect(page.getByText('Workspace required')).toHaveCount(0);
});

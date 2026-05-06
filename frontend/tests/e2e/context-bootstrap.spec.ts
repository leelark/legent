import { expect, test } from '@playwright/test';

test('login bootstraps workspace context and opens app route', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email address').fill('admin@legent.com');
  await page.getByLabel('Password').fill('Admin@123');
  await page.getByRole('button', { name: 'Sign in' }).click();

  await expect(page.getByRole('heading', { name: 'Email Studio' })).toBeVisible({ timeout: 20000 });
  await expect(page.getByRole('navigation')).toBeVisible();
  await expect(page.getByText('Workspace required')).toHaveCount(0);
});

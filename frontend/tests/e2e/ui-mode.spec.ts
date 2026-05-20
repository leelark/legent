import { expect, test } from '@playwright/test';
import { mockWorkspaceApis } from './support/workspace-mock';

test('basic mode hides advanced settings navigation without blocking direct route access', async ({ page }) => {
  await mockWorkspaceApis(page, { uiMode: 'BASIC' });
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('/app/email', { waitUntil: 'domcontentloaded' });

  await expect(page.locator('html')).toHaveClass(/mode-basic/);
  await expect(page.getByRole('link', { name: /^Settings$/ })).toHaveCount(0);
  await expect(page.locator('[data-mode-feature="workspace.nav.settings"]')).toHaveCount(0);

  await page.goto('/app/settings/platform', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { name: /Enterprise Settings/ })).toBeVisible({ timeout: 45_000 });
});

test('advanced mode shows settings navigation while admin remains role gated', async ({ page }) => {
  await mockWorkspaceApis(page, { roles: ['USER'], uiMode: 'ADVANCED' });
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('/app/email', { waitUntil: 'domcontentloaded' });

  await expect(page.locator('html')).toHaveClass(/mode-advanced/);
  await expect(page.getByRole('link', { name: /^Settings$/ })).toBeVisible();
  await expect(page.getByRole('link', { name: /^Admin$/ })).toHaveCount(0);

  await page.getByRole('button', { name: 'Toggle Basic or Advanced mode' }).click();
  await expect(page.locator('html')).toHaveClass(/mode-basic/);
  await expect(page.getByRole('link', { name: /^Settings$/ })).toHaveCount(0);
});

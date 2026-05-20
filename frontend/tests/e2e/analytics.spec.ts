import { expect, test } from '@playwright/test';
import { mockWorkspaceApis } from './support/workspace-mock';

test('analytics page renders journey path analytics without campaign experiment conflation', async ({ page }) => {
  await mockWorkspaceApis(page);
  await page.setViewportSize({ width: 1440, height: 920 });

  await page.goto('/app/analytics', { waitUntil: 'domcontentloaded' });

  await expect(page.getByRole('heading', { name: 'Analytics Overview' })).toBeVisible({ timeout: 45_000 });
  await expect(page.getByRole('heading', { name: 'Journey Path Performance' })).toBeVisible();
  await expect(page.getByRole('combobox', { name: 'Workflow' })).toHaveValue('workflow-1');
  await expect(page.locator('p', { hasText: 'Lifecycle nurture' }).first()).toBeVisible();
  await expect(page.getByRole('cell', { name: 'Offer path test' })).toBeVisible();
  await expect(page.getByText('entry -> split -> goal')).toBeVisible();
  await expect(page.getByText('Journey Path Tests')).toBeVisible();
  await expect(page.getByText('8 tracking conversions')).toBeVisible();
  await expect(page.getByText('Deterministic Signals')).toBeVisible();

  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(2);
});

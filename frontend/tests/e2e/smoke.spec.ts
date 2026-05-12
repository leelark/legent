import { expect, test } from '@playwright/test';
import { mockWorkspaceApis } from './support/workspace-mock';

test('release smoke: health endpoint and public shell render', async ({ page, request }) => {
  const health = await request.get('/api/health');
  expect(health.ok()).toBeTruthy();

  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await expect(page.locator('.public-site')).toHaveAttribute('data-hydrated', 'true', { timeout: 15_000 });
  await expect(page.getByRole('heading', { name: /Run lifecycle email/i })).toBeVisible();
});

const workspaceRoutes = [
  { path: '/app/audience', heading: /Audience/i },
  { path: '/app/campaigns', heading: /Campaign Studio/i },
  { path: '/app/admin', heading: /Govern users, runtime policy/i },
  { path: '/app/settings/platform', heading: /Settings that reshape/i },
];

for (const route of workspaceRoutes) {
  test(`release smoke: workspace route renders desktop ${route.path}`, async ({ page }) => {
    await mockWorkspaceApis(page);
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto(route.path, { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: route.heading }).first()).toBeVisible({ timeout: 45_000 });
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(2);
  });

  test(`release smoke: workspace route renders mobile ${route.path}`, async ({ page }) => {
    await mockWorkspaceApis(page);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(route.path, { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: route.heading }).first()).toBeVisible({ timeout: 45_000 });
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(2);
  });
}

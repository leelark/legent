import { expect, test } from '@playwright/test';
import { mockWorkspaceApis } from './support/workspace-mock';

const cases = [
  {
    name: 'home desktop',
    path: '/',
    viewport: { width: 1440, height: 900 },
    heading: /Run lifecycle email/i,
  },
  {
    name: 'home mobile',
    path: '/',
    viewport: { width: 390, height: 844 },
    heading: /Run lifecycle email/i,
  },
  {
    name: 'login desktop',
    path: '/login',
    viewport: { width: 1440, height: 900 },
    heading: /Welcome back/i,
  },
  {
    name: 'login mobile',
    path: '/login',
    viewport: { width: 390, height: 844 },
    heading: /Welcome back/i,
  },
  {
    name: 'app shell desktop',
    path: '/app/audience',
    viewport: { width: 1440, height: 900 },
    heading: /Audience/i,
    authenticated: true,
  },
  {
    name: 'app shell mobile',
    path: '/app/audience',
    viewport: { width: 390, height: 844 },
    heading: /Audience/i,
    authenticated: true,
  },
  {
    name: 'admin desktop',
    path: '/app/admin',
    viewport: { width: 1440, height: 900 },
    heading: /Govern users, runtime policy/i,
    authenticated: true,
  },
  {
    name: 'settings mobile',
    path: '/app/settings/platform',
    viewport: { width: 390, height: 844 },
    heading: /Settings that reshape/i,
    authenticated: true,
  },
];

for (const item of cases) {
  test(`visual shell has no horizontal overflow: ${item.name}`, async ({ page }, testInfo) => {
    if ('authenticated' in item && item.authenticated) {
      await mockWorkspaceApis(page);
    }
    await page.setViewportSize(item.viewport);
    await page.goto(item.path, { waitUntil: 'networkidle' });
    await expect(page.getByRole('heading', { name: item.heading }).first()).toBeVisible();

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(2);

    const screenshotName = item.name.replace(/\s+/g, '-');
    await page.screenshot({
      path: testInfo.outputPath(`${screenshotName}.png`),
      fullPage: true,
    });
  });
}

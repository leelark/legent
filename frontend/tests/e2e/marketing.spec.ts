import { expect, test } from '@playwright/test';

test('marketing navigation and auth entry points', async ({ page }) => {
  await page.goto('/');
  const featuresLink = page.getByRole('link', { name: 'Features', exact: true }).first();
  await expect(featuresLink).toBeVisible();
  await Promise.all([
    page.waitForURL(/\/features$/, { timeout: 15000 }),
    featuresLink.click(),
  ]);
  const startFreeLink = page.getByRole('link', { name: 'Start Free' }).first();
  await expect(startFreeLink).toBeVisible();
  await Promise.all([
    page.waitForURL(/\/signup$/, { timeout: 15000 }),
    startFreeLink.click(),
  ]);
});

test('public pages have differentiated content and contact submit state', async ({ page }) => {
  await page.route('**/api/v1/public/contact', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: {
          id: '01HXCONTACTREQUEST000000000',
          status: 'RECEIVED',
          message: 'Request received. A Legent specialist will follow up shortly.',
        },
      }),
    });
  });

  await page.goto('/');
  await expect(page.getByRole('heading', { name: /premium command center/i })).toBeVisible();
  await expect(page.getByRole('heading', { name: /Solution operating layers|Summary cards aligned/i })).toBeVisible();

  await page.goto('/features');
  await expect(page.getByRole('heading', { name: /practical operators/i })).toBeVisible();
  await expect(page.getByText('Security and governance')).toBeVisible();

  await page.goto('/modules');
  await expect(page.getByRole('heading', { name: /Dedicated spaces/i })).toBeVisible();
  await expect(page.getByText('How studios work together')).toBeVisible();

  await page.goto('/pricing');
  await expect(page.getByText('INR 4,999')).toBeVisible();
  await expect(page.getByRole('heading', { name: /What changes as your program scales/i })).toBeVisible();

  await page.goto('/contact');
  await page.getByLabel('Work email').fill('operator@example.com');
  await page.getByLabel('Company').fill('Legent Labs');
  await page.getByLabel('Message').fill('We need a governed rollout plan.');
  await page.getByRole('button', { name: /Request Consultation/i }).click();
  await expect(page.getByText('Request received. A Legent specialist will follow up shortly.')).toBeVisible();

  await page.setViewportSize({ width: 390, height: 760 });
  await page.goto('/modules');
  await page.getByLabel('Toggle navigation').click();
  await expect(page.getByRole('link', { name: 'Pricing', exact: true })).toBeVisible();
});

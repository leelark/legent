import { expect, test } from '@playwright/test';

test('premium public navigation, theme persistence, and mobile menu', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: /Run lifecycle email/i })).toBeVisible();

  await page.getByRole('button', { name: 'Toggle public theme' }).first().click();
  await page.reload();
  await expect(page.locator('.public-site.public-dark')).toBeVisible();

  const featuresLink = page.getByRole('link', { name: 'Features', exact: true }).first();
  await Promise.all([page.waitForURL(/\/features$/), featuresLink.click()]);
  await expect(page.getByRole('heading', { name: /Every capability works/i })).toBeVisible();

  await page.setViewportSize({ width: 390, height: 800 });
  await page.goto('/modules');
  await page.getByLabel('Toggle navigation').click();
  await expect(page.getByRole('link', { name: /Pricing/ }).first()).toBeVisible();
});

test('homepage scenarios and pricing yearly toggle work', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: /Show Provider-aware delivery room/i }).click();
  await expect(page.getByRole('heading', { name: 'Provider-aware delivery room' }).first()).toBeVisible();

  await page.goto('/pricing');
  await expect(page.getByRole('heading', { name: /Pricing responds/i })).toBeVisible();
  await page.getByRole('button', { name: 'Yearly' }).first().click();
  await expect(page.getByText(/INR 47,990\/yr/)).toBeVisible();
  await expect(page.getByText(/20% annual savings/i).first()).toBeVisible();
});

test('public pages are differentiated and contact submit succeeds', async ({ page }) => {
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

  await page.goto('/features');
  await expect(page.getByText('Interactive architecture')).toBeVisible();

  await page.goto('/modules');
  await expect(page.getByRole('heading', { name: /Select a module/i })).toBeVisible();

  await page.goto('/about');
  await expect(page.getByRole('heading', { name: /production infrastructure/i })).toBeVisible();

  await page.goto('/blog');
  await expect(page.getByRole('heading', { name: /Operating essays/i })).toBeVisible();

  await page.goto('/contact');
  await page.getByLabel('Work email').fill('operator@example.com');
  await page.getByLabel('Company').fill('Legent Labs');
  await page.getByLabel('Message').fill('We need a governed rollout plan.');
  await page.getByRole('button', { name: /Request Consultation/i }).click();
  await expect(page.getByText('Request received. A Legent specialist will follow up shortly.')).toBeVisible();
});

test('auth and onboarding public surfaces preserve required fields', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: /Welcome back/i })).toBeVisible();
  await expect(page.getByLabel('Email address')).toBeVisible();
  await expect(page.getByLabel('Password')).toBeVisible();

  await page.goto('/signup');
  await expect(page.getByRole('heading', { name: /Create your operating workspace/i })).toBeVisible();
  await expect(page.getByLabel('Company name')).toBeVisible();
  await expect(page.getByLabel('Work email')).toBeVisible();

  await page.goto('/forgot-password');
  await expect(page.getByLabel('Email address')).toBeVisible();

  await page.goto('/reset-password?token=test-token');
  await expect(page.getByLabel('New password')).toBeVisible();

  await page.goto('/onboarding');
  await expect(page.getByLabel('Workspace setup')).toBeVisible();
});

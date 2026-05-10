import { expect, test, type Page } from '@playwright/test';

test.describe.configure({ timeout: 120_000 });

async function waitForPublicHydration(page: Page) {
  await expect(page.locator('.public-site')).toHaveAttribute('data-hydrated', 'true', { timeout: 15000 });
}

async function gotoPublic(page: Page, url: string) {
  for (let attempt = 0; attempt < 2; attempt += 1) {
    try {
      await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 45_000 });
      break;
    } catch (error) {
      if (attempt === 1 || !String(error).includes('interrupted by another navigation')) {
        throw error;
      }
      await page.waitForTimeout(300);
    }
  }
  await expect(page).toHaveURL(new RegExp(`${url.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`), { timeout: 15000 });
  await waitForPublicHydration(page);
}

test('premium public navigation, theme persistence, and mobile menu', async ({ page }) => {
  await gotoPublic(page, '/');
  await page.evaluate(() => window.localStorage.setItem('legent_public_theme', 'light'));
  await page.reload({ waitUntil: 'domcontentloaded' });
  await waitForPublicHydration(page);
  await expect(page.getByRole('heading', { name: /Run lifecycle email/i })).toBeVisible();

  await page.getByRole('button', { name: 'Toggle public theme' }).first().click();
  await expect(page.locator('.public-site.public-dark')).toBeVisible();
  await page.reload({ waitUntil: 'domcontentloaded' });
  await waitForPublicHydration(page);
  await expect(page.locator('.public-site.public-dark')).toBeVisible();

  const featuresLink = page.getByRole('link', { name: 'Features', exact: true }).first();
  await Promise.all([page.waitForURL(/\/features$/, { waitUntil: 'domcontentloaded' }), featuresLink.click()]);
  await waitForPublicHydration(page);
  await expect(page.getByRole('heading', { name: /Every capability works/i })).toBeVisible();

  await page.setViewportSize({ width: 390, height: 800 });
  await gotoPublic(page, '/modules');
  await page.getByLabel('Toggle navigation').click();
  await expect(page.getByRole('link', { name: /Pricing/ }).first()).toBeVisible();
});

test('homepage scenarios and pricing yearly toggle work', async ({ page }) => {
  await gotoPublic(page, '/');
  await page.getByRole('button', { name: /Show Provider-aware delivery room/i }).click();
  await expect(page.getByRole('heading', { name: 'Provider-aware delivery room' }).first()).toBeVisible();

  await gotoPublic(page, '/pricing');
  await expect(page.getByRole('heading', { name: /Pricing responds/i })).toBeVisible();
  const planSection = page.locator('section').filter({ has: page.getByRole('heading', { name: /Pricing responds/i }) });
  await planSection.getByRole('button', { name: /^yearly$/i }).click();
  await expect(planSection.getByText(/INR 47,990\/yr/)).toBeVisible();
  await expect(planSection.getByText(/20% annual savings/i).first()).toBeVisible();
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

  await gotoPublic(page, '/features');
  await expect(page.getByText('Interactive architecture')).toBeVisible();

  await gotoPublic(page, '/modules');
  await expect(page.getByRole('heading', { name: /Select a module/i })).toBeVisible();

  await gotoPublic(page, '/about');
  await expect(page.getByRole('heading', { name: /production infrastructure/i })).toBeVisible();

  await gotoPublic(page, '/blog');
  await expect(page.getByRole('heading', { name: /Operating essays/i })).toBeVisible();

  await gotoPublic(page, '/contact');
  await page.getByLabel('Work email').fill('operator@example.com');
  await page.getByLabel('Company').fill('Legent Labs');
  await page.getByLabel('Message').fill('We need a governed rollout plan.');
  await page.getByRole('button', { name: /Request Consultation/i }).click();
  await expect(page.getByText('Request received. A Legent specialist will follow up shortly.')).toBeVisible();
});

test('auth and onboarding public surfaces preserve required fields', async ({ page }) => {
  await gotoPublic(page, '/login');
  await expect(page.getByRole('heading', { name: /Welcome back/i })).toBeVisible();
  await expect(page.getByLabel('Email address')).toBeVisible();
  await expect(page.getByLabel('Password')).toBeVisible();

  await gotoPublic(page, '/signup');
  await expect(page.getByRole('heading', { name: /Create your operating workspace/i })).toBeVisible();
  await expect(page.getByLabel('Company name')).toBeVisible();
  await expect(page.getByLabel('Work email')).toBeVisible();

  await gotoPublic(page, '/forgot-password');
  await expect(page.getByLabel('Email address')).toBeVisible();

  await gotoPublic(page, '/reset-password?token=test-token');
  await expect(page.getByLabel('New password')).toBeVisible();

  await gotoPublic(page, '/onboarding');
  await expect(page.getByLabel('Workspace setup')).toBeVisible();
});

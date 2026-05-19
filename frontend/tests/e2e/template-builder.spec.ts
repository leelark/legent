import { expect, test, type Page, type Route } from '@playwright/test';

type SeenRequests = {
  draftPayload?: Record<string, unknown>;
  updatePayload?: Record<string, unknown>;
  renderPayload?: Record<string, unknown>;
  validatePayload?: Record<string, unknown>;
};

function ok(data: unknown) {
  return {
    success: true,
    data,
    meta: { timestamp: new Date('2026-05-19T00:00:00Z').toISOString() },
  };
}

async function fulfill(route: Route, data: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(data),
  });
}

async function mockTemplateStudioApis(page: Page, seen: SeenRequests) {
  await page.addInitScript(() => {
    localStorage.setItem('legent_user_id', 'template-user');
    localStorage.setItem('legent_roles', JSON.stringify(['ADMIN']));
    localStorage.setItem('legent_tenant_id', 'tenant-1');
    localStorage.setItem('legent_workspace_id', 'workspace-1');
    localStorage.setItem('legent_environment_id', 'local');
  });

  const initialBlocks = [
    {
      id: 'hero-block',
      name: 'Hero',
      blockType: 'HEADER',
      content: '<h2>Spring launch</h2>',
      styles: { backgroundColor: '#ffffff', textColor: '#0f172a', padding: 18, borderRadius: 8 },
      settings: { hideOnMobile: false, hideOnDesktop: false, visibilityRule: '' },
    },
    {
      id: 'body-block',
      name: 'Body',
      blockType: 'TEXT',
      content: '<p>Launch copy</p>',
      styles: { backgroundColor: '#ffffff', textColor: '#0f172a', padding: 16, borderRadius: 8 },
      settings: { hideOnMobile: false, hideOnDesktop: false, visibilityRule: '' },
    },
  ];

  let currentTemplate = {
    id: 'tpl-1',
    name: 'Launch Template',
    subject: 'Launch day',
    status: 'DRAFT',
    category: 'Lifecycle',
    approvalRequired: false,
    templateType: 'MARKETING',
    lastPublishedVersion: 1,
    updatedAt: '2026-05-18T10:00:00Z',
    createdAt: '2026-05-17T10:00:00Z',
    metadata: JSON.stringify({ builderBlocks: initialBlocks }),
    draftSubject: 'Launch day',
    draftHtmlContent: '<p>Launch copy</p>',
  };

  const libraryTemplates = [
    currentTemplate,
    {
      id: 'tpl-published',
      name: 'Promo Published',
      subject: 'Offer live',
      status: 'PUBLISHED',
      category: 'Promotion',
      templateType: 'MARKETING',
      lastPublishedVersion: 3,
      updatedAt: '2026-05-16T10:00:00Z',
      createdAt: '2026-05-15T10:00:00Z',
      metadata: '{}',
    },
    {
      id: 'tpl-archived',
      name: 'Old Digest',
      subject: 'Archived news',
      status: 'ARCHIVED',
      category: 'Newsletter',
      templateType: 'MARKETING',
      updatedAt: '2026-05-14T10:00:00Z',
      createdAt: '2026-05-13T10:00:00Z',
      metadata: '{}',
    },
  ];

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');
    const method = request.method();

    if (path === '/auth/session') {
      return fulfill(route, ok({
        status: 'success',
        userId: 'template-user',
        tenantId: 'tenant-1',
        workspaceId: 'workspace-1',
        environmentId: 'local',
        roles: ['ADMIN'],
      }));
    }
    if (path === '/auth/contexts') {
      return fulfill(route, ok([{ tenantId: 'tenant-1', workspaceId: 'workspace-1', environmentId: 'local', default: true }]));
    }
    if (path === '/users/preferences') {
      return fulfill(route, ok({
        tenantId: 'tenant-1',
        userId: 'template-user',
        theme: 'light',
        uiMode: 'ADVANCED',
        density: 'comfortable',
        sidebarCollapsed: false,
        metadata: {},
      }));
    }
    if (path === '/templates' && method === 'GET') {
      return fulfill(route, ok({ content: libraryTemplates }));
    }
    if (path === '/templates/tpl-1' && method === 'GET') {
      return fulfill(route, ok(currentTemplate));
    }
    if (path === '/templates/tpl-1' && method === 'PUT') {
      const payload = JSON.parse(request.postData() || '{}') as Record<string, unknown>;
      seen.updatePayload = payload;
      currentTemplate = {
        ...currentTemplate,
        ...payload,
        draftHtmlContent: typeof payload.htmlContent === 'string' ? payload.htmlContent : currentTemplate.draftHtmlContent,
        draftSubject: typeof payload.subject === 'string' ? payload.subject : currentTemplate.draftSubject,
        metadata: typeof payload.metadata === 'string' ? payload.metadata : currentTemplate.metadata,
      };
      return fulfill(route, ok(currentTemplate));
    }
    if (path === '/templates/tpl-1/draft' && method === 'POST') {
      seen.draftPayload = JSON.parse(request.postData() || '{}') as Record<string, unknown>;
      return fulfill(route, ok({ templateId: 'tpl-1', status: 'DRAFT' }));
    }
    if (path === '/templates/tpl-1/render' && method === 'POST') {
      seen.renderPayload = JSON.parse(request.postData() || '{}') as Record<string, unknown>;
      return fulfill(route, ok({
        subject: 'Rendered launch QA',
        htmlContent: '<h1>Rendered launch QA</h1><p>Current draft QA</p>',
        textContent: 'Rendered launch QA Current draft QA',
        validationStatus: 'VALID',
        warnings: ['Gmail clipping risk'],
        compatibilityWarnings: [],
        tokenKeys: ['firstName'],
        dynamicSlots: [],
      }));
    }
    if (path === '/templates/tpl-1/validate' && method === 'POST') {
      seen.validatePayload = JSON.parse(request.postData() || '{}') as Record<string, unknown>;
      return fulfill(route, ok({
        status: 'VALID',
        linkCount: 1,
        brokenLinkCount: 0,
        imageCount: 1,
        imagesMissingAlt: 0,
        warnings: [],
        errors: [],
        compatibilityWarnings: [],
        tokenKeys: ['firstName'],
        dynamicSlots: [],
      }));
    }
    if (path === '/templates/tpl-1/versions') {
      return fulfill(route, ok([
        { id: 'version-1', versionNumber: 1, isPublished: true, createdAt: '2026-05-18T10:00:00Z' },
      ]));
    }
    if (path === '/templates/tpl-1/approvals') {
      return fulfill(route, ok([]));
    }
    if (path === '/templates/tpl-1/dynamic-content') {
      return fulfill(route, ok([
        { id: 'rule-1', templateId: 'tpl-1', slotKey: 'main', name: 'VIP', priority: 1, operator: 'EQUALS', active: true },
      ]));
    }
    if (path === '/templates/tpl-1/test-sends') {
      return fulfill(route, ok([
        { id: 'test-1', templateId: 'tpl-1', recipientEmail: 'qa@example.com', status: 'QUEUED', createdAt: '2026-05-18T11:00:00Z' },
      ]));
    }
    if (path.startsWith('/assets')) {
      return fulfill(route, ok({ content: [
        { id: 'asset-1', name: 'Hero', fileName: 'hero.png', contentType: 'image/png', sizeBytes: 1200 },
      ] }));
    }
    if (path.startsWith('/content/snippets')) {
      return fulfill(route, ok({ content: [
        { id: 'snippet-1', snippetKey: 'footer.disclaimer', name: 'Footer Disclaimer', snippetType: 'HTML', content: '<p>Footer</p>' },
      ] }));
    }
    if (path.startsWith('/personalization-tokens')) {
      return fulfill(route, ok({ content: [
        { id: 'token-1', tokenKey: 'firstName', displayName: 'First name', dataPath: 'firstName', defaultValue: 'there' },
      ] }));
    }
    if (path.startsWith('/brand-kits')) {
      return fulfill(route, ok({ content: [
        { id: 'brand-1', name: 'Primary Brand', primaryColor: '#2563eb', isDefault: true },
      ] }));
    }

    return fulfill(route, ok([]));
  });
}

test('template builder supports add, edit, drag reorder, responsive rules, and save payload', async ({ page }) => {
  const seen: SeenRequests = {};
  await mockTemplateStudioApis(page, seen);
  await page.setViewportSize({ width: 1440, height: 1000 });

  await page.goto('/app/email/templates/tpl-1', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { name: 'Template Studio' })).toBeVisible({ timeout: 45_000 });
  const builder = page.getByTestId('template-builder');
  await expect(builder).toBeVisible();
  await expect(page.locator('[data-testid^="builder-block-"]')).toHaveCount(2);

  await builder.getByTestId('block-library-BUTTON').click();
  await expect(page.locator('[data-testid^="builder-block-"]')).toHaveCount(3);
  await page.getByLabel('Block Name').fill('Primary CTA');
  await page.getByLabel('HTML Content').fill('<a href="https://example.com/start">Start now</a><script>alert(1)</script>');

  await builder.getByRole('button', { name: 'Style' }).click();
  await page.getByLabel('Padding').fill('22');
  await page.getByRole('spinbutton', { name: 'Border' }).fill('1');
  await builder.getByRole('button', { name: 'Center align' }).click();

  await builder.getByRole('button', { name: 'Rules' }).click();
  await page.getByLabel('Hide on mobile').check();
  await page.getByLabel('Conditional Rule').fill('segment == VIP');
  await builder.getByRole('button', { name: 'Mobile preview' }).click();
  await expect(page.getByText('Hidden on mobile')).toBeVisible();

  await page.evaluate(() => {
    const source = document.querySelector('[data-testid="builder-block-BUTTON"]');
    const target = document.querySelector('[data-testid="builder-drop-zone-0"]');
    if (!source || !target) {
      throw new Error('builder drag handles missing');
    }
    const dataTransfer = new DataTransfer();
    source.dispatchEvent(new DragEvent('dragstart', { bubbles: true, cancelable: true, dataTransfer }));
    target.dispatchEvent(new DragEvent('dragover', { bubbles: true, cancelable: true, dataTransfer }));
    target.dispatchEvent(new DragEvent('drop', { bubbles: true, cancelable: true, dataTransfer }));
    source.dispatchEvent(new DragEvent('dragend', { bubbles: true, cancelable: true, dataTransfer }));
  });
  await expect(page.locator('[data-testid^="builder-block-"]').first()).toHaveAttribute('data-testid', 'builder-block-BUTTON');

  await page.getByRole('button', { name: 'Save Draft' }).click();
  await expect.poll(() => Boolean(seen.updatePayload)).toBeTruthy();
  await expect.poll(() => Boolean(seen.draftPayload)).toBeTruthy();

  const updatePayload = seen.updatePayload ?? {};
  expect(String(updatePayload.htmlContent)).toContain('legent-hide-mobile');
  expect(String(updatePayload.htmlContent)).toContain('data-legent-visibility-rule="segment == VIP"');
  expect(String(updatePayload.htmlContent)).not.toContain('<script>');

  const metadata = JSON.parse(String(updatePayload.metadata)) as {
    builderBlocks: Array<{
      blockType: string;
      name: string;
      settings?: Record<string, unknown>;
      styles?: Record<string, unknown>;
    }>;
  };
  expect(metadata.builderBlocks[0].blockType).toBe('BUTTON');
  expect(metadata.builderBlocks[0].name).toBe('Primary CTA');
  expect(metadata.builderBlocks[0].settings?.hideOnMobile).toBe(true);
  expect(metadata.builderBlocks[0].styles?.textAlign).toBe('center');
});

test('template module exposes library controls and command-center QA flow', async ({ page }) => {
  const seen: SeenRequests = {};
  await mockTemplateStudioApis(page, seen);
  await page.setViewportSize({ width: 1440, height: 1000 });

  await page.goto('/app/email/templates', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { name: 'Template Studio' })).toBeVisible({ timeout: 45_000 });
  await expect(page.getByText('Total Library')).toBeVisible();
  await expect(page.getByText('Prebuilt campaign templates')).toBeVisible();

  await page.getByRole('textbox', { name: 'Search' }).fill('launch');
  await page.getByRole('button', { name: 'Grid view' }).click();
  await expect(page.getByTestId('template-card-tpl-1')).toBeVisible();
  await expect(page.getByText('Promo Published')).toHaveCount(0);

  await page.getByRole('button', { name: 'List view' }).click();
  await page.getByRole('link', { name: /Launch Template/ }).click();
  await expect(page.getByTestId('template-command-center')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Operational readiness' })).toBeVisible();

  await page.getByRole('button', { name: 'Run QA' }).click();
  await expect.poll(() => Boolean(seen.renderPayload)).toBeTruthy();
  await expect.poll(() => Boolean(seen.validatePayload)).toBeTruthy();

  await page.getByRole('tab', { name: 'Preview & QA' }).click();
  await expect(page.getByTestId('template-preview-frame')).toContainText('Rendered launch QA');
});

import { expect, test, type Page, type Route } from '@playwright/test';

const unsupportedDefinition = {
  graphVersion: 2,
  initialNodeId: 'entry',
  nodes: {
    entry: { id: 'entry', type: 'ENTRY_TRIGGER', configuration: {}, nextNodeId: 'wait' },
    wait: { id: 'wait', type: 'WAIT_UNTIL', configuration: { at: '2026-05-20T12:00:00Z' }, nextNodeId: 'end' },
    end: { id: 'end', type: 'END', configuration: {} },
  },
};

const supportedDefinition = {
  graphVersion: 2,
  initialNodeId: 'entry',
  nodes: {
    entry: { id: 'entry', type: 'ENTRY_TRIGGER', configuration: {}, nextNodeId: 'send' },
    send: { id: 'send', type: 'SEND_EMAIL', configuration: { campaignId: 'campaign-1' }, nextNodeId: 'end' },
    end: { id: 'end', type: 'END', configuration: {} },
  },
};

function ok(data: unknown) {
  return {
    success: true,
    data,
    meta: { timestamp: new Date('2026-05-20T00:00:00Z').toISOString() },
  };
}

async function fulfill(route: Route, data: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(data),
  });
}

async function mockAutomationApis(
  page: Page,
  seen: Record<string, unknown> = {},
  options: { definition?: unknown; validationResponse?: unknown; uiMode?: 'BASIC' | 'ADVANCED' } = {}
) {
  await page.addInitScript(() => {
    localStorage.setItem('legent_user_id', 'automation-user');
    localStorage.setItem('legent_roles', JSON.stringify(['ADMIN']));
    localStorage.setItem('legent_tenant_id', 'tenant-1');
    localStorage.setItem('legent_workspace_id', 'workspace-1');
    localStorage.setItem('legent_environment_id', 'local');
  });

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');
    const method = request.method();

    if (path === '/auth/session') {
      return fulfill(route, ok({ status: 'success', userId: 'automation-user', tenantId: 'tenant-1', workspaceId: 'workspace-1', roles: ['ADMIN'] }));
    }
    if (path === '/auth/contexts') {
      return fulfill(route, ok([{ tenantId: 'tenant-1', workspaceId: 'workspace-1', environmentId: 'local', default: true }]));
    }
    if (path === '/users/preferences') {
      return fulfill(route, ok({ tenantId: 'tenant-1', userId: 'automation-user', theme: 'light', uiMode: options.uiMode ?? 'ADVANCED', density: 'comfortable', metadata: {} }));
    }
    if (path === '/workflow-definitions/workflow-1/latest') {
      return fulfill(route, ok({
        workflowId: 'workflow-1',
        version: 1,
        graphVersion: 2,
        definition: options.definition ?? unsupportedDefinition,
      }));
    }
    if (path === '/workflows/workflow-1/capabilities') {
      return fulfill(route, ok({
        workflowId: 'workflow-1',
        activeDefinitionVersion: 1,
        graphVersion: 2,
        capabilities: {
          runtimeSupportedNodeTypes: ['ENTRY_TRIGGER', 'SEND_EMAIL', 'DELAY', 'CONDITION', 'END'],
          runtimeUnsupportedNodes: [],
        },
      }));
    }
    if (path === '/workflows/workflow-1/validate' && method === 'POST') {
      seen.validation = JSON.parse(request.postData() || '{}');
      return fulfill(route, ok(options.validationResponse ?? { valid: true, runtimeSupported: true, errors: [] }));
    }
    if (path === '/workflows/workflow-1/definitions' && method === 'POST') {
      const definition = JSON.parse(request.postData() || '{}');
      seen.definition = definition;
      if (url.searchParams.get('published') === 'true') {
        seen.publishedDefinition = definition;
      }
      return fulfill(route, ok({ workflowId: 'workflow-1', version: 2 }));
    }
    if (path === '/workflows/workflow-1/trigger' && method === 'POST') {
      seen.trigger = JSON.parse(request.postData() || '{}');
      return fulfill(route, ok({ id: 'manual-run-1' }));
    }
    return fulfill(route, ok([]));
  });
}

test('automation builder marks unsupported loaded nodes as draft only', async ({ page }) => {
  await mockAutomationApis(page);
  await page.goto('/app/automations/builder?id=workflow-1');

  await expect(page.getByRole('heading', { name: 'Workflow canvas' })).toBeVisible();
  await page.getByRole('button', { name: 'Load' }).click();

  await expect(page.getByText('WAIT_UNTIL')).toBeVisible();
  await expect(page.getByText('Draft only')).toBeVisible();
});

test('node editor disables unsupported runtime node choices', async ({ page }) => {
  await mockAutomationApis(page);
  await page.goto('/app/automations/builder?id=workflow-1');

  await page.getByRole('button', { name: /add step/i }).click();
  await page.getByLabel('Delete journey step').waitFor();
  await page.getByLabel('Edit journey step Send Email').click();

  const nodeType = page.getByLabel('Node Type');
  await expect(nodeType.locator('option[value="SEND_EMAIL"]')).toBeEnabled();
  await expect(nodeType.locator('option[value="WAIT_UNTIL"]')).toBeDisabled();
  await expect(nodeType.locator('option[value="WEBHOOK"]')).toBeDisabled();
});

test('activate blocks unsupported draft-only nodes before published save', async ({ page }) => {
  const seen: Record<string, unknown> = {};
  await mockAutomationApis(page, seen, {
    validationResponse: {
      valid: false,
      runtimeSupported: false,
      errors: ['node wait type WAIT_UNTIL is not supported by the live workflow runtime'],
    },
  });
  await page.goto('/app/automations/builder?id=workflow-1');

  await page.getByRole('button', { name: 'Load' }).click();
  await page.getByRole('button', { name: 'Activate Workflow' }).click();

  await expect(page.getByRole('alert').filter({ hasText: 'WAIT_UNTIL' })).toBeVisible();
  expect(seen.validation).toBeTruthy();
  expect(seen.publishedDefinition).toBeUndefined();
});

test('basic mode hides manual trigger and blocks draft-only node save', async ({ page }) => {
  const seen: Record<string, unknown> = {};
  await mockAutomationApis(page, seen, { uiMode: 'BASIC' });
  await page.goto('/app/automations/builder?id=workflow-1');

  await expect(page.getByRole('button', { name: 'Trigger' })).toHaveCount(0);
  await expect(page.locator('[data-mode-feature="automation.workflow.manual-trigger"]')).toHaveCount(0);

  await page.getByRole('button', { name: 'Load' }).click();
  await expect(page.getByText('WAIT_UNTIL')).toBeVisible();
  await expect(page.getByText('Draft only')).toBeVisible();
  await page.getByRole('button', { name: 'Save' }).click();

  await expect(page.getByText(/requires Advanced mode before saving/)).toBeVisible();
  expect(seen.definition).toBeUndefined();
  expect(seen.trigger).toBeUndefined();
});

test('activate fails closed when validation does not confirm runtime support', async ({ page }) => {
  const seen: Record<string, unknown> = {};
  await mockAutomationApis(page, seen, { validationResponse: {} });
  await page.goto('/app/automations/builder?id=workflow-1');

  await page.getByRole('button', { name: 'Load' }).click();
  await page.getByRole('button', { name: 'Activate Workflow' }).click();

  await expect(page.getByRole('alert').filter({ hasText: 'Workflow validation did not confirm live runtime support' })).toBeVisible();
  expect(seen.publishedDefinition).toBeUndefined();
});

test('activate saves supported current graph as published after validation', async ({ page }) => {
  const seen: Record<string, unknown> = {};
  await mockAutomationApis(page, seen, { definition: supportedDefinition });
  await page.goto('/app/automations/builder?id=workflow-1');

  await page.getByRole('button', { name: 'Load' }).click();
  await page.getByRole('button', { name: 'Activate Workflow' }).click();

  await expect(page.getByRole('alert').filter({ hasText: 'Workflow published' })).toBeVisible();
  expect(seen.publishedDefinition).toMatchObject({
    graphVersion: 2,
    initialNodeId: 'entry',
    nodes: {
      entry: { id: 'entry', type: 'ENTRY_TRIGGER', nextNodeId: 'send' },
      send: { id: 'send', type: 'SEND_EMAIL', nextNodeId: 'end' },
      end: { id: 'end', type: 'END' },
    },
  });
});

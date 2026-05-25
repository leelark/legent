import { expect, test, type Page, type Route } from '@playwright/test';

function ok(data: unknown) {
  return {
    success: true,
    data,
    meta: { timestamp: new Date('2026-05-20T00:00:00Z').toISOString() },
  };
}

async function fulfill(route: Route, data: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(data),
  });
}

const activities = [
  {
    id: 'activity-1',
    name: 'Nightly SQL sync',
    activityType: 'SQL_QUERY',
    status: 'ACTIVE',
    dependencyActivityIds: [],
    failurePolicy: 'STOP_ON_FAILURE',
    verification: {
      valid: true,
      errors: [],
      warnings: ['SQL activity has no outputConfig.targetDataExtensionId; result will not populate a data extension.'],
      normalizedConfig: { liveExecutionSupported: true },
    },
  },
  {
    id: 'activity-2',
    name: 'Dormant audience extract',
    activityType: 'EXTRACT',
    status: 'DRAFT',
    dependencyActivityIds: ['activity-1'],
    failurePolicy: 'SKIP_DEPENDENTS',
    verification: {
      valid: true,
      errors: [],
      warnings: [],
      normalizedConfig: { liveExecutionSupported: true, validationOnly: false, sourceType: 'IMPORT_ARTIFACT' },
    },
  },
  {
    id: 'activity-4',
    name: 'Inbound import artifact',
    activityType: 'FILE_DROP',
    status: 'DRAFT',
    dependencyActivityIds: [],
    failurePolicy: 'STOP_ON_FAILURE',
    verification: {
      valid: true,
      errors: [],
      warnings: [],
      normalizedConfig: { liveExecutionSupported: true, validationOnly: false },
    },
  },
  {
    id: 'activity-3',
    name: 'Webhook retry audit',
    activityType: 'WEBHOOK',
    status: 'ACTIVE',
    dependencyActivityIds: [],
    failurePolicy: 'STOP_ON_FAILURE',
    verification: {
      valid: true,
      errors: [],
      warnings: [],
      normalizedConfig: { liveExecutionSupported: true, eventToDispatch: 'automation.activity.completed' },
    },
  },
  {
    id: 'activity-5',
    name: 'Failure notification',
    activityType: 'NOTIFICATION',
    status: 'ACTIVE',
    dependencyActivityIds: [],
    failurePolicy: 'STOP_ON_FAILURE',
    verification: {
      valid: true,
      errors: [],
      warnings: [],
      normalizedConfig: { liveExecutionSupported: true, terminalStatus: 'FAILED' },
    },
  },
];

const dataExtensions = [
  {
    id: 'de-loyalty',
    name: 'Loyalty members',
    sendable: true,
    recordCount: 1200,
    governance: { dataClassification: 'INTERNAL', sourceType: 'AUTOMATION' },
  },
  {
    id: 'de-dormant',
    name: 'Dormant subscribers',
    sendable: false,
    recordCount: 320,
    governance: { dataClassification: 'RESTRICTED', sourceType: 'QUERY' },
  },
];

const users = [
  {
    id: 'user-ops-1',
    email: 'ops@example.com',
    firstName: 'Ops',
    lastName: 'Owner',
    role: 'ADMIN',
    isActive: true,
  },
];

const campaigns = [
  {
    id: 'campaign-approved-1',
    name: 'Approved spring campaign',
    subject: 'Spring release',
    status: 'APPROVED',
  },
  {
    id: 'campaign-draft-1',
    name: 'Draft nurture campaign',
    subject: 'Nurture update',
    status: 'DRAFT',
  },
];

const artifacts: Record<string, Record<string, unknown>> = {
  'artifact-import-1': {
    artifactId: 'artifact-import-1',
    sourceKind: 'UPLOAD',
    status: 'READY',
    displayName: 'Ready CSV import',
    contentType: 'text/csv',
    sizeBytes: 4096,
  },
  'artifact-output-1': {
    artifactId: 'artifact-output-1',
    sourceKind: 'GENERATED',
    status: 'GENERATED',
    displayName: 'Dormant extract output',
    contentType: 'text/csv',
    sizeBytes: 2048,
  },
};

async function mockAutomationStudioApis(
  page: Page,
  seen: Record<string, unknown> = {},
  options: { uiMode?: 'BASIC' | 'ADVANCED'; failVerifyActivityId?: string } = {}
) {
  let dryRunPosted = false;
  let latestRun: Record<string, unknown> | null = null;
  const uiMode = options.uiMode ?? 'ADVANCED';

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
      return fulfill(route, ok({ tenantId: 'tenant-1', userId: 'automation-user', theme: 'light', uiMode, density: 'comfortable', metadata: {} }));
    }
    if (path === '/workflows') {
      return fulfill(route, ok([]));
    }
    if (path === '/data-extensions') {
      return fulfill(route, ok({ content: dataExtensions, page: 0, size: 100, totalElements: dataExtensions.length, totalPages: 1 }));
    }
    if (path === '/users') {
      return fulfill(route, ok(users));
    }
    if (path === '/campaigns') {
      return fulfill(route, ok({
        content: campaigns,
        page: 0,
        size: 100,
        totalElements: campaigns.length,
        totalPages: 1,
      }));
    }
    const artifactMatch = path.match(/^\/automation-studio\/artifacts\/([^/]+)$/);
    if (artifactMatch) {
      const artifact = artifacts[decodeURIComponent(artifactMatch[1])];
      if (!artifact) {
        return fulfill(route, { success: false, error: { message: 'Artifact not found' } }, 404);
      }
      return fulfill(route, ok(artifact));
    }
    if (path === '/automation-studio/activities' && method === 'POST') {
      seen.createdActivity = JSON.parse(request.postData() || '{}');
      return fulfill(route, ok({
        id: 'activity-new',
        name: 'Created activity',
        activityType: seen.createdActivity && typeof seen.createdActivity === 'object' && 'activityType' in seen.createdActivity
          ? (seen.createdActivity as { activityType?: string }).activityType
          : 'SQL_QUERY',
        status: 'DRAFT',
      }));
    }
    if (path === '/automation-studio/activities') {
      return fulfill(route, ok(activities));
    }
    if (path === '/automation-studio/activities/activity-1/runs' && method === 'POST') {
      const runPayload = JSON.parse(request.postData() || '{}') as Record<string, unknown>;
      const runPayloads = Array.isArray(seen.runPayloads) ? seen.runPayloads : [];
      seen.runPayloads = [...runPayloads, runPayload];
      if (runPayload.dryRun !== false) {
        dryRunPosted = true;
        seen.dryRunPayload = runPayload;
        latestRun = {
          id: 'run-after-dry-run',
          activityId: 'activity-1',
          status: 'SUCCEEDED',
          dryRun: true,
          triggerSource: 'MANUAL',
          rowsRead: 9,
          rowsWritten: 8,
          traceId: 'trace-after-dry-run',
          errorCode: null,
          dependencyTrace: { dependencyCount: 0, failurePolicy: 'STOP_ON_FAILURE' },
          startedAt: '2026-05-20T08:30:00Z',
          completedAt: '2026-05-20T08:31:00Z',
        };
        return fulfill(route, ok(latestRun));
      }
      if (runPayload.operatorOverride === true) {
        seen.overridePayload = runPayload;
        latestRun = {
          id: 'run-after-override',
          activityId: 'activity-1',
          status: 'SUCCEEDED',
          dryRun: false,
          triggerSource: 'MANUAL',
          rowsRead: 20,
          rowsWritten: 20,
          traceId: 'trace-after-override',
          errorCode: null,
          operatorOverride: true,
          overrideReason: runPayload.overrideReason,
          dependencyTrace: { dependencyCount: 0, failurePolicy: 'STOP_ON_FAILURE' },
          startedAt: '2026-05-20T08:40:00Z',
          completedAt: '2026-05-20T08:41:00Z',
        };
        return fulfill(route, ok(latestRun));
      }
      seen.liveRunPayload = runPayload;
      latestRun = {
        id: 'run-locked',
        activityId: 'activity-1',
        status: 'LOCKED',
        dryRun: false,
        triggerSource: 'MANUAL',
        rowsRead: 0,
        rowsWritten: 0,
        traceId: 'trace-locked',
        errorCode: 'ACTIVITY_LOCKED',
        errorMessage: 'Another live run is active for this activity.',
        retryAfterSeconds: 420,
        lockedUntil: '2026-05-20T08:37:00Z',
        lockOwnerRunId: 'run-owner-1',
        operatorOverride: false,
        dependencyTrace: { dependencyCount: 0, failurePolicy: 'STOP_ON_FAILURE' },
        startedAt: '2026-05-20T08:30:00Z',
        completedAt: '2026-05-20T08:30:01Z',
      };
      return fulfill(route, ok(latestRun));
    }
    if (path === '/automation-studio/activities/activity-1/runs') {
      return fulfill(route, ok(latestRun || dryRunPosted ? [
        latestRun ?? {
          id: 'run-after-dry-run',
          activityId: 'activity-1',
          status: 'SUCCEEDED',
          dryRun: true,
          triggerSource: 'MANUAL',
          rowsRead: 9,
          rowsWritten: 8,
          traceId: 'trace-after-dry-run',
          errorCode: null,
          dependencyTrace: { dependencyCount: 0, failurePolicy: 'STOP_ON_FAILURE' },
          startedAt: '2026-05-20T08:30:00Z',
          completedAt: '2026-05-20T08:31:00Z',
        },
      ] : [
        {
          id: 'run-1',
          activityId: 'activity-1',
          status: 'SUCCEEDED',
          dryRun: true,
          triggerSource: 'MANUAL',
          rowsRead: 120,
          rowsWritten: 118,
          traceId: 'trace-1',
          errorCode: null,
          dependencyTrace: { dependencyCount: 0, failurePolicy: 'STOP_ON_FAILURE' },
          startedAt: '2026-05-20T07:10:00Z',
          completedAt: '2026-05-20T07:11:00Z',
        },
        {
          id: 'run-2',
          activityId: 'activity-1',
          status: 'FAILED',
          dryRun: false,
          triggerSource: 'SCHEDULED',
          rowsRead: 40,
          rowsWritten: 0,
          traceId: 'trace-2',
          errorCode: 'ACTIVITY_EXECUTION_FAILED',
          dependencyTrace: { dependencyCount: 0, failurePolicy: 'STOP_ON_FAILURE' },
          errorMessage: 'SMTP connection failed',
          startedAt: '2026-05-19T22:00:00Z',
          completedAt: '2026-05-19T22:01:00Z',
        },
      ]));
    }
    const verifyMatch = path.match(/^\/automation-studio\/activities\/([^/]+)\/verify$/);
    if (verifyMatch && method === 'POST') {
      const activityId = verifyMatch[1];
      seen.verifyActivityId = activityId;
      if (options.failVerifyActivityId === activityId) {
        return fulfill(route, {
          success: false,
          error: { message: 'Verification service unavailable' },
        }, 500);
      }
      return fulfill(route, ok({
        valid: activityId === 'activity-1',
        errors: activityId === 'activity-1' ? [] : [`${activityId} is not executable`],
        warnings: activityId === 'activity-1' ? ['No target data extension selected'] : [],
        normalizedConfig: {
          liveExecutionSupported: activityId === 'activity-1',
        },
      }));
    }
    if (path === '/automation-studio/activities/activity-2/runs') {
      return fulfill(route, ok([]));
    }
    if (path === '/automation-studio/activities/activity-3/runs') {
      return fulfill(route, {
        success: false,
        error: { message: 'Run history unavailable' },
      }, 500);
    }
    return fulfill(route, ok([]));
  });
}

test('automation studio shows recent activity runs and refreshes after dry run', async ({ page }) => {
  const seen: Record<string, unknown> = {};
  await mockAutomationStudioApis(page, seen);

  await page.goto('/app/automation');
  await expect(page.getByText('Automation Studio Activities')).toBeVisible();

  await page.getByRole('button', { name: 'Show run history for Nightly SQL sync' }).click();

  const runList = page.getByRole('list', { name: 'Recent runs for Nightly SQL sync' });
  await expect(runList).toBeVisible();
  await expect(runList).toContainText('SUCCEEDED');
  await expect(runList).toContainText('Dry run');
  await expect(runList).toContainText('Rows: 120 read, 118 written');
  await expect(runList).toContainText('Trace: trace-1');
  await expect(runList).toContainText('FAILED');
  await expect(runList).toContainText('Live');
  await expect(runList).toContainText('Error code: ACTIVITY_EXECUTION_FAILED');
  await expect(runList).toContainText('SMTP connection failed');

  await page.getByRole('button', { name: 'Dry Run' }).first().click();

  await expect(runList).toContainText('Rows: 9 read, 8 written');
  expect(seen.dryRunPayload).toMatchObject({ dryRun: true, triggerSource: 'MANUAL' });
  await expect(runList).not.toContainText('SMTP connection failed');
});

test('automation studio surfaces live activity locks and explicit operator override', async ({ page }) => {
  const seen: Record<string, unknown> = {};
  await mockAutomationStudioApis(page, seen);

  await page.goto('/app/automation');

  const sqlRow = page.locator('div.p-4').filter({ hasText: 'Nightly SQL sync' });
  await sqlRow.getByRole('button', { name: 'Run Live' }).click();

  await expect(sqlRow.getByLabel('Latest run result for Nightly SQL sync')).toContainText('LOCKED');
  await expect(sqlRow.getByLabel('Latest run result for Nightly SQL sync')).toContainText('Lock owner: run-owner-1');
  await expect(sqlRow.getByLabel('Latest run result for Nightly SQL sync')).toContainText('retry after 7 minutes');
  expect(seen.liveRunPayload).toMatchObject({
    dryRun: false,
    confirmLiveRun: true,
    triggerSource: 'MANUAL',
  });

  await sqlRow.getByLabel('Override reason for Nightly SQL sync').fill('Ops approved after checking active owner run');
  await sqlRow.getByRole('button', { name: 'Override lock' }).click();

  await expect(sqlRow.getByLabel('Latest run result for Nightly SQL sync')).toContainText('Operator override recorded');
  expect(seen.overridePayload).toMatchObject({
    dryRun: false,
    confirmLiveRun: true,
    operatorOverride: true,
    overrideReason: 'Ops approved after checking active owner run',
  });
});

test('automation studio surfaces capability and verification results per activity', async ({ page }) => {
  const seen: Record<string, unknown> = {};
  await mockAutomationStudioApis(page, seen);

  await page.goto('/app/automation');

  const sqlRow = page.locator('div.p-4').filter({ hasText: 'Nightly SQL sync' });
  await expect(sqlRow).toContainText('Live execution');
  await expect(sqlRow).toContainText('Failure policy: stop on failure');

  const webhookRow = page.locator('div.p-4').filter({ hasText: 'Webhook retry audit' });
  await expect(webhookRow).toContainText('Live execution');
  await expect(webhookRow).toContainText('Verified');
  await expect(webhookRow.getByRole('button', { name: 'Dry Run' })).toBeEnabled();

  const notificationRow = page.locator('div.p-4').filter({ hasText: 'Failure notification' });
  await expect(notificationRow).toContainText('Live execution');
  await expect(notificationRow).toContainText('Verified');

  const extractRow = page.locator('div.p-4').filter({ hasText: 'Dormant audience extract' });
  await expect(extractRow).toContainText('Live execution');
  await expect(extractRow.getByRole('button', { name: 'Dry Run' })).toBeEnabled();

  const fileDropRow = page.locator('div.p-4').filter({ hasText: 'Inbound import artifact' });
  await expect(fileDropRow).toContainText('Live execution');
  await expect(fileDropRow.getByRole('button', { name: 'Dry Run' })).toBeEnabled();

  await sqlRow.getByRole('button', { name: 'Verify' }).click();
  await expect(page.getByLabel('Verification result for Nightly SQL sync')).toContainText('Verified');
  await expect(page.getByLabel('Verification result for Nightly SQL sync')).toContainText('No target data extension selected');
  expect(seen.verifyActivityId).toBe('activity-1');
});

test('automation studio scopes action errors to the activity row', async ({ page }) => {
  await mockAutomationStudioApis(page, {}, { failVerifyActivityId: 'activity-1' });

  await page.goto('/app/automation');

  const sqlRow = page.locator('div.p-4').filter({ hasText: 'Nightly SQL sync' });
  const extractRow = page.locator('div.p-4').filter({ hasText: 'Dormant audience extract' });

  await sqlRow.getByRole('button', { name: 'Verify' }).click();
  await expect(sqlRow).toContainText('Verification service unavailable');
  await expect(extractRow).not.toContainText('Verification service unavailable');
});

test('basic mode hides advanced automation activity controls and skips activity payloads', async ({ page }) => {
  const seen: Record<string, unknown> = {};
  await mockAutomationStudioApis(page, seen, { uiMode: 'BASIC' });

  await page.goto('/app/automation');
  await expect(page.getByText('Automation Studio Activities')).toBeVisible();

  await expect(page.getByRole('button', { name: 'New Activity' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Verify' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Dry Run' })).toHaveCount(0);
  await expect(page.locator('[data-mode-feature="automation.workflow.activity-authoring"]')).toHaveCount(0);
  await expect(page.locator('[data-mode-feature="automation.workflow.activity-execution"]')).toHaveCount(0);

  await page.getByRole('button', { name: 'Show run history for Nightly SQL sync' }).click();
  await expect(page.getByRole('list', { name: 'Recent runs for Nightly SQL sync' })).toBeVisible();
  expect(seen.createdActivity).toBeUndefined();
});

test('advanced mode exposes automation activity controls and creates explicit payloads', async ({ page }) => {
  const seen: Record<string, unknown> = {};
  await mockAutomationStudioApis(page, seen, { uiMode: 'ADVANCED' });

  await page.goto('/app/automation');

  await expect(page.getByRole('button', { name: 'New Activity' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Verify' }).first()).toBeVisible();
  await expect(page.getByRole('button', { name: 'Dry Run' }).first()).toBeVisible();
  await expect(page.locator('[data-mode-feature="automation.workflow.activity-authoring"]')).not.toHaveCount(0);
  await expect(page.locator('[data-mode-feature="automation.workflow.activity-execution"]')).not.toHaveCount(0);

  await page.getByRole('button', { name: 'New Activity' }).click();
  await page.getByLabel('Activity Name').fill('Subscriber import');
  await page.getByLabel('Activity Type').selectOption('IMPORT');
  await page.getByRole('textbox', { name: 'Import Artifact', exact: true }).fill('artifact-import-1');
  await page.getByRole('button', { name: 'Verify Import Artifact' }).click();
  await expect(page.getByText('Ready CSV import')).toBeVisible();
  await page.getByLabel('Import Target Type').selectOption('DATA_EXTENSION');
  await page.getByLabel('Target Data Extension').selectOption('de-loyalty');
  await expect(page.getByText('Loyalty members', { exact: true })).toBeVisible();
  await expect(page.getByLabel('Scoped Artifact ID')).toHaveCount(0);
  await expect(page.getByLabel('Target Data Extension ID')).toHaveCount(0);
  await page.getByLabel('Email Field Mapping').fill('Email');
  await page.getByRole('button', { name: 'Create', exact: true }).click();

  await expect.poll(() => seen.createdActivity).toBeTruthy();
  expect(seen.createdActivity).toMatchObject({
    name: 'Subscriber import',
    activityType: 'IMPORT',
    status: 'DRAFT',
    failurePolicy: 'STOP_ON_FAILURE',
    inputConfig: {
      artifactId: 'artifact-import-1',
      targetType: 'DATA_EXTENSION',
      targetId: 'de-loyalty',
      fieldMapping: { email: 'Email' },
    },
  });
});

test('advanced mode selects extract source data extension and output artifact', async ({ page }) => {
  const seen: Record<string, unknown> = {};
  await mockAutomationStudioApis(page, seen, { uiMode: 'ADVANCED' });

  await page.goto('/app/automation');
  await page.getByRole('button', { name: 'New Activity' }).click();
  await page.getByLabel('Activity Name').fill('Dormant extract');
  await page.getByLabel('Activity Type').selectOption('EXTRACT');
  await page.getByLabel('Source Type').selectOption('DATA_EXTENSION');
  await page.getByLabel('Source Data Extension').selectOption('de-loyalty');
  await page.getByRole('textbox', { name: 'Output Artifact' }).fill('artifact-output-1');
  await page.getByRole('button', { name: 'Verify Output Artifact' }).click();
  await expect(page.getByText('Dormant extract output')).toBeVisible();
  await page.getByRole('button', { name: 'Create', exact: true }).click();

  await expect.poll(() => seen.createdActivity).toBeTruthy();
  expect(seen.createdActivity).toMatchObject({
    name: 'Dormant extract',
    activityType: 'EXTRACT',
    inputConfig: {
      sourceType: 'DATA_EXTENSION',
      sourceId: 'de-loyalty',
    },
    outputConfig: { artifactId: 'artifact-output-1' },
  });
});

test('advanced mode creates artifact-backed extract payloads with backend source types', async ({ page }) => {
  const seen: Record<string, unknown> = {};
  await mockAutomationStudioApis(page, seen, { uiMode: 'ADVANCED' });

  await page.goto('/app/automation');
  await page.getByRole('button', { name: 'New Activity' }).click();
  await page.getByLabel('Activity Name').fill('Artifact extract');
  await page.getByLabel('Activity Type').selectOption('EXTRACT');

  const sourceType = page.getByLabel('Source Type');
  await expect(sourceType.locator('option[value="ARTIFACT"]')).toHaveCount(0);
  await expect(sourceType.locator('option[value="IMPORT_ARTIFACT"]')).toHaveCount(1);
  await expect(sourceType.locator('option[value="GENERATED_EXTRACT"]')).toHaveCount(1);

  await sourceType.selectOption('IMPORT_ARTIFACT');
  await page.getByRole('textbox', { name: 'Source Artifact' }).fill('artifact-import-1');
  await page.getByRole('button', { name: 'Verify Source Artifact' }).click();
  await expect(page.getByText('Ready CSV import')).toBeVisible();
  await page.getByRole('textbox', { name: 'Output Artifact' }).fill('artifact-output-1');
  await page.getByRole('button', { name: 'Create', exact: true }).click();

  await expect.poll(() => seen.createdActivity).toBeTruthy();
  expect(seen.createdActivity).toMatchObject({
    name: 'Artifact extract',
    activityType: 'EXTRACT',
    inputConfig: {
      sourceType: 'IMPORT_ARTIFACT',
      sourceArtifactId: 'artifact-import-1',
    },
    outputConfig: { artifactId: 'artifact-output-1' },
  });
});

test('advanced mode creates file-drop movement payloads with output artifacts', async ({ page }) => {
  const seen: Record<string, unknown> = {};
  await mockAutomationStudioApis(page, seen, { uiMode: 'ADVANCED' });

  await page.goto('/app/automation');
  await page.getByRole('button', { name: 'New Activity' }).click();
  await page.getByLabel('Activity Name').fill('Inbound file movement');
  await page.getByLabel('Activity Type').selectOption('FILE_DROP');
  await page.getByRole('textbox', { name: 'File-drop Artifact' }).fill('artifact-import-1');
  await page.getByRole('button', { name: 'Verify File-drop Artifact' }).click();
  await expect(page.getByText('Ready CSV import')).toBeVisible();
  await page.getByRole('textbox', { name: 'Output Artifact' }).fill('artifact-output-1');
  await page.getByRole('button', { name: 'Create', exact: true }).click();

  await expect.poll(() => seen.createdActivity).toBeTruthy();
  expect(seen.createdActivity).toMatchObject({
    name: 'Inbound file movement',
    activityType: 'FILE_DROP',
    inputConfig: { artifactId: 'artifact-import-1' },
    outputConfig: { artifactId: 'artifact-output-1' },
  });
});

test('advanced mode creates guarded webhook activity payloads', async ({ page }) => {
  const seen: Record<string, unknown> = {};
  await mockAutomationStudioApis(page, seen, { uiMode: 'ADVANCED' });

  await page.goto('/app/automation');
  await page.getByRole('button', { name: 'New Activity' }).click();
  await page.getByLabel('Activity Name').fill('Webhook event');
  await page.getByLabel('Activity Type').selectOption('WEBHOOK');
  await page.getByLabel('Platform Event Type').fill('automation.activity.completed');
  await page.getByLabel('Webhook Auth Ref').fill('whref-1');
  await page.getByLabel('Webhook Payload JSON').fill('{"status":"completed"}');
  await page.getByRole('button', { name: 'Create', exact: true }).click();

  await expect.poll(() => seen.createdActivity).toBeTruthy();
  expect(seen.createdActivity).toMatchObject({
    name: 'Webhook event',
    activityType: 'WEBHOOK',
    inputConfig: {
      eventToDispatch: 'automation.activity.completed',
      webhookAuthRef: 'whref-1',
      data: { status: 'completed' },
    },
  });
  expect(JSON.stringify(seen.createdActivity)).not.toContain('https://');
});

test('advanced mode creates terminal notification activity payloads', async ({ page }) => {
  const seen: Record<string, unknown> = {};
  await mockAutomationStudioApis(page, seen, { uiMode: 'ADVANCED' });

  await page.goto('/app/automation');
  await page.getByRole('button', { name: 'New Activity' }).click();
  await page.getByLabel('Activity Name').fill('Notify operator');
  await page.getByLabel('Activity Type').selectOption('NOTIFICATION');
  await page.getByLabel('Recipient User').selectOption('user-ops-1');
  await expect(page.getByText('ops@example.com', { exact: true })).toBeVisible();
  await expect(page.getByLabel('Recipient User ID')).toHaveCount(0);
  await page.getByLabel('Title').fill('Automation failed');
  await page.getByLabel('Message').fill('Run failed');
  await page.getByLabel('Notification Severity').selectOption('ERROR');
  await page.getByLabel('Notification Terminal Status').selectOption('FAILED');
  await page.getByRole('button', { name: 'Create', exact: true }).click();

  await expect.poll(() => seen.createdActivity).toBeTruthy();
  expect(seen.createdActivity).toMatchObject({
    name: 'Notify operator',
    activityType: 'NOTIFICATION',
    inputConfig: {
      userId: 'user-ops-1',
      title: 'Automation failed',
      message: 'Run failed',
      severity: 'ERROR',
      terminalStatus: 'FAILED',
    },
  });
});

test('advanced mode creates send-email campaign handoff payloads', async ({ page }) => {
  const seen: Record<string, unknown> = {};
  await mockAutomationStudioApis(page, seen, { uiMode: 'ADVANCED' });

  await page.goto('/app/automation');
  await page.getByRole('button', { name: 'New Activity' }).click();
  await page.getByLabel('Activity Name').fill('Send approved campaign');
  await page.getByLabel('Activity Type').selectOption('SEND_EMAIL');
  await page.getByLabel('Campaign').selectOption('campaign-approved-1');
  await expect(page.getByText('Spring release')).toBeVisible();
  await page.getByRole('button', { name: 'Create', exact: true }).click();

  await expect.poll(() => seen.createdActivity).toBeTruthy();
  expect(seen.createdActivity).toMatchObject({
    name: 'Send approved campaign',
    activityType: 'SEND_EMAIL',
    inputConfig: { campaignId: 'campaign-approved-1' },
    outputConfig: {},
  });
  expect(JSON.stringify(seen.createdActivity)).not.toContain('recipients');
});

test('advanced mode blocks unsafe file activity references before posting', async ({ page }) => {
  const seen: Record<string, unknown> = {};
  await mockAutomationStudioApis(page, seen, { uiMode: 'ADVANCED' });

  await page.goto('/app/automation');
  await page.getByRole('button', { name: 'New Activity' }).click();
  await page.getByLabel('Activity Name').fill('Unsafe import');
  await page.getByLabel('Activity Type').selectOption('IMPORT');
  await page.getByRole('textbox', { name: 'Import Artifact', exact: true }).fill('https://storage.example.com/import.csv');
  await page.getByLabel('Email Field Mapping').fill('Email');
  await page.getByRole('button', { name: 'Create', exact: true }).click();

  await expect(page.getByText('Import activity requires an opaque scoped artifact ID')).toBeVisible();
  expect(seen.createdActivity).toBeUndefined();
});

test('advanced mode blocks unsafe webhook event references before posting', async ({ page }) => {
  const seen: Record<string, unknown> = {};
  await mockAutomationStudioApis(page, seen, { uiMode: 'ADVANCED' });

  await page.goto('/app/automation');
  await page.getByRole('button', { name: 'New Activity' }).click();
  await page.getByLabel('Activity Name').fill('Unsafe webhook');
  await page.getByLabel('Activity Type').selectOption('WEBHOOK');
  await page.getByLabel('Platform Event Type').fill('https://example.com/webhook');
  await page.getByRole('button', { name: 'Create', exact: true }).click();

  await expect(page.getByText('Webhook activity requires an automation.* platform event type')).toBeVisible();
  expect(seen.createdActivity).toBeUndefined();
});

test('automation studio scopes empty and error run-history states to each activity', async ({ page }) => {
  await mockAutomationStudioApis(page);

  await page.goto('/app/automation');
  await page.getByRole('button', { name: 'Show run history for Dormant audience extract' }).click();
  await expect(page.getByText('No recent runs recorded.')).toBeVisible();

  await page.getByRole('button', { name: 'Show run history for Webhook retry audit' }).click();
  await expect(page.getByText('Run history unavailable')).toBeVisible();
  await expect(page.getByText('No recent runs recorded.')).toBeVisible();
});

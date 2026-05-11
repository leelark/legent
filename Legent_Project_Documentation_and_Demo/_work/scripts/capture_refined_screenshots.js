const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '..', '..');
const outDir = path.join(root, 'assets', 'screenshots');
const evidenceDir = path.join(root, 'evidence');
fs.mkdirSync(outDir, { recursive: true });
fs.mkdirSync(evidenceDir, { recursive: true });

const baseUrl = process.env.LEGENT_BASE_URL || 'http://127.0.0.1:3000';
const logPath = path.join(evidenceDir, 'refined-screenshot-capture.log');
fs.writeFileSync(logPath, '');

function log(line) {
  const msg = `[${new Date().toISOString()}] ${line}`;
  console.log(msg);
  fs.appendFileSync(logPath, `${msg}\n`);
}

const now = new Date().toISOString();
const tenantId = '11111111-1111-4111-8111-111111111111';
const workspaceId = '22222222-2222-4222-8222-222222222222';
const environmentId = '33333333-3333-4333-8333-333333333333';
const userId = '44444444-4444-4444-8444-444444444444';

const templates = [
  { id: 'tpl-welcome', name: 'Welcome Series - Hero Offer', subject: 'Welcome to Legent', status: 'APPROVED', category: 'onboarding', updatedAt: now },
  { id: 'tpl-spring', name: 'Spring Launch Announcement', subject: 'Early access opens today', status: 'DRAFT', category: 'campaign', updatedAt: now },
  { id: 'tpl-winback', name: 'Dormant Subscriber Winback', subject: 'A quick update for you', status: 'APPROVED', category: 'retention', updatedAt: now },
];

const lists = [
  { id: 'list-vip', name: 'VIP Customers', description: 'High-value opted-in buyers', subscriberCount: 8420, status: 'ACTIVE', createdAt: now, updatedAt: now },
  { id: 'list-news', name: 'Newsletter Subscribers', description: 'Weekly editorial audience', subscriberCount: 28750, status: 'ACTIVE', createdAt: now, updatedAt: now },
];

const segments = [
  { id: 'seg-engaged', name: 'Engaged last 30 days', description: 'Opened or clicked recently', audienceSize: 12680, status: 'ACTIVE', criteria: { engagement: 'recent' }, createdAt: now, updatedAt: now },
  { id: 'seg-risk', name: 'At-risk premium customers', description: 'Premium buyers without recent engagement', audienceSize: 2140, status: 'ACTIVE', criteria: { churnRisk: 'high' }, createdAt: now, updatedAt: now },
];

const subscribers = [
  { id: 'sub-001', email: 'mira.chen@example.com', firstName: 'Mira', lastName: 'Chen', status: 'SUBSCRIBED', listIds: ['list-vip'], attributes: { tier: 'Gold', city: 'San Francisco' }, createdAt: now, updatedAt: now },
  { id: 'sub-002', email: 'jon.martin@example.com', firstName: 'Jon', lastName: 'Martin', status: 'SUBSCRIBED', listIds: ['list-news'], attributes: { tier: 'Silver', city: 'Austin' }, createdAt: now, updatedAt: now },
  { id: 'sub-003', email: 'aisha.rao@example.com', firstName: 'Aisha', lastName: 'Rao', status: 'SUBSCRIBED', listIds: ['list-vip', 'list-news'], attributes: { tier: 'Platinum', city: 'Bengaluru' }, createdAt: now, updatedAt: now },
];

const campaigns = [
  {
    id: 'cmp-spring',
    name: 'Spring Launch',
    description: 'Coordinated product announcement with approval gates.',
    status: 'READY',
    templateId: 'tpl-spring',
    templateName: 'Spring Launch Announcement',
    subject: 'Early access opens today',
    audienceSize: 12680,
    sentCount: 9680,
    deliveredCount: 9384,
    openCount: 4129,
    clickCount: 932,
    bounceCount: 84,
    createdAt: now,
    updatedAt: now,
    scheduledAt: new Date(Date.now() + 86400000).toISOString(),
  },
  {
    id: 'cmp-welcome',
    name: 'Welcome Journey',
    description: 'Onboarding drip for new subscribers.',
    status: 'SENT',
    templateId: 'tpl-welcome',
    templateName: 'Welcome Series - Hero Offer',
    subject: 'Welcome to Legent',
    audienceSize: 8420,
    sentCount: 8420,
    deliveredCount: 8338,
    openCount: 3840,
    clickCount: 1142,
    bounceCount: 82,
    createdAt: now,
    updatedAt: now,
  },
];

const emails = [
  { id: 'em-001', name: 'Founder welcome', subject: 'Welcome to Legent', status: 'DRAFT', updatedAt: now },
  { id: 'em-002', name: 'Spring launch creative', subject: 'Early access opens today', status: 'APPROVED', updatedAt: now },
];

const workflows = [
  { id: 'wf-welcome', name: 'Welcome nurture', status: 'ACTIVE', triggerType: 'SUBSCRIBER_CREATED', stepCount: 5, updatedAt: now },
  { id: 'wf-winback', name: 'Winback journey', status: 'DRAFT', triggerType: 'SEGMENT_ENTERED', stepCount: 4, updatedAt: now },
];

const imports = [
  { id: 'imp-001', fileName: 'vip-customers.csv', status: 'COMPLETED', totalRows: 8400, successfulRows: 8372, failedRows: 28, createdAt: now },
  { id: 'imp-002', fileName: 'newsletter-may.csv', status: 'VALIDATING', totalRows: 12000, successfulRows: 0, failedRows: 0, createdAt: now },
];

const domains = [
  { id: 'dom-001', domainName: 'mail.legent.example', domain: 'mail.legent.example', status: 'VERIFIED', spfVerified: true, dkimVerified: true, dmarcVerified: true, reputationScore: 94 },
  { id: 'dom-002', domainName: 'news.legent.example', domain: 'news.legent.example', status: 'PENDING', spfVerified: true, dkimVerified: false, dmarcVerified: false, reputationScore: 78 },
];

const deliveryMessages = [
  { id: 'msg-001', messageId: 'msg-001', email: 'mira.chen@example.com', subject: 'Early access opens today', status: 'SENT', attemptCount: 1, failureClass: '', provider: 'SES', createdAt: now },
  { id: 'msg-002', messageId: 'msg-002', email: 'jon.martin@example.com', subject: 'Welcome to Legent', status: 'FAILED', attemptCount: 2, failureClass: 'BOUNCE', provider: 'SES', createdAt: now },
];

const adminUsers = [
  { id: 'usr-1', firstName: 'Mira', lastName: 'Chen', email: 'mira.chen@example.com', role: 'PLATFORM_ADMIN', roles: ['PLATFORM_ADMIN'], isActive: true, lastLoginAt: now },
  { id: 'usr-2', firstName: 'Jon', lastName: 'Martin', email: 'jon.martin@example.com', role: 'CAMPAIGN_MANAGER', roles: ['CAMPAIGN_MANAGER'], isActive: true, lastLoginAt: now },
  { id: 'usr-3', firstName: 'Aisha', lastName: 'Rao', email: 'aisha.rao@example.com', role: 'ANALYST', roles: ['ANALYST'], isActive: false, lastLoginAt: now },
];

const syncEvents = [
  { id: 'sync-1', event_type: 'ROLE_POLICY_SYNCED', eventType: 'ROLE_POLICY_SYNCED', status: 'SUCCESS', module: 'identity', created_at: now, createdAt: now },
  { id: 'sync-2', event_type: 'CONFIG_PROPAGATED', eventType: 'CONFIG_PROPAGATED', status: 'SUCCESS', module: 'delivery', created_at: now, createdAt: now },
  { id: 'sync-3', event_type: 'AUDIT_INDEXED', eventType: 'AUDIT_INDEXED', status: 'SUCCESS', module: 'audit', created_at: now, createdAt: now },
];

function pageData(content, extra = {}) {
  return {
    success: true,
    data: {
      content,
      data: content,
      totalElements: content.length,
      totalPages: 1,
      page: 0,
      size: content.length || 20,
      first: true,
      last: true,
      ...extra,
    },
  };
}

function ok(data) {
  return { success: true, data };
}

function metricData() {
  return ok({
    sent: 84200,
    delivered: 82040,
    opens: 36480,
    clicks: 9320,
    bounces: 410,
    unsubscribes: 73,
    openRate: 44.5,
    clickRate: 11.3,
    deliveryRate: 97.4,
  });
}

function publicContent(pathname) {
  if (pathname.includes('/features')) return ok({ heroTitle: 'Every capability works together', sections: [] });
  if (pathname.includes('/modules')) return ok({ heroTitle: 'Six operating studios', modules: [] });
  if (pathname.includes('/pricing')) return ok({ heroTitle: 'Pricing responds to real operating scale', plans: [] });
  return ok({});
}

function mockResponse(requestUrl, method) {
  const url = new URL(requestUrl);
  const p = url.pathname.replace(/^\/api\/v1/, '');

  if (p === '/auth/session') {
    return ok({
      status: 'success',
      userId,
      email: 'admin@legent.example',
      name: 'Admin User',
      roles: ['ADMIN', 'MARKETER'],
      tenantId,
      workspaceId,
      environmentId,
    });
  }
  if (p === '/auth/context/switch') {
    return ok({ status: 'success', tenantId, workspaceId, environmentId });
  }
  if (p === '/auth/contexts') {
    return ok([
      {
        tenantId,
        tenantName: 'Legent Demo Tenant',
        workspaceId,
        workspaceName: 'Lifecycle Marketing',
        environmentId,
        environmentName: 'Production',
        role: 'ADMIN',
        default: true,
      },
    ]);
  }
  if (p === '/users/preferences') return ok({ theme: 'light', compactMode: false, timezone: 'Asia/Calcutta' });
  if (p.startsWith('/public/')) return publicContent(p);

  if (p === '/emails/recent') return ok(emails);
  if (p.startsWith('/emails')) return pageData(emails);
  if (p.startsWith('/templates/') && p !== '/templates/seed') return ok({
    ...templates[0],
    blocks: [
      { id: 'b1', type: 'hero', content: 'Launch faster with governed lifecycle messaging.' },
      { id: 'b2', type: 'button', content: 'Start campaign' },
    ],
    versions: [{ id: 'v1', version: 3, status: 'APPROVED', createdAt: now }],
    approvals: [{ id: 'app-1', status: 'APPROVED', reviewer: 'Marketing Ops', createdAt: now }],
  });
  if (p.startsWith('/templates')) return pageData(templates);

  if (p === '/subscribers/count') return ok({ count: 42800 });
  if (p.startsWith('/subscribers')) return pageData(subscribers, { totalElements: 42800 });
  if (p.startsWith('/lists')) return pageData(lists);
  if (p.startsWith('/segments')) return pageData(segments);
  if (p.startsWith('/data-extensions')) return pageData([
    { id: 'de-orders', name: 'Orders', key: 'orders', fields: 12, rowCount: 128400, updatedAt: now },
    { id: 'de-loyalty', name: 'Loyalty Profile', key: 'loyalty_profile', fields: 9, rowCount: 42800, updatedAt: now },
  ]);
  if (p.startsWith('/imports')) return pageData(imports);

  if (p.startsWith('/campaigns/cmp-spring/tracking') || p.startsWith('/campaigns/cmp-spring/analytics')) {
    return ok({
      campaign: campaigns[0],
      summary: { delivered: 9384, opens: 4129, clicks: 932, bounces: 84, complaints: 3 },
      timeline: [
        { time: '09:00', event: 'Launched', count: 12680 },
        { time: '09:20', event: 'Delivered', count: 9384 },
        { time: '10:05', event: 'Opened', count: 4129 },
      ],
      jobs: [{ id: 'job-001', status: 'RUNNING', progress: 76, startedAt: now }],
      gates: [
        { name: 'Approval', status: 'PASS' },
        { name: 'Audience', status: 'PASS' },
        { name: 'Budget', status: 'PASS' },
        { name: 'Frequency cap', status: 'WARN' },
      ],
      variants: [
        { name: 'A', openRate: 41.2, clickRate: 8.7 },
        { name: 'B', openRate: 46.8, clickRate: 10.2 },
      ],
    });
  }
  if (p.startsWith('/campaigns/cmp-spring')) return ok(campaigns[0]);
  if (p.startsWith('/campaigns')) return pageData(campaigns);

  if (p.startsWith('/launch')) return ok({
    campaigns: [campaigns[0]],
    readiness: { approval: 'PASS', audience: 'PASS', deliverability: 'PASS', compliance: 'WARN' },
  });
  if (p.startsWith('/tracking')) return metricData();
  if (p.startsWith('/analytics')) return ok({
    metrics: metricData().data,
    campaigns,
    series: [
      { label: 'Mon', opens: 5200, clicks: 1180 },
      { label: 'Tue', opens: 6100, clicks: 1320 },
      { label: 'Wed', opens: 5840, clicks: 1270 },
    ],
  });

  if (p === '/workflows' || p.startsWith('/automation')) return ok(workflows);
  if (p.includes('/definitions') || p.includes('/versions') || p.includes('/runs') || p.includes('/schedules')) return ok([]);
  if (p.startsWith('/workflows/')) return ok(workflows.find((wf) => p.includes(wf.id)) || workflows[0]);
  if (p.startsWith('/automations')) return ok(workflows);

  if (p === '/deliverability/domains') return ok(domains);
  if (p === '/delivery/queue/stats') {
    return ok({ pending: 184, processing: 42, sent: 82040, failed: 19, replayPending: 27, replayFailed: 3, unhealthyProviders: 1, updatedAt: now });
  }
  if (p.startsWith('/delivery/messages')) return ok(deliveryMessages);
  if (p === '/delivery/diagnostics/failures') return ok({ failedRecent: 19, byFailureClass: { BOUNCE: 11, TIMEOUT: 5, THROTTLED: 3 }, replayQueueDepth: 27 });
  if (p === '/delivery/warmup/status') return ok({ activeProviders: 3, healthyProviders: 2, degradedProviders: 1, unhealthyProviders: 0, readiness: 91, updatedAt: now });
  if (p.startsWith('/deliverability') || p.startsWith('/delivery')) {
    return ok({
      score: 91,
      queueDepth: 184,
      replayReady: 27,
      domains,
      messages: deliveryMessages,
      recent: deliveryMessages,
    });
  }
  if (p.startsWith('/domains')) return pageData(domains);

  if (p === '/users') return ok(adminUsers);
  if (p.startsWith('/users/')) return ok(adminUsers[0]);
  if (p === '/admin/operations/sync-events') return ok(syncEvents);
  if (p === '/admin/operations/access') return ok({
    roles: [
      { roleKey: 'PLATFORM_ADMIN', displayName: 'Platform Admin', permissions: ['admin:*', 'campaign:*', 'delivery:*'] },
      { roleKey: 'CAMPAIGN_MANAGER', displayName: 'Campaign Manager', permissions: ['campaign:*', 'template:read', 'audience:read'] },
    ],
    permissionGroups: [
      { key: 'delivery', permissions: ['delivery:read', 'delivery:retry', 'delivery:replay'] },
      { key: 'analytics', permissions: ['analytics:read', 'reports:export'] },
    ],
    delegatedAccess: [{ principal: 'Campaign Manager', scope: 'workspace', expiresAt: now }],
    propagation: syncEvents,
  });
  if (p === '/admin/operations/dashboard') return ok({
    generatedAt: now,
    health: { status: 'OPERATIONAL' },
    stats: { workspaces: 4, memberships: 37, runtimeConfigs: 58, auditEvents24h: 126 },
    modules: [
      { key: 'identity', label: 'Identity', description: 'Auth, roles, tenant context', status: 'OPERATIONAL', configs: 7, auditEvents: 22, syncEvents: 6 },
      { key: 'delivery', label: 'Delivery', description: 'Queue, provider, replay controls', status: 'OPERATIONAL', configs: 11, auditEvents: 38, syncEvents: 9 },
      { key: 'campaign', label: 'Campaign', description: 'Launch gates and lifecycle sends', status: 'OPERATIONAL', configs: 14, auditEvents: 44, syncEvents: 8 },
      { key: 'analytics', label: 'Analytics', description: 'Tracking and attribution runtime', status: 'OPERATIONAL', configs: 6, auditEvents: 18, syncEvents: 5 },
    ],
    jobs: [{ id: 'job-1', name: 'Config propagation', status: 'SUCCESS', createdAt: now }],
    alerts: [
      { title: 'One domain needs DMARC review', detail: 'news.legent.example is pending DMARC verification.', tone: 'warning' },
    ],
    recommendedActions: [
      { key: 'delivery-domain', title: 'Complete DMARC verification', detail: 'Finish DNS alignment before high-volume launch.', action: 'Review' },
      { key: 'role-review', title: 'Review delegated admin access', detail: 'One campaign role has elevated delivery controls.', action: 'Audit' },
    ],
    activity: [
      { action: 'Updated campaign policy', actor: 'Mira Chen', createdAt: now },
      { action: 'Synced provider health', actor: 'System', createdAt: now },
    ],
    syncEvents,
  });
  if (p === '/core/audit') return ok([
    { id: 'audit-1', actor: 'Mira Chen', action: 'Updated campaign policy', status: 'SUCCESS', createdAt: now },
    { id: 'audit-2', actor: 'System', action: 'Synced provider health', status: 'SUCCESS', createdAt: now },
  ]);
  if (p === '/admin/settings' || p === '/admin/configs') return ok([
    { id: 'cfg-1', key: 'campaign.launch.approvalRequired', value: 'true', description: 'Require approval before launch', module: 'campaign', category: 'CAMPAIGN', configType: 'BOOLEAN', scope: 'WORKSPACE', editable: true },
    { id: 'cfg-2', key: 'delivery.replay.maxAttempts', value: '3', description: 'Replay retry ceiling', module: 'delivery', category: 'DELIVERY', configType: 'NUMBER', scope: 'WORKSPACE', editable: true },
  ]);
  if (p === '/platform/webhooks') return ok([
    { id: 'wh-1', name: 'Warehouse Sync', endpointUrl: 'https://hooks.example.com/events', eventsSubscribed: JSON.stringify(['campaign.sent', 'delivery.failed']), isActive: true },
  ]);
  if (p === '/providers/health' || p === '/providers') return ok([
    { id: 'ses', name: 'Amazon SES', status: 'HEALTHY', latencyMs: 138 },
    { id: 'sendgrid', name: 'SendGrid', status: 'DEGRADED', latencyMs: 276 },
  ]);
  if (p.startsWith('/admin') || p.startsWith('/platform')) return ok({
    users: adminUsers,
    tenants: [{ id: tenantId, name: 'Legent Demo Tenant', status: 'ACTIVE' }],
    workspaces: [{ id: workspaceId, name: 'Lifecycle Marketing', status: 'ACTIVE' }],
    audit: [{ id: 'audit-1', actor: 'Admin User', action: 'Updated campaign policy', createdAt: now }],
  });

  log(`fallback ${method} ${p}`);
  return ok({ content: [], data: [], totalElements: 0 });
}

async function installRoutes(page) {
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const body = mockResponse(request.url(), request.method());
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(body),
      headers: { 'access-control-allow-origin': '*' },
    });
  });
}

async function seedContext(context) {
  await context.addInitScript(({ tenantId, workspaceId, environmentId, userId }) => {
    localStorage.setItem('legent_user_id', userId);
    localStorage.setItem('legent_roles', JSON.stringify(['ADMIN', 'MARKETER']));
    localStorage.setItem('legent_tenant_id', tenantId);
    localStorage.setItem('legent_workspace_id', workspaceId);
    localStorage.setItem('legent_environment_id', environmentId);
    localStorage.setItem('legent_theme', 'light');
    localStorage.setItem('legent_ui_mode', 'comfortable');
  }, { tenantId, workspaceId, environmentId, userId });
}

const screens = [
  ['01_public_home', '/', 'Run lifecycle email'],
  ['02_public_features', '/features', 'Every capability works inside one governed operating model'],
  ['03_public_modules', '/modules', 'Six operating studios, one shared runtime fabric'],
  ['04_public_pricing', '/pricing', 'Pricing responds to how your operation matures'],
  ['05_signup', '/signup', 'Create your operating workspace'],
  ['06_onboarding', '/onboarding', 'Finish workspace readiness'],
  ['07_email_studio', '/app/email', 'Email Studio'],
  ['08_template_studio', '/app/email/templates', 'Template Studio'],
  ['09_audience_overview', '/app/audience', 'Audience'],
  ['10_subscriber_manager', '/app/audience/subscribers', 'Subscribers'],
  ['11_campaign_studio', '/app/campaigns', 'Campaign Studio'],
  ['12_campaign_wizard', '/app/campaigns/new', 'Campaign Wizard'],
  ['13_launch_orchestration', '/app/launch', 'One-action launch orchestration'],
  ['14_campaign_tracking', '/app/campaigns/cmp-spring/tracking', 'Spring Launch'],
  ['15_automation_builder', '/app/automation', 'Automation'],
  ['16_deliverability', '/app/deliverability', 'Delivery Studio'],
  ['17_analytics', '/app/analytics', 'Analytics Overview'],
  ['18_admin_console', '/app/admin', 'Govern users, runtime policy'],
  ['19_platform_settings', '/app/settings/platform', 'Settings that reshape the shell'],
];

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    deviceScaleFactor: 1,
    recordVideo: { dir: path.join(root, 'evidence', 'source-recordings'), size: { width: 1440, height: 1000 } },
  });
  await seedContext(context);
  const page = await context.newPage();
  await installRoutes(page);

  page.on('console', (msg) => {
    if (['error', 'warning'].includes(msg.type())) log(`browser ${msg.type()}: ${msg.text().slice(0, 500)}`);
  });
  page.on('pageerror', (err) => log(`pageerror: ${err.message}`));
  page.on('requestfailed', (req) => log(`requestfailed: ${req.url()} ${req.failure()?.errorText || ''}`));

  const manifest = [];
  for (const [name, route, expected] of screens) {
    const target = `${baseUrl}${route}`;
    log(`capture ${name} ${target}`);
    await page.goto(target, { waitUntil: 'domcontentloaded', timeout: 240000 });
    await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
    await page.waitForFunction(
      (text) => document.body && document.body.innerText && document.body.innerText.includes(text),
      expected,
      { timeout: 90000 },
    ).catch(async (err) => {
      const text = await page.evaluate(() => document.body?.innerText?.slice(0, 4000) || '');
      log(`expected text missing for ${name}: ${expected}; body=${JSON.stringify(text)}`);
      throw err;
    });
    await page.waitForTimeout(1500);
    const file = path.join(outDir, `${name}.png`);
    await page.screenshot({ path: file, fullPage: true });
    const bodyText = await page.evaluate(() => document.body.innerText);
    manifest.push({ name, route, expected, file, textSample: bodyText.slice(0, 500) });
  }

  fs.writeFileSync(path.join(outDir, 'manifest.json'), JSON.stringify(manifest, null, 2));
  await page.close();
  await context.close();
  await browser.close();
  log(`done ${manifest.length} screenshots`);
})().catch((err) => {
  log(`fatal ${err.stack || err.message}`);
  process.exit(1);
});

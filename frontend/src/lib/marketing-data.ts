import {
  BarChart3,
  Bot,
  CheckCircle2,
  Database,
  GitBranch,
  Globe2,
  Headphones,
  LayoutTemplate,
  LockKeyhole,
  MailCheck,
  Megaphone,
  RadioTower,
  ShieldCheck,
  Sparkles,
  Users,
  Zap,
} from 'lucide-react';

export type MarketingPageKey = 'home' | 'features' | 'modules' | 'pricing' | 'about' | 'contact';

export const studios = [
  { name: 'Audience Studio', desc: 'Unify contacts, segments, imports, suppression, preferences, and lifecycle signals.', icon: Users },
  { name: 'Template Studio', desc: 'Build responsive templates with blocks, previews, versions, assets, and test sends.', icon: LayoutTemplate },
  { name: 'Campaign Studio', desc: 'Plan launches, approval, A/B tests, schedules, retries, pause, resume, and resend.', icon: Megaphone },
  { name: 'Automation Studio', desc: 'Create journeys, triggers, simulations, execution traces, and reusable playbooks.', icon: Bot },
  { name: 'Delivery Studio', desc: 'Monitor queues, providers, replay, retry, warm-up, reputation, and delivery history.', icon: RadioTower },
  { name: 'Analytics Studio', desc: 'Track attribution, funnels, SLA health, reporting, and live engagement metrics.', icon: BarChart3 },
];

export const marketingPages = {
  home: {
    eyebrow: 'Enterprise lifecycle messaging',
    title: 'The premium command center for email growth teams.',
    subtitle:
      'Legent connects audience data, templates, campaigns, automation, delivery, analytics, and governance in one controlled workspace.',
    primaryCta: 'Start Free',
    secondaryCta: 'Explore Modules',
    highlights: ['Live workspace context', 'Provider-aware delivery', 'Governed campaign operations'],
    metrics: [
      { label: 'Faster launches', value: '42%' },
      { label: 'Studios unified', value: '8' },
      { label: 'Queue visibility', value: 'Live' },
    ],
    sections: [
      {
        title: 'From idea to inbox without handoff drag.',
        body: 'Move from segment selection to template approval, scheduled delivery, suppression checks, and analytics without leaving the workspace.',
      },
      {
        title: 'Built for teams that need control.',
        body: 'Every action keeps tenant, workspace, role, audit, and runtime configuration in view.',
      },
    ],
    signalFlow: ['Audience signal', 'Template fit', 'Approval path', 'Delivery guardrail', 'Analytics loop'],
    solutionLayers: [
      { title: 'Data readiness', metric: '42.8k', body: 'Know which contacts, segments, suppressions, and preferences are ready before launch.', items: ['Segment fit', 'Preference health', 'Import quality'] },
      { title: 'Campaign control', metric: '18', body: 'Approval, variants, schedules, and retry posture stay visible in one launch workspace.', items: ['Approval queue', 'Variant status', 'Schedule lock'] },
      { title: 'Delivery confidence', metric: '99.2%', body: 'Provider health, queue depth, warm-up state, and replay safety are built into the decision view.', items: ['Provider stable', 'Queue below SLA', 'Replay ready'] },
    ],
  },
  features: {
    eyebrow: 'Platform features',
    title: 'Modern email operations with enterprise guardrails.',
    subtitle:
      'Design, approve, send, observe, and improve every customer message with consistent state, configuration, and ownership.',
    primaryCta: 'Create Workspace',
    secondaryCta: 'View Pricing',
    highlights: ['RBAC-ready flows', 'Config-driven runtime', 'Full workflow visibility'],
    features: [
      { title: 'Governed audience operations', body: 'Lists, segments, tags, imports, suppression, preferences, scoring, and bulk actions.', icon: ShieldCheck },
      { title: 'Composable campaign launch', body: 'Lifecycle status, approvals, scheduling, experiments, and rollback-friendly controls.', icon: GitBranch },
      { title: 'Inbox-aware delivery', body: 'Provider readiness, queue depth, warm-up, retries, replay, and feedback loops.', icon: MailCheck },
      { title: 'Real-time analytics', body: 'Funnels, attribution, dashboards, reporting, SLA monitoring, and live metrics.', icon: BarChart3 },
      { title: 'Admin runtime control', body: 'Branding, feature flags, quotas, providers, themes, audit logs, and advanced setup.', icon: LockKeyhole },
      { title: 'Fast operator UX', body: 'Search, activity, toasts, tables, forms, loading states, empty states, and responsive layouts.', icon: Zap },
    ],
    bands: [
      { title: 'Security and governance', body: 'Roles, tenant scope, workspace scope, approval controls, and audit trails remain visible across every action.', icon: ShieldCheck },
      { title: 'Runtime configuration', body: 'Provider, theme, quota, flag, and delivery settings shape live behavior instead of becoming static admin records.', icon: LockKeyhole },
      { title: 'Operational telemetry', body: 'Dashboards, funnel views, SLA status, and delivery health help teams act before risk reaches customers.', icon: BarChart3 },
    ],
    operatorWorkflows: [
      { title: 'Approve with context', body: 'Review audience fit, template version, provider readiness, and campaign risk together.' },
      { title: 'Recover without chaos', body: 'Retry, replay, pause, resume, and provider switching expose state before action.' },
      { title: 'Improve every send', body: 'Attribution, funnels, reports, and engagement history feed back into the next launch.' },
    ],
  },
  modules: {
    eyebrow: 'Connected studios',
    title: 'Every messaging workflow has a dedicated studio.',
    subtitle:
      'Each studio owns its workflow while sharing tenant, workspace, permissions, configuration, activity, and analytics context.',
    primaryCta: 'Open Workspace',
    secondaryCta: 'See Features',
    highlights: ['Audience to analytics', 'Template to send', 'Automation to delivery'],
    flow: ['Audience', 'Template', 'Campaign', 'Automation', 'Delivery', 'Analytics'],
    drilldowns: [
      { title: 'Audience Studio', body: 'Contacts, lists, imports, tags, preferences, suppression, scoring, and merge workflows.', proof: 'Data ready' },
      { title: 'Template Studio', body: 'Blocks, previews, versions, rollback, HTML import/export, assets, and test sends.', proof: 'Versioned' },
      { title: 'Campaign Studio', body: 'Scheduling, approval, lifecycle controls, cloning, retry, resend, and experiments.', proof: 'Launch safe' },
      { title: 'Automation Studio', body: 'Triggers, recurring journeys, simulation, execution trace, templates, and rollback.', proof: 'Traceable' },
      { title: 'Delivery Studio', body: 'Send jobs, queue health, replay, provider switching, warm-up, and delivery history.', proof: 'Inbox-aware' },
      { title: 'Analytics Studio', body: 'Tracking, attribution, funnels, reports, SLA monitoring, and real-time metrics.', proof: 'Live' },
    ],
  },
  pricing: {
    eyebrow: 'Pricing',
    title: 'Plans for teams scaling from first sends to global programs.',
    subtitle:
      'Start with a focused workspace, then add orchestration, delivery governance, automation, and platform controls as you grow.',
    primaryCta: 'Start Free',
    secondaryCta: 'Talk to Sales',
    plans: [
      { name: 'Launch', price: 'INR 4,999', desc: 'For first lifecycle programs.', features: ['1 workspace', 'Template and campaign studios', 'MailHog/local delivery setup', 'Basic analytics'] },
      { name: 'Scale', price: 'INR 24,999', desc: 'For growing marketing teams.', featured: true, features: ['5 workspaces', 'Automation and delivery studios', 'Provider switching', 'Advanced segmentation'] },
      { name: 'Enterprise', price: 'Custom INR', desc: 'For governed multi-team programs.', features: ['Unlimited workspaces', 'SLA and audit controls', 'Dedicated provider strategy', 'Security review support'] },
    ],
    comparison: [
      { feature: 'Workspaces', launch: '1', scale: '5', enterprise: 'Unlimited' },
      { feature: 'Automation Studio', launch: 'Basic', scale: 'Advanced', enterprise: 'Custom governance' },
      { feature: 'Provider switching', launch: 'Local-ready', scale: 'Included', enterprise: 'Dedicated strategy' },
      { feature: 'Audit and SLA controls', launch: 'Core logs', scale: 'Advanced', enterprise: 'Enterprise review' },
    ],
    addons: [
      { title: 'Deliverability advisory', body: 'Domain warm-up, provider strategy, reputation posture, and recovery planning.' },
      { title: 'Migration support', body: 'Template, audience, suppression, automation, and campaign migration planning.' },
      { title: 'Security review', body: 'Architecture walkthrough, data handling review, and enterprise procurement support.' },
    ],
    faqs: [
      { q: 'Can we use our own providers?', a: 'Yes. Local defaults stay simple while paid providers are configurable through admin controls.' },
      { q: 'Is the public site tied to backend content?', a: 'No. These public pages render from static frontend data for speed and resilience.' },
      { q: 'Can teams work in dark and light mode?', a: 'Yes. The product keeps theme support while the public brand defaults to dark purple.' },
    ],
  },
  about: {
    eyebrow: 'About Legent',
    title: 'Built for operators who care about message quality at scale.',
    subtitle:
      'Legent treats email as a production system: data contracts, approvals, provider health, attribution, and governance belong in the same workspace.',
    primaryCta: 'Explore Platform',
    secondaryCta: 'Contact Team',
    principles: [
      { title: 'Operational clarity', body: 'Every workflow should show state, ownership, risk, and next action clearly.' },
      { title: 'Premium speed', body: 'Beautiful interfaces must still help busy teams move faster under real pressure.' },
      { title: 'Runtime discipline', body: 'Configuration, security, delivery, and analytics must stay connected at execution time.' },
    ],
    timeline: [
      { title: 'Foundation', body: 'Tenant, workspace, roles, providers, and defaults created with clear ownership.' },
      { title: 'Launch control', body: 'Audience, template, campaign, approval, delivery, and analytics flow through one workspace.' },
      { title: 'Operational maturity', body: 'Teams add governance, reporting, deliverability posture, and audit-ready execution.' },
    ],
    quality: [
      { title: 'No disconnected studio', body: 'Every visible workflow should have a real runtime path and clear state.' },
      { title: 'No invisible risk', body: 'Suppression, provider health, permissions, and config context are first-class UI signals.' },
      { title: 'No generic SaaS feel', body: 'Dense operational surfaces still need premium hierarchy, motion, and calm spacing.' },
    ],
  },
  contact: {
    eyebrow: 'Contact',
    title: 'Talk with product and solution architects.',
    subtitle:
      'Use the form to plan a workspace rollout, provider setup, deliverability strategy, migration, or enterprise review.',
    primaryCta: 'Enter Workspace',
    secondaryCta: 'Read Blog',
    contactCards: [
      { title: 'Sales architecture', body: 'Plan team structure, modules, quotas, and launch path.', icon: Globe2 },
      { title: 'Delivery review', body: 'Discuss provider switching, warm-up, replay, and reputation.', icon: MailCheck },
      { title: 'Support', body: 'Get help with configuration, onboarding, and workspace setup.', icon: Headphones },
    ],
    formInterests: ['Workspace rollout', 'Provider and deliverability', 'Migration planning', 'Security review'],
    expectations: [
      { label: 'Initial response', value: '1 business day' },
      { label: 'Architecture review', value: '30 minutes' },
      { label: 'Recommended next step', value: 'Workspace plan' },
    ],
  },
} as const;

export const blogCategories = ['Operations', 'Delivery', 'UX'] as const;

export const blogPosts = [
  {
    slug: 'audience-to-inbox-operating-model',
    title: 'Designing an audience-to-inbox operating model',
    summary: 'How high-growth teams connect segmentation, approvals, provider health, and analytics.',
    readTime: '6 min read',
    category: 'Operations',
    body:
      '<p>A premium messaging operation starts with shared context. Audience, template, campaign, delivery, and analytics workflows need the same workspace, permissions, and configuration source.</p><p>Legent keeps those handoffs visible so operators can find risk before it reaches the queue.</p>',
  },
  {
    slug: 'delivery-replay-without-chaos',
    title: 'Delivery replay without chaos',
    summary: 'A practical framework for retry, replay, provider switching, and suppression safety.',
    readTime: '5 min read',
    category: 'Delivery',
    body:
      '<p>Retry systems fail when they hide state. Replay should expose idempotency, provider health, suppression checks, and audit trail before an operator acts.</p><p>The result is faster recovery without accidental duplicate sends.</p>',
  },
  {
    slug: 'premium-campaign-workflows',
    title: 'What makes campaign workflow feel premium',
    summary: 'Premium UX is not decoration. It is speed, hierarchy, confidence, and controlled action.',
    readTime: '4 min read',
    category: 'UX',
    body:
      '<p>Campaign teams need dense information, but not visual noise. Clear tables, stable controls, obvious state, and fast paths to action make a product feel modern.</p><p>Animation should confirm motion through the system, not distract from it.</p>',
  },
];

export const proofCards = [
  { label: 'API-backed studios', value: '8', icon: Database },
  { label: 'Launch status clarity', value: 'Live', icon: CheckCircle2 },
  { label: 'Premium operator flow', value: 'Fast', icon: Sparkles },
];

import {
  BarChart3,
  Bot,
  Boxes,
  BrainCircuit,
  Building2,
  CheckCircle2,
  Database,
  FileCheck2,
  GitBranch,
  Globe2,
  Headphones,
  LayoutTemplate,
  LineChart,
  LockKeyhole,
  MailCheck,
  Megaphone,
  Network,
  RadioTower,
  ShieldCheck,
  Sparkles,
  Target,
  Users,
  Zap,
} from 'lucide-react';

export type MarketingPageKey = 'home' | 'features' | 'modules' | 'pricing' | 'about' | 'contact';

export const studios = [
  {
    name: 'Audience Studio',
    short: 'Audience',
    desc: 'Unify subscribers, lists, imports, preferences, suppression, and lifecycle readiness.',
    outcome: 'Clean, compliant customer reach.',
    proof: '42.8k ready contacts',
    color: 'emerald',
    icon: Users,
    capabilities: ['Subscriber intelligence', 'Segment rules', 'Import validation', 'Preference health'],
  },
  {
    name: 'Template Studio',
    short: 'Template',
    desc: 'Build responsive templates with reusable blocks, previews, assets, versions, and tests.',
    outcome: 'Approved creative with fewer production loops.',
    proof: '12 active versions',
    color: 'amber',
    icon: LayoutTemplate,
    capabilities: ['Block library', 'HTML QA', 'Version control', 'Test sends'],
  },
  {
    name: 'Campaign Studio',
    short: 'Campaign',
    desc: 'Plan launches, approvals, schedules, experiments, retries, pause, resume, and resend.',
    outcome: 'Launch control without disconnected handoffs.',
    proof: '18 governed launches',
    color: 'violet',
    icon: Megaphone,
    capabilities: ['Approval queue', 'A/B paths', 'Schedule locks', 'Rollback posture'],
  },
  {
    name: 'Automation Studio',
    short: 'Automation',
    desc: 'Create journeys, triggers, simulations, execution traces, and reusable lifecycle playbooks.',
    outcome: 'Always-on automation teams can understand.',
    proof: '64% journey complete',
    color: 'blue',
    icon: Bot,
    capabilities: ['Journey map', 'Trigger rules', 'Simulation', 'Execution trace'],
  },
  {
    name: 'Delivery Studio',
    short: 'Delivery',
    desc: 'Monitor queues, providers, replay, retry, warm-up, reputation, and delivery history.',
    outcome: 'Inbox-first execution under real load.',
    proof: '99.2% safe route',
    color: 'rose',
    icon: RadioTower,
    capabilities: ['Provider health', 'Queue SLA', 'Replay safety', 'Warm-up state'],
  },
  {
    name: 'Analytics Studio',
    short: 'Analytics',
    desc: 'Track attribution, funnels, SLA health, reporting, and live engagement metrics.',
    outcome: 'Feedback loops that improve every send.',
    proof: 'Live funnel signal',
    color: 'cyan',
    icon: BarChart3,
    capabilities: ['Realtime events', 'Funnel view', 'Attribution', 'Reports'],
  },
] as const;

export const homeScenarios = [
  {
    eyebrow: 'Launch readiness',
    title: 'Executive launch command',
    metric: '76%',
    status: 'Approved',
    narrative: 'Audience quality, creative approval, delivery safety, and analytics readiness move together before a campaign enters queue.',
    modules: ['Audience', 'Template', 'Campaign', 'Delivery'],
    stages: ['Audience fit', 'Template QA', 'Approval path', 'Provider route'],
    activity: ['Segment health refreshed', 'Variant B approved', 'Suppression check passed', 'Provider route locked'],
    bars: [46, 62, 54, 78, 72, 86, 81, 94],
  },
  {
    eyebrow: 'AI orchestration',
    title: 'Lifecycle journey control',
    metric: '38k',
    status: 'Learning',
    narrative: 'AI-assisted journey suggestions stay governed by explicit trigger logic, audit evidence, and operator approval.',
    modules: ['Audience', 'Automation', 'Template', 'Analytics'],
    stages: ['Signal detected', 'Journey branch', 'Content match', 'Feedback loop'],
    activity: ['Dormant cohort detected', 'Next-best branch simulated', 'Personalization tokens verified', 'Lift report updating'],
    bars: [28, 42, 61, 58, 74, 69, 88, 92],
  },
  {
    eyebrow: 'Inbox intelligence',
    title: 'Provider-aware delivery room',
    metric: '99.2%',
    status: 'Healthy',
    narrative: 'Queue depth, provider health, warm-up state, retries, replay, and suppression safety remain visible during send.',
    modules: ['Campaign', 'Delivery', 'Analytics', 'Admin'],
    stages: ['Queue watch', 'Provider score', 'Retry policy', 'Live outcome'],
    activity: ['SES route stable', 'Retry budget normal', 'Replay guard active', 'Open event stream live'],
    bars: [55, 68, 64, 83, 79, 91, 87, 96],
  },
] as const;

export const marketingPages = {
  home: {
    eyebrow: 'Enterprise email command layer',
    title: 'Run lifecycle email from signal to inbox.',
    subtitle:
      'Legent brings audience intelligence, governed creative, AI-assisted journeys, inbox-safe delivery, and revenue feedback into one operating system for modern lifecycle teams.',
    primaryCta: 'Create Workspace',
    secondaryCta: 'Explore Product Fabric',
    highlights: ['Launch readiness in one view', 'Delivery risk before send', 'Shared evidence for every team'],
    metrics: [
      { label: 'Launch cycle reduction', value: '42%', detail: 'from fewer approval, QA, and delivery handoffs' },
      { label: 'Connected studios', value: '6', detail: 'with admin and settings control planes sharing runtime context' },
      { label: 'Inbox safety signal', value: 'Live', detail: 'provider, queue, reputation, DNS, and suppression state' },
    ],
    signalFlow: ['Customer signal', 'AI guidance', 'Human approval', 'Inbox guardrail', 'Revenue loop'],
    solutionLayers: [
      { title: 'Operational intelligence', metric: 'Live', body: 'Every send starts with data quality, audience readiness, approval state, provider risk, and performance history in one view.', items: ['Audience fit', 'Risk posture', 'Launch confidence'] },
      { title: 'Workflow orchestration', metric: '18', body: 'Teams plan journeys, campaigns, approvals, experiments, retries, and recovery paths without losing ownership context.', items: ['Approval rail', 'Variant control', 'Recovery path'] },
      { title: 'Measurable improvement', metric: '42%', body: 'Lifecycle teams move from reactive send management to repeatable operating systems tied to measurable outcomes.', items: ['Revenue signal', 'Cycle time', 'Team velocity'] },
    ],
  },
  features: {
    eyebrow: 'Platform intelligence',
    title: 'Every capability works inside one governed operating model.',
    subtitle:
      'Legent turns fragmented email tooling into a shared command system for data, creative, automation, delivery, analytics, and runtime control.',
    primaryCta: 'Create Workspace',
    secondaryCta: 'View Pricing',
    highlights: ['Governed by design', 'Observable by default', 'Fast under pressure'],
    features: [
      { title: 'Audience intelligence', body: 'Contacts, segments, imports, suppression, preferences, scoring, and consent signals stay visible before launch.', icon: Users },
      { title: 'Composable campaign launch', body: 'Approvals, variants, schedules, experiments, retries, pause, resume, and rollback-friendly controls in one flow.', icon: GitBranch },
      { title: 'AI journey assistance', body: 'Suggested next-best branches, timing windows, and personalization checks remain explainable and operator-approved.', icon: BrainCircuit },
      { title: 'Inbox-aware delivery', body: 'Provider readiness, queue depth, warm-up, retries, replay, reputation, and feedback loops become launch inputs.', icon: MailCheck },
      { title: 'Realtime analytics', body: 'Funnels, attribution, live engagement, SLA health, and reporting feed operational decisions, not only dashboards.', icon: LineChart },
      { title: 'Admin runtime control', body: 'Branding, feature flags, quotas, providers, themes, audit logs, and advanced setup shape live behavior.', icon: LockKeyhole },
    ],
    process: ['Ingest signal', 'Assess risk', 'Compose workflow', 'Approve action', 'Deliver safely', 'Learn'],
  },
  modules: {
    eyebrow: 'Product architecture',
    title: 'Six operating studios, one shared runtime fabric.',
    subtitle:
      'Each module owns a deep workflow while tenant, workspace, permissions, configuration, activity, and analytics context stay connected.',
    primaryCta: 'Open Workspace',
    secondaryCta: 'See Features',
    highlights: ['Shared runtime', 'Visible handoffs', 'Expandable depth'],
    flow: ['Audience', 'Template', 'Campaign', 'Automation', 'Delivery', 'Analytics'],
  },
  pricing: {
    eyebrow: 'Pricing workspace',
    title: 'Choose the operating model your team can grow into.',
    subtitle:
      'Start with launch discipline, then add orchestration, delivery governance, automation depth, and enterprise controls as your program matures.',
    primaryCta: 'Start Free',
    secondaryCta: 'Talk to Sales',
    plans: [
      { name: 'Launch', monthly: 4999, desc: 'For first lifecycle programs.', fit: 'Founder-led and early marketing teams', features: ['1 workspace', 'Template and campaign studios', 'Local delivery setup', 'Basic analytics'] },
      { name: 'Scale', monthly: 24999, desc: 'For growing lifecycle teams.', featured: true, fit: 'Teams running repeated launches', features: ['5 workspaces', 'Automation and delivery studios', 'Provider switching', 'Advanced segmentation'] },
      { name: 'Enterprise', monthly: null, desc: 'For governed multi-team programs.', fit: 'Security, SLA, and procurement needs', features: ['Unlimited workspaces', 'SLA and audit controls', 'Dedicated provider strategy', 'Security review support'] },
    ],
    comparison: [
      { feature: 'Workspaces', launch: '1', scale: '5', enterprise: 'Unlimited' },
      { feature: 'Automation Studio', launch: 'Basic journeys', scale: 'Advanced branching', enterprise: 'Governed playbooks' },
      { feature: 'Provider switching', launch: 'Local-ready', scale: 'Included', enterprise: 'Dedicated strategy' },
      { feature: 'Audit and SLA controls', launch: 'Core logs', scale: 'Advanced', enterprise: 'Enterprise review' },
      { feature: 'Support model', launch: 'Product support', scale: 'Architecture review', enterprise: 'Success plan' },
    ],
    roi: [
      { label: 'Launch hours saved', value: '42%' },
      { label: 'Recovery clarity', value: '3x' },
      { label: 'Workflow visibility', value: 'Live' },
    ],
  },
  about: {
    eyebrow: 'About Legent',
    title: 'Built for teams that treat messaging as production infrastructure.',
    subtitle:
      'Legent is shaped around operators who need beautiful interfaces, but also demand state, ownership, safety, and measurable outcomes.',
    primaryCta: 'Explore Platform',
    secondaryCta: 'Contact Team',
    principles: [
      { title: 'Operational clarity', body: 'Every workflow should expose state, ownership, risk, and the next action without extra tools.', icon: Target },
      { title: 'Human control over automation', body: 'AI should accelerate decisions while keeping approval, evidence, and rollback posture explicit.', icon: BrainCircuit },
      { title: 'Runtime discipline', body: 'Configuration, security, delivery, and analytics must stay connected when work actually executes.', icon: ShieldCheck },
    ],
    timeline: [
      { year: '01', title: 'Foundation', body: 'Tenant, workspace, roles, providers, and defaults created with clear ownership.' },
      { year: '02', title: 'Launch control', body: 'Audience, template, campaign, approval, delivery, and analytics run through one workspace.' },
      { year: '03', title: 'Automation maturity', body: 'Teams add journey intelligence, simulations, recovery paths, and AI-assisted workflow design.' },
      { year: '04', title: 'Enterprise readiness', body: 'Governance, reporting, deliverability posture, and audit-ready execution become the default.' },
    ],
  },
  contact: {
    eyebrow: 'Contact',
    title: 'Bring a real launch problem. We will map the operating path.',
    subtitle:
      'Use the form to plan workspace rollout, provider setup, deliverability posture, migration, onboarding, or enterprise review.',
    primaryCta: 'Enter Workspace',
    secondaryCta: 'Read Blog',
    contactCards: [
      { title: 'Solution architecture', body: 'Plan team structure, workspaces, modules, quotas, and launch governance.', icon: Globe2 },
      { title: 'Delivery review', body: 'Discuss provider switching, warm-up, replay, reputation, and failure recovery.', icon: MailCheck },
      { title: 'Customer support', body: 'Get help with onboarding, configuration, migration, and workspace setup.', icon: Headphones },
    ],
    formInterests: ['Workspace rollout', 'Provider and deliverability', 'Migration planning', 'Security review'],
    expectations: [
      { label: 'Initial response', value: '1 business day' },
      { label: 'Architecture review', value: '30 minutes' },
      { label: 'Recommended next step', value: 'Workspace plan' },
    ],
  },
} as const;

export const contactRoutes = [
  { label: 'Workspace rollout', icon: Building2, path: 'Product + solution architecture' },
  { label: 'Provider review', icon: RadioTower, path: 'Deliverability and routing' },
  { label: 'Migration', icon: Boxes, path: 'Templates, audiences, automation' },
  { label: 'Security', icon: FileCheck2, path: 'Architecture and procurement' },
] as const;

export const blogCategories = ['Operations', 'Delivery', 'Automation', 'UX'] as const;

export const blogPosts = [
  {
    slug: 'audience-to-inbox-operating-model',
    title: 'Designing an audience-to-inbox operating model',
    summary: 'How high-growth teams connect segmentation, approvals, provider health, and analytics into one accountable workflow.',
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
    slug: 'ai-automation-with-human-control',
    title: 'AI automation with human control',
    summary: 'How lifecycle teams use AI guidance without losing approval clarity, evidence, or rollback posture.',
    readTime: '7 min read',
    category: 'Automation',
    body:
      '<p>AI can accelerate lifecycle work when it explains why a branch, segment, or timing window matters. The operator still needs clear evidence, approval state, and recovery options.</p><p>Automation becomes valuable when it remains inspectable.</p>',
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
  { label: 'Runtime-connected studios', value: '6', icon: Database },
  { label: 'Launch status clarity', value: 'Live', icon: CheckCircle2 },
  { label: 'Operator acceleration', value: '42%', icon: Sparkles },
  { label: 'Governed automation', value: 'AI', icon: Network },
  { label: 'Queue recovery', value: 'Safe', icon: Zap },
  { label: 'Approval evidence', value: 'Ready', icon: ShieldCheck },
] as const;

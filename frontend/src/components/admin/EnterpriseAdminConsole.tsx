'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { AnimatePresence, motion } from 'framer-motion';
import { clsx } from 'clsx';
import {
  Activity,
  AlertTriangle,
  ArrowRight,
  BadgeCheck,
  BellRing,
  Boxes,
  CheckCircle2,
  ChevronRight,
  Clock3,
  DatabaseZap,
  FileDown,
  Fingerprint,
  Gauge,
  Globe2,
  KeyRound,
  Loader2,
  LockKeyhole,
  Network,
  Plus,
  RefreshCcw,
  RotateCcw,
  Search,
  ServerCog,
  Settings2,
  ShieldCheck,
  ShieldAlert,
  SlidersHorizontal,
  Sparkles,
  Trophy,
  UserCog,
  Users,
  Workflow,
  XCircle,
} from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { Button } from '@/components/ui/Button';
import { useToast } from '@/components/ui/Toast';
import {
  AdminConfigPanel,
} from '@/components/admin/AdminConfigPanel';
import { AuditPanel } from '@/components/admin/AuditPanel';
import { BootstrapStatusPanel } from '@/components/admin/BootstrapStatusPanel';
import { BrandingPanel } from '@/components/admin/BrandingPanel';
import { ContactRequestsPanel } from '@/components/admin/ContactRequestsPanel';
import { DifferentiationPlatformPanel } from '@/components/admin/DifferentiationPlatformPanel';
import { FederationConfigPanel } from '@/components/admin/FederationConfigPanel';
import { GlobalEnterprisePanel } from '@/components/admin/GlobalEnterprisePanel';
import { PerformanceIntelligencePanel } from '@/components/admin/PerformanceIntelligencePanel';
import { PlatformCorePanel } from '@/components/admin/PlatformCorePanel';
import { PublicContentPanel } from '@/components/admin/PublicContentPanel';
import { WebhookPanel } from '@/components/admin/WebhookPanel';
import {
  createAdminUser,
  getAdminAccessOverview,
  getAdminOperationsDashboard,
  listAdminSyncEvents,
  listAdminUsers,
  requestUserPasswordReset,
  updateAdminUser,
  type AdminOperationsDashboard,
  type AdminUser,
} from '@/lib/admin-api';
import { coreApi } from '@/lib/core-api';

type SectionId = 'command' | 'users' | 'roles' | 'federation' | 'audit' | 'configuration' | 'operations' | 'differentiation' | 'global' | 'performance';

type UserDraft = {
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  password: string;
};

const sections: Array<{
  id: SectionId;
  label: string;
  kicker: string;
  icon: typeof Gauge;
}> = [
  { id: 'command', label: 'System Command', kicker: 'health, jobs, drift', icon: Gauge },
  { id: 'users', label: 'Users', kicker: 'identity, sessions, access', icon: Users },
  { id: 'roles', label: 'Role Engine', kicker: 'inheritance, grants, features', icon: Fingerprint },
  { id: 'federation', label: 'Federation', kicker: 'SAML, OIDC, SCIM', icon: KeyRound },
  { id: 'audit', label: 'Audit Center', kicker: 'investigation, export, replay', icon: FileDown },
  { id: 'configuration', label: 'Configuration', kicker: 'validated runtime control', icon: SlidersHorizontal },
  { id: 'operations', label: 'Platform Ops', kicker: 'branding, webhooks, public ops', icon: ServerCog },
  { id: 'differentiation', label: 'Differentiation', kicker: 'AI, omni, SLO, SDKs', icon: Sparkles },
  { id: 'global', label: 'Global Ops', kicker: 'regions, residency, governance', icon: Globe2 },
  { id: 'performance', label: 'Performance Intelligence', kicker: 'decisions, optimization, ops, benchmarks', icon: Trophy },
];

const roleOptions = ['ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN', 'SECURITY_ADMIN', 'WORKSPACE_OWNER', 'CAMPAIGN_MANAGER', 'DELIVERY_OPERATOR', 'ANALYST', 'VIEWER', 'USER'];

const moduleFlow = [
  'Identity',
  'Access',
  'Config',
  'Workflow',
  'Delivery',
  'Analytics',
];

const defaultUserDraft: UserDraft = {
  email: '',
  firstName: '',
  lastName: '',
  role: 'USER',
  password: '',
};

export function EnterpriseAdminConsole() {
  const router = useRouter();
  const { isAdmin, roles } = useAuth();
  const { addToast } = useToast();
  const [active, setActive] = useState<SectionId>('command');
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [dashboard, setDashboard] = useState<AdminOperationsDashboard | null>(null);
  const [access, setAccess] = useState<Record<string, Array<Record<string, unknown>>>>({});
  const [syncEvents, setSyncEvents] = useState<Array<Record<string, unknown>>>([]);
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [query, setQuery] = useState('');
  const [userDraft, setUserDraft] = useState<UserDraft>(defaultUserDraft);
  const [savingUser, setSavingUser] = useState(false);
  const [pendingUserIds, setPendingUserIds] = useState<Set<string>>(new Set());
  const [loadIssues, setLoadIssues] = useState<string[]>([]);

  const canAdmin = useMemo(
    () => isAdmin() || roles.some((role) => ['PLATFORM_ADMIN', 'ORG_ADMIN'].includes(role)),
    [isAdmin, roles]
  );

  const load = useCallback(async (silent = false) => {
    if (!silent) {
      setLoading(true);
    } else {
      setRefreshing(true);
    }
    try {
      const labels = ['Operations dashboard', 'Access overview', 'Sync ledger', 'Users'] as const;
      const [dashboardRes, accessRes, syncRes, usersRes] = await Promise.allSettled([
        getAdminOperationsDashboard(),
        getAdminAccessOverview(),
        listAdminSyncEvents({ limit: 50 }),
        listAdminUsers(),
      ] as const);
      const results = [dashboardRes, accessRes, syncRes, usersRes] as const;
      const issues = labels
        .map((label, index) => {
          const result = results[index];
          return result.status === 'rejected' ? label : null;
        })
        .filter(Boolean) as string[];
      setLoadIssues(issues);
      if (dashboardRes.status === 'fulfilled') {
        setDashboard(dashboardRes.value);
      }
      if (accessRes.status === 'fulfilled') {
        setAccess(accessRes.value);
      }
      if (syncRes.status === 'fulfilled') {
        setSyncEvents(syncRes.value);
      }
      if (usersRes.status === 'fulfilled') {
        setUsers(usersRes.value);
      }
      if (issues.length && !silent) {
        addToast({
          type: 'warning',
          title: 'Some admin data unavailable',
          message: `${issues.join(', ')} did not load. Available panels remain usable.`,
        });
      }
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [addToast]);

  const setUserPending = useCallback((id: string, pending: boolean) => {
    setPendingUserIds((current) => {
      const next = new Set(current);
      if (pending) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }, []);

  useEffect(() => {
    if (!canAdmin) {
      router.replace('/app/email');
      return;
    }
    load();
    const timer = window.setInterval(() => load(true), 15000);
    return () => window.clearInterval(timer);
  }, [canAdmin, load, router]);

  const filteredUsers = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) {
      return users;
    }
    return users.filter((user) =>
      [user.email, user.firstName, user.lastName, user.role, ...(user.roles || [])]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
        .includes(q)
    );
  }, [query, users]);

  const createUser = async () => {
    const email = userDraft.email.trim().toLowerCase();
    if (!email || !userDraft.password || !userDraft.role) {
      addToast({ type: 'error', title: 'User needs email, password, and role' });
      return;
    }
    setSavingUser(true);
    try {
      const created = await createAdminUser({
        ...userDraft,
        email,
        firstName: userDraft.firstName.trim(),
        lastName: userDraft.lastName.trim(),
        role: userDraft.role.trim(),
        isActive: true,
      });
      setUsers((prev) => [created, ...prev]);
      setUserDraft(defaultUserDraft);
      addToast({ type: 'success', title: 'User created', message: 'Access context is available for assignment.' });
    } catch (error: any) {
      addToast({ type: 'error', title: 'User create failed', message: error?.normalized?.message || error?.message });
    } finally {
      setSavingUser(false);
    }
  };

  const patchUser = async (user: AdminUser, patch: Partial<AdminUser>) => {
    const payload = {
      email: user.email,
      firstName: patch.firstName ?? user.firstName ?? '',
      lastName: patch.lastName ?? user.lastName ?? '',
      role: patch.role ?? user.role,
      isActive: patch.isActive ?? user.isActive,
    };
    setUserPending(user.id, true);
    try {
      const updated = await updateAdminUser(user.id, payload);
      setUsers((prev) => prev.map((candidate) => (candidate.id === user.id ? updated : candidate)));
      addToast({ type: 'success', title: 'User policy updated', message: 'Menus and API checks refresh on next session validation.' });
    } catch (error: any) {
      addToast({ type: 'error', title: 'User update failed', message: error?.normalized?.message || error?.message });
    } finally {
      setUserPending(user.id, false);
    }
  };

  const sendReset = async (user: AdminUser) => {
    setUserPending(user.id, true);
    try {
      await requestUserPasswordReset(user.email);
      addToast({ type: 'success', title: 'Reset workflow queued', message: user.email });
    } catch (error: any) {
      addToast({ type: 'error', title: 'Reset request failed', message: error?.normalized?.message || error?.message });
    } finally {
      setUserPending(user.id, false);
    }
  };

  const createOperatorRole = async () => {
    try {
      await coreApi.createRole({
        roleKey: 'OPERATIONS_GOVERNOR',
        displayName: 'Operations Governor',
        description: 'Manages delivery, config, audit investigation, and workflow recovery.',
        system: false,
        permissions: ['delivery:*', 'config:*', 'audit:*', 'workflow:*', 'tracking:read'],
        metadata: { createdFrom: 'enterprise-admin-console' },
      });
      addToast({ type: 'success', title: 'Role created', message: 'Propagation event recorded by the access engine.' });
      load(true);
    } catch (error: any) {
      addToast({ type: 'error', title: 'Role create failed', message: error?.normalized?.message || error?.message });
    }
  };

  if (!canAdmin) {
    return null;
  }

  return (
    <div className="space-y-5 text-content-primary">
      <section className="overflow-hidden rounded-lg border border-border-default bg-surface-elevated shadow-[0_20px_80px_rgba(31,41,55,0.10)]">
        <div className="relative grid gap-6 p-5 md:grid-cols-[minmax(0,1fr)_360px] md:p-7">
          <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(135deg,rgba(16,185,129,0.10),transparent_38%,rgba(59,130,246,0.10))]" />
          <div className="relative">
            <div className="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/30 dark:text-emerald-300">
              <ShieldCheck className="h-3.5 w-3.5" />
              Enterprise administration fabric
            </div>
            <h1 className="mt-4 text-3xl font-semibold tracking-normal text-content-primary md:text-4xl">
              Govern users, runtime policy, audit evidence, and configuration propagation from one control plane.
            </h1>
            <p className="mt-3 max-w-3xl text-sm leading-6 text-content-secondary md:text-base">
              Admin reads live operational state, records propagation events for access/config changes, and keeps settings, workflows, menus, and API permissions aligned with tenant context.
            </p>
            <div className="mt-5 flex flex-wrap gap-3">
              <Button onClick={() => load(true)} loading={refreshing} icon={<RefreshCcw className="h-4 w-4" />}>
                Refresh console
              </Button>
              <Button variant="secondary" onClick={() => setActive('configuration')} icon={<Settings2 className="h-4 w-4" />}>
                Runtime controls
              </Button>
            </div>
          </div>
          <motion.div
            initial={{ opacity: 0, y: 18 }}
            animate={{ opacity: 1, y: 0 }}
            className="relative min-h-[220px] rounded-lg border border-border-default bg-white/80 p-4 shadow-inner dark:bg-white/5"
          >
            <div className="flex items-center justify-between">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-content-muted">Propagation mesh</p>
              <StatusBadge status={asText(dashboard?.health?.status, 'SYNCING')} />
            </div>
            <div className="mt-5 flex flex-wrap items-center gap-2">
              {moduleFlow.map((item, index) => (
                <div key={item} className="flex items-center gap-2">
                  <motion.div
                    animate={{ y: [0, -4, 0] }}
                    transition={{ duration: 2.6, repeat: Infinity, delay: index * 0.16 }}
                    className="rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-xs font-semibold shadow-sm"
                  >
                    {item}
                  </motion.div>
                  {index < moduleFlow.length - 1 && <ArrowRight className="h-4 w-4 text-content-muted" />}
                </div>
              ))}
            </div>
            <div className="mt-5 space-y-2">
              {(dashboard?.syncEvents?.slice(0, 4) || syncEvents.slice(0, 4)).map((event, index) => (
                <motion.div
                  key={`${asText(event.event_type || event.eventType, 'sync')}-${index}`}
                  initial={{ opacity: 0, x: -10 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: index * 0.08 }}
                  className="flex items-center justify-between rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-xs"
                >
                  <span className="font-semibold text-content-primary">{asText(event.event_type || event.eventType, 'SYNC_EVENT')}</span>
                  <span className="text-content-secondary">{formatTime(event.created_at || event.createdAt)}</span>
                </motion.div>
              ))}
              {!dashboard?.syncEvents?.length && !syncEvents.length ? (
                <p className="rounded-lg border border-dashed border-border-default bg-surface-secondary px-3 py-2 text-xs text-content-muted">
                  No propagation events recorded yet.
                </p>
              ) : null}
            </div>
          </motion.div>
        </div>
      </section>

      {loadIssues.length ? (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-200">
          Partial admin data loaded. Unavailable: {loadIssues.join(', ')}.
        </div>
      ) : null}

      <div className="grid gap-4 lg:grid-cols-[260px_minmax(0,1fr)]">
        <aside className="h-fit rounded-lg border border-border-default bg-surface-elevated p-2 shadow-sm">
          {sections.map((section) => {
            const Icon = section.icon;
            const selected = active === section.id;
            return (
              <button
                key={section.id}
                type="button"
                onClick={() => setActive(section.id)}
                className={clsx(
                  'mb-1 flex w-full items-center gap-3 rounded-lg px-3 py-3 text-left transition-all focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent',
                  selected ? 'bg-brand-50 text-brand-800 dark:bg-brand-900/30 dark:text-brand-100' : 'text-content-secondary hover:bg-surface-secondary hover:text-content-primary'
                )}
              >
                <Icon className="h-4 w-4 shrink-0" />
                <span className="min-w-0 flex-1">
                  <span className="block text-sm font-semibold">{section.label}</span>
                  <span className="block truncate text-xs opacity-75">{section.kicker}</span>
                </span>
                {selected && <ChevronRight className="h-4 w-4" />}
              </button>
            );
          })}
        </aside>

        <main className="min-w-0">
          {loading ? (
            <LoadingPanel />
          ) : (
            <AnimatePresence mode="wait">
              {active === 'command' && <CommandDashboard key="command" dashboard={dashboard} syncEvents={syncEvents} />}
              {active === 'users' && (
                <UserManagement
                  key="users"
                  query={query}
                  setQuery={setQuery}
                  users={filteredUsers}
                  userDraft={userDraft}
                  setUserDraft={setUserDraft}
                  createUser={createUser}
                  savingUser={savingUser}
                  pendingUserIds={pendingUserIds}
                  patchUser={patchUser}
                  sendReset={sendReset}
                />
              )}
              {active === 'roles' && <RoleEngine key="roles" access={access} createOperatorRole={createOperatorRole} />}
              {active === 'federation' && <FederationConfigPanel key="federation" />}
              {active === 'audit' && <AuditCenter key="audit" syncEvents={syncEvents} />}
              {active === 'configuration' && <ConfigurationCenter key="configuration" />}
              {active === 'operations' && <PlatformOperations key="operations" />}
              {active === 'differentiation' && <DifferentiationPlatform key="differentiation" />}
              {active === 'global' && <GlobalEnterprise key="global" />}
              {active === 'performance' && <PerformanceIntelligence key="performance" />}
            </AnimatePresence>
          )}
        </main>
      </div>
    </div>
  );
}

function CommandDashboard({ dashboard, syncEvents }: { dashboard: AdminOperationsDashboard | null; syncEvents: Array<Record<string, unknown>> }) {
  const stats = dashboard?.stats || {};
  const metrics = [
    { label: 'Workspaces', value: asNumber(stats.workspaces), icon: Boxes, tone: 'blue' },
    { label: 'Active users', value: asNumber(stats.memberships), icon: Users, tone: 'emerald' },
    { label: 'Runtime configs', value: asNumber(stats.runtimeConfigs), icon: DatabaseZap, tone: 'violet' },
    { label: 'Audit 24h', value: asNumber(stats.auditEvents24h), icon: Activity, tone: 'amber' },
  ];

  return (
    <PanelMotion>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {metrics.map((metric, index) => {
          const Icon = metric.icon;
          return (
            <motion.div
              key={metric.label}
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: index * 0.05 }}
              className="rounded-lg border border-border-default bg-surface-elevated p-4 shadow-sm"
            >
              <div className="flex items-center justify-between">
                <Icon className={clsx('h-5 w-5', toneText(metric.tone))} />
                <span className="text-xs font-semibold text-content-muted">LIVE</span>
              </div>
              <p className="mt-4 text-3xl font-semibold">{metric.value}</p>
              <p className="text-sm text-content-secondary">{metric.label}</p>
            </motion.div>
          );
        })}
      </div>

      <div className="mt-4 grid gap-4 xl:grid-cols-[minmax(0,1.15fr)_minmax(360px,0.85fr)]">
        <section className="rounded-lg border border-border-default bg-surface-elevated p-5 shadow-sm">
          <SectionHeader
            icon={Network}
            title="Module Activity Graph"
            subtitle="Config, audit, and propagation signals by operating module."
          />
          <div className="mt-5 grid gap-3 md:grid-cols-2">
            {(dashboard?.modules || []).map((module, index) => (
              <motion.div
                key={asText(module.key, String(index))}
                whileHover={{ y: -3, scale: 1.01 }}
                className="group rounded-lg border border-border-default bg-surface-secondary p-4"
              >
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-semibold text-content-primary">{asText(module.label, 'Module')}</p>
                    <p className="mt-1 text-xs leading-5 text-content-secondary">{asText(module.description, 'Operational module')}</p>
                  </div>
                  <StatusBadge status={asText(module.status, 'OPERATIONAL')} />
                </div>
                <div className="mt-4 grid grid-cols-3 gap-2 text-center text-xs">
                  <MiniStat label="configs" value={asNumber(module.configs)} />
                  <MiniStat label="audit" value={asNumber(module.auditEvents)} />
                  <MiniStat label="sync" value={asNumber(module.syncEvents)} />
                </div>
                <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-surface-primary">
                  <motion.div
                    className="h-full rounded-full bg-gradient-to-r from-emerald-400 via-blue-500 to-brand-500"
                    initial={{ width: '18%' }}
                    animate={{ width: `${Math.min(96, 28 + asNumber(module.configs) * 8 + asNumber(module.syncEvents) * 10)}%` }}
                    transition={{ duration: 0.8 }}
                  />
                </div>
              </motion.div>
            ))}
            {!dashboard?.modules?.length ? (
              <EmptyBlock title="No module activity" detail="Operational modules will appear when admin telemetry is available." />
            ) : null}
          </div>
        </section>

        <section className="space-y-4">
          <div className="rounded-lg border border-border-default bg-surface-elevated p-5 shadow-sm">
            <SectionHeader icon={BellRing} title="Alert Queue" subtitle="Critical state that requires admin review." />
            <div className="mt-4 space-y-3">
              {(dashboard?.alerts || []).map((alert, index) => (
                <div key={`${asText(alert.title, 'alert')}-${index}`} className="flex items-start gap-3 rounded-lg border border-border-default bg-surface-secondary p-3">
                  {asText(alert.tone) === 'danger' ? <ShieldAlert className="h-5 w-5 text-red-500" /> : asText(alert.tone) === 'warning' ? <AlertTriangle className="h-5 w-5 text-amber-500" /> : <CheckCircle2 className="h-5 w-5 text-emerald-500" />}
                  <div>
                    <p className="text-sm font-semibold">{asText(alert.title, 'Alert')}</p>
                    <p className="text-xs text-content-secondary">{asText(alert.detail, 'No detail')}</p>
                  </div>
                </div>
              ))}
              {!dashboard?.alerts?.length ? (
                <EmptyBlock title="No alerts" detail="No critical admin alerts are active." compact />
              ) : null}
            </div>
          </div>
          <div className="rounded-lg border border-border-default bg-surface-elevated p-5 shadow-sm">
            <SectionHeader icon={Sparkles} title="Recommended Actions" subtitle="Safe admin moves based on live operations state." />
            <div className="mt-4 space-y-3">
              {(dashboard?.recommendedActions || []).map((action, index) => (
                <div key={`${asText(action.key, 'action')}-${index}`} className="rounded-lg border border-border-default bg-surface-secondary p-3">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="text-sm font-semibold text-content-primary">{asText(action.title, 'Recommended action')}</p>
                      <p className="mt-1 text-xs leading-5 text-content-secondary">{asText(action.detail, 'No detail')}</p>
                    </div>
                    <StatusBadge status={asText(action.action, 'Review')} />
                  </div>
                </div>
              ))}
              {!dashboard?.recommendedActions?.length ? (
                <EmptyBlock title="No recommendations" detail="Admin recommendations appear after operations telemetry loads." compact />
              ) : null}
            </div>
          </div>
          <div className="rounded-lg border border-border-default bg-surface-elevated p-5 shadow-sm">
            <SectionHeader icon={Workflow} title="Recent Propagation" subtitle="Admin changes applied across dependent modules." />
            <ActivityList items={dashboard?.syncEvents?.length ? dashboard.syncEvents : syncEvents} empty="No propagation events yet." />
          </div>
        </section>
      </div>

      <section className="mt-4 rounded-lg border border-border-default bg-surface-elevated p-5 shadow-sm">
        <SectionHeader icon={Clock3} title="Recent Critical Actions" subtitle="Audit trail from core governance workflows." />
        <ActivityList items={dashboard?.activity || []} empty="No audit activity reported." />
      </section>
    </PanelMotion>
  );
}

function UserManagement({
  query,
  setQuery,
  users,
  userDraft,
  setUserDraft,
  createUser,
  savingUser,
  pendingUserIds,
  patchUser,
  sendReset,
}: {
  query: string;
  setQuery: (value: string) => void;
  users: AdminUser[];
  userDraft: UserDraft;
  setUserDraft: (draft: UserDraft) => void;
  createUser: () => void;
  savingUser: boolean;
  pendingUserIds: Set<string>;
  patchUser: (user: AdminUser, patch: Partial<AdminUser>) => Promise<void>;
  sendReset: (user: AdminUser) => Promise<void>;
}) {
  return (
    <PanelMotion>
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_380px]">
        <section className="rounded-lg border border-border-default bg-surface-elevated shadow-sm">
          <div className="border-b border-border-default p-5">
            <SectionHeader icon={UserCog} title="User Management" subtitle="Create accounts, control roles, deactivate access, and queue reset workflows." />
            <div className="mt-4 flex flex-col gap-3 sm:flex-row">
              <label className="relative flex-1">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-content-muted" />
                <input
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  className="w-full rounded-lg border border-border-default bg-surface-primary py-2 pl-10 pr-3 text-sm outline-none transition focus:border-brand-400 focus:ring-2 focus:ring-brand-200 dark:focus:ring-brand-900"
                  placeholder="Search users, roles, status"
                />
              </label>
            </div>
          </div>
          <div className="divide-y divide-border-default">
            {users.map((user) => {
              const pending = pendingUserIds.has(user.id);
              return (
              <div key={user.id} className="grid gap-4 p-4 md:grid-cols-[minmax(0,1fr)_180px_220px] md:items-center">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="truncate font-semibold text-content-primary">{user.email}</p>
                    {user.isActive ? <StatusBadge status="ACTIVE" /> : <StatusBadge status="LOCKED" />}
                  </div>
                  <p className="mt-1 text-xs text-content-secondary">
                    {[user.firstName, user.lastName].filter(Boolean).join(' ') || 'Profile details pending'} · Last login {formatTime(user.lastLoginAt)}
                  </p>
                </div>
                <select
                  value={user.role || 'USER'}
                  onChange={(event) => patchUser(user, { role: event.target.value })}
                  disabled={pending}
                  className="rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm focus:border-brand-400 focus:ring-2 focus:ring-brand-200"
                >
                  {roleOptions.map((role) => (
                    <option key={role} value={role}>{role}</option>
                  ))}
                </select>
                <div className="flex flex-wrap gap-2 md:justify-end">
                  <Button variant="secondary" size="sm" onClick={() => sendReset(user)} loading={pending} disabled={pending} icon={<KeyRound className="h-4 w-4" />}>
                    Reset
                  </Button>
                  <Button
                    variant={user.isActive ? 'danger' : 'secondary'}
                    size="sm"
                    onClick={() => patchUser(user, { isActive: !user.isActive })}
                    loading={pending}
                    disabled={pending}
                    icon={user.isActive ? <XCircle className="h-4 w-4" /> : <CheckCircle2 className="h-4 w-4" />}
                  >
                    {user.isActive ? 'Deactivate' : 'Activate'}
                  </Button>
                </div>
              </div>
              );
            })}
            {!users.length && (
              <EmptyBlock title="No users found" detail="Create a user or clear the current search filter." />
            )}
          </div>
        </section>

        <aside className="space-y-4">
          <section className="rounded-lg border border-border-default bg-surface-elevated p-5 shadow-sm">
            <SectionHeader icon={Plus} title="Create User" subtitle="Provision a governed account with immediate role context." />
            <div className="mt-4 space-y-3">
              <Input label="Email" value={userDraft.email} onChange={(value) => setUserDraft({ ...userDraft, email: value })} />
              <div className="grid grid-cols-2 gap-3">
                <Input label="First" value={userDraft.firstName} onChange={(value) => setUserDraft({ ...userDraft, firstName: value })} />
                <Input label="Last" value={userDraft.lastName} onChange={(value) => setUserDraft({ ...userDraft, lastName: value })} />
              </div>
              <label className="block text-xs font-semibold text-content-secondary">
                Role
                <select
                  value={userDraft.role}
                  onChange={(event) => setUserDraft({ ...userDraft, role: event.target.value })}
                  className="mt-1 w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary"
                >
                  {roleOptions.map((role) => <option key={role} value={role}>{role}</option>)}
                </select>
              </label>
              <Input label="Temporary password" type="password" value={userDraft.password} onChange={(value) => setUserDraft({ ...userDraft, password: value })} />
              <Button className="w-full" loading={savingUser} onClick={createUser} icon={<Plus className="h-4 w-4" />}>
                Create account
              </Button>
            </div>
          </section>
          <section className="rounded-lg border border-border-default bg-surface-elevated p-5 shadow-sm">
            <SectionHeader icon={LockKeyhole} title="Session Policy" subtitle="Revocation and failed login tracking are wired through refresh tokens and audit review." />
            <div className="mt-4 grid gap-3 text-sm">
              <PolicyLine label="Password reset" value="Queued by secure reset token" />
              <PolicyLine label="Inactive access" value="Blocked at identity service" />
              <PolicyLine label="Role freshness" value="Rechecked on session refresh" />
              <PolicyLine label="Device history" value="Tracked by auth session context" />
            </div>
          </section>
        </aside>
      </div>
    </PanelMotion>
  );
}

function RoleEngine({ access, createOperatorRole }: { access: Record<string, Array<Record<string, unknown>>>; createOperatorRole: () => Promise<void> }) {
  const roles = access.roles || [];
  const groups = access.permissionGroups || [];
  const grants = access.delegatedAccess || [];
  const propagation = access.propagation || [];

  return (
    <PanelMotion>
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_380px]">
        <section className="rounded-lg border border-border-default bg-surface-elevated p-5 shadow-sm">
          <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
            <SectionHeader icon={Fingerprint} title="Permission Resolution Engine" subtitle="Role inheritance, feature controls, direct grants, and field/action permission groups." />
            <Button variant="secondary" onClick={createOperatorRole} icon={<Plus className="h-4 w-4" />}>
              Seed operator role
            </Button>
          </div>
          <div className="mt-5 grid gap-3 md:grid-cols-3">
            <EngineNode icon={Users} title="Principal" detail="user, team, workspace" />
            <EngineNode icon={BadgeCheck} title="Role + Groups" detail="inherit, compose, scope" />
            <EngineNode icon={Workflow} title="Propagation" detail="menus, APIs, workflows" />
          </div>
          <div className="mt-5 grid gap-3 lg:grid-cols-2">
            {roles.map((role, index) => (
              <motion.div
                key={`${asText(role.role_key || role.roleKey, 'role')}-${index}`}
                whileHover={{ y: -2 }}
                className="rounded-lg border border-border-default bg-surface-secondary p-4"
              >
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-semibold">{asText(role.display_name || role.displayName, 'Role')}</p>
                    <p className="mt-1 text-xs text-content-secondary">{asText(role.description, 'No description')}</p>
                  </div>
                  {role.is_system || role.isSystem ? <StatusBadge status="SYSTEM" /> : <StatusBadge status="CUSTOM" />}
                </div>
                <PermissionChips permissions={role.permissions} />
              </motion.div>
            ))}
            {!roles.length && <EmptyBlock title="No roles available" detail="Role definitions will appear after platform core sync completes." />}
          </div>
        </section>

        <aside className="space-y-4">
          <section className="rounded-lg border border-border-default bg-surface-elevated p-5 shadow-sm">
            <SectionHeader icon={ShieldCheck} title="Permission Groups" subtitle="Reusable bundles for action and module access." />
            <div className="mt-4 space-y-3">
              {groups.slice(0, 6).map((group, index) => (
                <div key={`${asText(group.group_key || group.groupKey, 'group')}-${index}`} className="rounded-lg border border-border-default bg-surface-secondary p-3">
                  <p className="text-sm font-semibold">{asText(group.display_name || group.displayName, 'Group')}</p>
                  <PermissionChips permissions={group.permissions} compact />
                </div>
              ))}
              {!groups.length && <EmptyBlock title="No groups" detail="Permission groups not loaded." compact />}
            </div>
          </section>
          <section className="rounded-lg border border-border-default bg-surface-elevated p-5 shadow-sm">
            <SectionHeader icon={KeyRound} title="Delegated Access" subtitle="Temporary and elevated access grants." />
            <div className="mt-4 space-y-2">
              {grants.slice(0, 5).map((grant, index) => (
                <PolicyLine
                  key={`${asText(grant.id, 'grant')}-${index}`}
                  label={asText(grant.status, 'PENDING')}
                  value={asText(grant.grantee_user_id || grant.granteeUserId, 'unknown user')}
                />
              ))}
              {!grants.length && <EmptyBlock title="No active grants" detail="No delegated access requests pending." compact />}
            </div>
          </section>
          <section className="rounded-lg border border-border-default bg-surface-elevated p-5 shadow-sm">
            <SectionHeader icon={RotateCcw} title="Propagation" subtitle="Latest permission invalidation events." />
            <ActivityList items={propagation} empty="No access propagation events yet." />
          </section>
        </aside>
      </div>
    </PanelMotion>
  );
}

function AuditCenter({ syncEvents }: { syncEvents: Array<Record<string, unknown>> }) {
  return (
    <PanelMotion>
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
        <AuditPanel />
        <aside className="space-y-4">
          <section className="rounded-lg border border-border-default bg-surface-elevated p-5 shadow-sm">
            <SectionHeader icon={FileDown} title="Investigation Mode" subtitle="Trace actor, resource, config, and propagation events together." />
            <div className="mt-4 space-y-3">
              <PolicyLine label="Evidence scope" value="core audit + sync ledger" />
              <PolicyLine label="Export state" value="filterable events ready" />
              <PolicyLine label="Silent changes" value="blocked by audited services" />
            </div>
          </section>
          <section className="rounded-lg border border-border-default bg-surface-elevated p-5 shadow-sm">
            <SectionHeader icon={Network} title="Sync Ledger" subtitle="Cross-module state updates recorded after admin changes." />
            <ActivityList items={syncEvents} empty="No sync events recorded." />
          </section>
        </aside>
      </div>
    </PanelMotion>
  );
}

function ConfigurationCenter() {
  return (
    <PanelMotion>
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_380px]">
        <AdminConfigPanel />
        <aside className="space-y-4">
          <BootstrapStatusPanel />
          <PlatformCorePanel />
        </aside>
      </div>
    </PanelMotion>
  );
}

function PlatformOperations() {
  return (
    <PanelMotion>
      <div className="grid gap-4 xl:grid-cols-2">
        <BrandingPanel />
        <WebhookPanel />
        <PublicContentPanel />
        <ContactRequestsPanel />
      </div>
    </PanelMotion>
  );
}

function DifferentiationPlatform() {
  return (
    <PanelMotion>
      <DifferentiationPlatformPanel />
    </PanelMotion>
  );
}

function GlobalEnterprise() {
  return (
    <PanelMotion>
      <GlobalEnterprisePanel />
    </PanelMotion>
  );
}

function PerformanceIntelligence() {
  return (
    <PanelMotion>
      <PerformanceIntelligencePanel />
    </PanelMotion>
  );
}

function PanelMotion({ children }: { children: React.ReactNode }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 18 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -10 }}
      transition={{ duration: 0.22 }}
    >
      {children}
    </motion.div>
  );
}

function LoadingPanel() {
  return (
    <div className="rounded-lg border border-border-default bg-surface-elevated p-8 text-center shadow-sm">
      <Loader2 className="mx-auto h-8 w-8 animate-spin text-brand-500" />
      <p className="mt-4 text-sm font-semibold">Loading admin control plane</p>
      <p className="mt-1 text-xs text-content-secondary">Reading health, access, audit, and sync state.</p>
    </div>
  );
}

function SectionHeader({ icon: Icon, title, subtitle }: { icon: typeof Gauge; title: string; subtitle: string }) {
  return (
    <div className="flex items-start gap-3">
      <div className="rounded-lg border border-border-default bg-surface-secondary p-2 text-brand-600 dark:text-brand-300">
        <Icon className="h-4 w-4" />
      </div>
      <div>
        <h2 className="text-base font-semibold tracking-normal text-content-primary">{title}</h2>
        <p className="mt-1 text-sm text-content-secondary">{subtitle}</p>
      </div>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const normalized = status.toUpperCase();
  return (
    <span className={clsx('inline-flex items-center rounded-full border px-2 py-0.5 text-[11px] font-semibold', statusTone(normalized))}>
      {normalized}
    </span>
  );
}

function MiniStat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg border border-border-default bg-surface-primary px-2 py-2">
      <p className="text-sm font-semibold text-content-primary">{value}</p>
      <p className="text-[10px] uppercase tracking-[0.12em] text-content-muted">{label}</p>
    </div>
  );
}

function ActivityList({ items, empty }: { items: Array<Record<string, unknown>>; empty: string }) {
  if (!items.length) {
    return <EmptyBlock title="No activity" detail={empty} compact />;
  }
  return (
    <div className="mt-4 max-h-[420px] space-y-2 overflow-auto pr-1">
      {items.slice(0, 12).map((item, index) => (
        <div key={`${asText(item.id || item.event_type || item.action, 'item')}-${index}`} className="rounded-lg border border-border-default bg-surface-secondary p-3">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-content-primary">
                {asText(item.action || item.event_type || item.eventType, 'Activity')}
              </p>
              <p className="mt-1 text-xs text-content-secondary">
                {asText(item.resource_type || item.resourceType || item.source_module || item.sourceModule, 'system')} · {asText(item.actor_id || item.actorId || item.status, 'system')}
              </p>
            </div>
            <span className="shrink-0 text-[11px] text-content-muted">{formatTime(item.created_at || item.createdAt || item.applied_at || item.appliedAt)}</span>
          </div>
        </div>
      ))}
    </div>
  );
}

function EngineNode({ icon: Icon, title, detail }: { icon: typeof Gauge; title: string; detail: string }) {
  return (
    <motion.div whileHover={{ y: -3 }} className="rounded-lg border border-border-default bg-surface-secondary p-4">
      <Icon className="h-5 w-5 text-brand-500" />
      <p className="mt-3 font-semibold">{title}</p>
      <p className="mt-1 text-xs text-content-secondary">{detail}</p>
    </motion.div>
  );
}

function PermissionChips({ permissions, compact = false }: { permissions: unknown; compact?: boolean }) {
  const list = normalizePermissions(permissions);
  return (
    <div className={clsx('flex flex-wrap gap-1.5', compact ? 'mt-2' : 'mt-4')}>
      {list.slice(0, compact ? 4 : 8).map((permission) => (
        <span key={permission} className="rounded-full border border-border-default bg-surface-primary px-2 py-0.5 text-[11px] font-medium text-content-secondary">
          {permission}
        </span>
      ))}
      {list.length > (compact ? 4 : 8) && (
        <span className="rounded-full border border-border-default bg-surface-primary px-2 py-0.5 text-[11px] text-content-muted">
          +{list.length - (compact ? 4 : 8)}
        </span>
      )}
      {!list.length && <span className="text-xs text-content-muted">No permissions listed</span>}
    </div>
  );
}

function EmptyBlock({ title, detail, compact = false }: { title: string; detail: string; compact?: boolean }) {
  return (
    <div className={clsx('rounded-lg border border-dashed border-border-default bg-surface-secondary text-center', compact ? 'p-3' : 'p-8')}>
      <Sparkles className="mx-auto h-5 w-5 text-content-muted" />
      <p className="mt-2 text-sm font-semibold text-content-primary">{title}</p>
      <p className="mt-1 text-xs text-content-secondary">{detail}</p>
    </div>
  );
}

function Input({ label, value, onChange, type = 'text' }: { label: string; value: string; onChange: (value: string) => void; type?: string }) {
  return (
    <label className="block text-xs font-semibold text-content-secondary">
      {label}
      <input
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="mt-1 w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary outline-none transition focus:border-brand-400 focus:ring-2 focus:ring-brand-200 dark:focus:ring-brand-900"
      />
    </label>
  );
}

function PolicyLine({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4 rounded-lg border border-border-default bg-surface-secondary px-3 py-2">
      <span className="text-xs font-semibold uppercase tracking-[0.12em] text-content-muted">{label}</span>
      <span className="text-right text-xs font-medium text-content-primary">{value}</span>
    </div>
  );
}

function asNumber(value: unknown) {
  if (typeof value === 'number') {
    return value;
  }
  if (typeof value === 'string' && value.trim() !== '') {
    return Number(value) || 0;
  }
  return 0;
}

function asText(value: unknown, fallback = '') {
  if (value === null || value === undefined) {
    return fallback;
  }
  return String(value);
}

function formatTime(value: unknown) {
  const text = asText(value);
  if (!text) {
    return 'not recorded';
  }
  const date = new Date(text);
  if (Number.isNaN(date.getTime())) {
    return text;
  }
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date);
}

function normalizePermissions(value: unknown) {
  if (Array.isArray(value)) {
    return value.map(String);
  }
  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value);
      return Array.isArray(parsed) ? parsed.map(String) : [value];
    } catch {
      return value.split(',').map((item) => item.trim()).filter(Boolean);
    }
  }
  return [];
}

function statusTone(status: string) {
  if (['OPERATIONAL', 'ACTIVE', 'APPLIED', 'SUCCESS', 'SYSTEM'].includes(status)) {
    return 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/30 dark:text-emerald-300';
  }
  if (['WATCH', 'PENDING', 'CUSTOM', 'SYNCING'].includes(status)) {
    return 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-300';
  }
  return 'border-red-200 bg-red-50 text-red-700 dark:border-red-800 dark:bg-red-950/30 dark:text-red-300';
}

function toneText(tone: string) {
  const map: Record<string, string> = {
    blue: 'text-blue-500',
    emerald: 'text-emerald-500',
    violet: 'text-brand-500',
    amber: 'text-amber-500',
  };
  return map[tone] || 'text-brand-500';
}

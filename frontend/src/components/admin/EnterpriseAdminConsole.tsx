'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
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
  FileCheck2,
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
import { Card } from '@/components/ui/Card';
import { MetricCard, PageHeader } from '@/components/ui/PageChrome';
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
import {
  createSendGovernancePolicy,
  listSendGovernancePolicies,
  sendGovernancePolicyItems,
  updateSendGovernancePolicy,
  type SendGovernancePolicy,
  type SendGovernancePolicyRequest,
} from '@/lib/send-governance-policy-api';

type SectionId = 'command' | 'users' | 'roles' | 'federation' | 'audit' | 'configuration' | 'operations' | 'differentiation' | 'global' | 'performance';

type UserDraft = {
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  password: string;
};

type SendGovernancePolicyDraft = {
  policyKey: string;
  name: string;
  description: string;
  classification: string;
  providerId: string;
  sendingDomain: string;
  unsubscribePolicy: string;
  publicationPolicy: string;
  sendLogRetentionDays: string;
  suppressionRequired: boolean;
  consentRequired: boolean;
  trackingAllowed: boolean;
  active: boolean;
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
  { id: 'differentiation', label: 'Differentiation', kicker: 'AI review, omni, SLO, SDKs', icon: Sparkles },
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

const defaultPolicyDraft: SendGovernancePolicyDraft = {
  policyKey: '',
  name: '',
  description: '',
  classification: 'COMMERCIAL',
  providerId: '',
  sendingDomain: '',
  unsubscribePolicy: 'REQUIRED',
  publicationPolicy: 'APPROVED_CONTENT_REQUIRED',
  sendLogRetentionDays: '365',
  suppressionRequired: true,
  consentRequired: false,
  trackingAllowed: true,
  active: true,
};

const publicationPolicyOptions = [
  'APPROVED_CONTENT_REQUIRED',
  'APPROVAL_REQUIRED',
  'DRAFT_ALLOWED',
  'NO_APPROVAL_REQUIRED',
];

const unsubscribePolicyOptions = ['REQUIRED', 'REQUIRED_FOR_COMMERCIAL', 'OPTIONAL'];

const ADMIN_POLL_INTERVAL_MS = 15000;
const OPERATOR_ROLE_KEY = 'OPERATIONS_GOVERNOR';
const OPERATOR_ROLE_DESCRIPTION = 'Manages delivery, config, audit investigation, and workflow recovery.';
const OPERATOR_ROLE_PERMISSIONS = ['delivery:*', 'config:*', 'audit:*', 'workflow:*', 'tracking:read'];

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
  const [seedingOperatorRole, setSeedingOperatorRole] = useState(false);
  const [operatorRoleSeeded, setOperatorRoleSeeded] = useState(false);
  const [pendingUserIds, setPendingUserIds] = useState<Set<string>>(new Set());
  const [loadIssues, setLoadIssues] = useState<string[]>([]);
  const loadInFlightRef = useRef(false);
  const loadSequenceRef = useRef(0);

  const canAdmin = useMemo(
    () => isAdmin() || roles.some((role) => ['PLATFORM_ADMIN', 'ORG_ADMIN'].includes(role)),
    [isAdmin, roles]
  );

  const operatorRoleExists = useMemo(
    () => operatorRoleSeeded || hasRoleKey(access.roles || [], OPERATOR_ROLE_KEY),
    [operatorRoleSeeded, access.roles]
  );

  const load = useCallback(async (silent = false) => {
    if (loadInFlightRef.current) {
      return;
    }
    loadInFlightRef.current = true;
    const sequence = loadSequenceRef.current + 1;
    loadSequenceRef.current = sequence;

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
      if (sequence !== loadSequenceRef.current) {
        return;
      }
      setLoadIssues(issues);
      if (dashboardRes.status === 'fulfilled') {
        setDashboard(dashboardRes.value);
      }
      if (accessRes.status === 'fulfilled') {
        setAccess(accessRes.value);
        if (hasRoleKey(accessRes.value.roles || [], OPERATOR_ROLE_KEY)) {
          setOperatorRoleSeeded(true);
        }
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
      loadInFlightRef.current = false;
      if (sequence === loadSequenceRef.current) {
        setLoading(false);
        setRefreshing(false);
      }
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

    let timer: number | undefined;
    const stopPolling = () => {
      if (timer !== undefined) {
        window.clearInterval(timer);
        timer = undefined;
      }
    };
    const poll = () => {
      if (document.visibilityState === 'visible') {
        void load(true);
      }
    };
    const startPolling = () => {
      if (timer === undefined) {
        timer = window.setInterval(poll, ADMIN_POLL_INTERVAL_MS);
      }
    };
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        void load(true);
        startPolling();
      } else {
        stopPolling();
      }
    };

    void load();
    if (document.visibilityState === 'visible') {
      startPolling();
    }
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => {
      stopPolling();
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      loadSequenceRef.current += 1;
    };
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
    } catch (error: unknown) {
      addToast({ type: 'error', title: 'User create failed', message: getErrorMessage(error, 'Unable to create user.') });
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
    } catch (error: unknown) {
      addToast({ type: 'error', title: 'User update failed', message: getErrorMessage(error, 'Unable to update user.') });
    } finally {
      setUserPending(user.id, false);
    }
  };

  const sendReset = async (user: AdminUser) => {
    setUserPending(user.id, true);
    try {
      await requestUserPasswordReset(user.email);
      addToast({ type: 'success', title: 'Reset workflow queued', message: user.email });
    } catch (error: unknown) {
      addToast({ type: 'error', title: 'Reset request failed', message: getErrorMessage(error, 'Unable to queue reset workflow.') });
    } finally {
      setUserPending(user.id, false);
    }
  };

  const createOperatorRole = async () => {
    if (operatorRoleExists) {
      addToast({ type: 'info', title: 'Role already exists', message: `${OPERATOR_ROLE_KEY} is already available.` });
      return;
    }

    setSeedingOperatorRole(true);
    try {
      const created = await coreApi.createRole({
        roleKey: OPERATOR_ROLE_KEY,
        displayName: 'Operations Governor',
        description: OPERATOR_ROLE_DESCRIPTION,
        system: false,
        permissions: OPERATOR_ROLE_PERMISSIONS,
        metadata: { createdFrom: 'enterprise-admin-console' },
      });
      setOperatorRoleSeeded(true);
      setAccess((current) => withOperatorRole(current, created));
      addToast({ type: 'success', title: 'Role created', message: 'Propagation event recorded by the access engine.' });
      void load(true);
    } catch (error: unknown) {
      if (isConflictError(error)) {
        setOperatorRoleSeeded(true);
        setAccess((current) => withOperatorRole(current));
        addToast({ type: 'info', title: 'Role already exists', message: `${OPERATOR_ROLE_KEY} is already available.` });
        void load(true);
        return;
      }
      addToast({ type: 'error', title: 'Role create failed', message: getErrorMessage(error, 'Unable to create role.') });
    } finally {
      setSeedingOperatorRole(false);
    }
  };

  if (!canAdmin) {
    return null;
  }

  return (
    <div className="space-y-6 text-content-primary">
      <PageHeader
        eyebrow="Enterprise administration"
        title="Admin Control Plane"
        description="Govern users, runtime policy, audit evidence, and configuration propagation while keeping menus, workflows, and API permissions aligned with tenant context."
        action={(
          <div className="flex flex-wrap items-center gap-2">
            <Button onClick={() => load(true)} loading={refreshing} icon={<RefreshCcw className="h-4 w-4" />}>
              Refresh
            </Button>
            <Button variant="secondary" onClick={() => setActive('configuration')} icon={<Settings2 className="h-4 w-4" />}>
              Runtime controls
            </Button>
          </div>
        )}
      />

      <Card className="overflow-hidden !p-0">
        <div className="border-b border-border-default bg-surface-elevated/70 p-4">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
            <SectionHeader
              icon={ShieldCheck}
              title="Propagation Mesh"
              subtitle="Live admin state, access changes, and dependent module sync events."
            />
            <StatusBadge status={asText(dashboard?.health?.status, 'SYNCING')} />
          </div>
        </div>
        <div className="grid gap-4 p-4 lg:grid-cols-[minmax(0,1fr)_360px]">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.16em] text-content-muted">Module path</p>
            <div className="mt-3 flex flex-wrap items-center gap-2">
              {moduleFlow.map((item, index) => (
                <div key={item} className="flex items-center gap-2">
                  <div className="rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-xs font-semibold text-content-primary">
                    {item}
                  </div>
                  {index < moduleFlow.length - 1 && <ArrowRight className="h-4 w-4 text-content-muted" />}
                </div>
              ))}
            </div>
          </div>
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-[0.16em] text-content-muted">Recent sync</p>
            {(dashboard?.syncEvents?.slice(0, 4) || syncEvents.slice(0, 4)).map((event, index) => (
              <div
                key={`${asText(event.event_type || event.eventType, 'sync')}-${index}`}
                className="flex items-center justify-between gap-3 rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-xs"
              >
                <span className="font-semibold text-content-primary">{asText(event.event_type || event.eventType, 'SYNC_EVENT')}</span>
                <span className="text-content-secondary">{formatTime(event.created_at || event.createdAt)}</span>
              </div>
            ))}
            {!dashboard?.syncEvents?.length && !syncEvents.length ? (
              <p className="rounded-lg border border-dashed border-border-default bg-surface-secondary px-3 py-2 text-xs text-content-muted">
                No propagation events recorded yet.
              </p>
            ) : null}
          </div>
        </div>
      </Card>

      {loadIssues.length ? (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-200">
          Partial admin data loaded. Unavailable: {loadIssues.join(', ')}.
        </div>
      ) : null}

      <div className="grid gap-4 lg:grid-cols-[260px_minmax(0,1fr)]">
        <aside className="h-fit">
          <Card className="!p-2">
            {sections.map((section) => {
              const Icon = section.icon;
              const selected = active === section.id;
              return (
                <button
                  key={section.id}
                  type="button"
                  onClick={() => setActive(section.id)}
                  className={clsx(
                    'mb-1 flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent',
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
          </Card>
        </aside>

        <main className="min-w-0">
          {loading ? (
            <LoadingPanel />
          ) : (
            <>
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
              {active === 'roles' && (
                <RoleEngine
                  key="roles"
                  access={access}
                  createOperatorRole={createOperatorRole}
                  operatorRoleExists={operatorRoleExists}
                  seedingOperatorRole={seedingOperatorRole}
                />
              )}
              {active === 'federation' && <FederationConfigPanel key="federation" />}
              {active === 'audit' && <AuditCenter key="audit" syncEvents={syncEvents} />}
              {active === 'configuration' && <ConfigurationCenter key="configuration" />}
              {active === 'operations' && <PlatformOperations key="operations" />}
              {active === 'differentiation' && <DifferentiationPlatform key="differentiation" />}
              {active === 'global' && <GlobalEnterprise key="global" />}
              {active === 'performance' && <PerformanceIntelligence key="performance" />}
            </>
          )}
        </main>
      </div>
    </div>
  );
}

function CommandDashboard({ dashboard, syncEvents }: { dashboard: AdminOperationsDashboard | null; syncEvents: Array<Record<string, unknown>> }) {
  const stats = dashboard?.stats || {};
  const metrics: Array<{
    label: string;
    value: number;
    helper: string;
    icon: typeof Gauge;
    accent: 'brand' | 'success' | 'warning' | 'danger';
  }> = [
    { label: 'Workspaces', value: asNumber(stats.workspaces), helper: 'Tenant workspace records', icon: Boxes, accent: 'brand' },
    { label: 'Active users', value: asNumber(stats.memberships), helper: 'Current memberships', icon: Users, accent: 'success' },
    { label: 'Runtime configs', value: asNumber(stats.runtimeConfigs), helper: 'Governed config entries', icon: DatabaseZap, accent: 'warning' },
    { label: 'Audit 24h', value: asNumber(stats.auditEvents24h), helper: 'Recent admin evidence', icon: Activity, accent: 'danger' },
  ];

  return (
    <>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {metrics.map((metric) => {
          const Icon = metric.icon;
          return (
            <MetricCard
              key={metric.label}
              label={metric.label}
              value={metric.value}
              helper={metric.helper}
              icon={<Icon size={18} />}
              accent={metric.accent}
            />
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
              <div
                key={asText(module.key, String(index))}
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
                  <div
                    className="h-full rounded-full bg-gradient-to-r from-emerald-400 via-blue-500 to-brand-500"
                    style={{ width: `${Math.min(96, 28 + asNumber(module.configs) * 8 + asNumber(module.syncEvents) * 10)}%` }}
                  />
                </div>
              </div>
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
    </>
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
    <>
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
                  className="w-full rounded-lg border border-border-default bg-surface-primary py-2 pl-10 pr-3 text-sm outline-none focus:border-brand-400 focus:ring-2 focus:ring-brand-200 dark:focus:ring-brand-900"
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
    </>
  );
}

function RoleEngine({
  access,
  createOperatorRole,
  operatorRoleExists,
  seedingOperatorRole,
}: {
  access: Record<string, Array<Record<string, unknown>>>;
  createOperatorRole: () => Promise<void>;
  operatorRoleExists: boolean;
  seedingOperatorRole: boolean;
}) {
  const roles = access.roles || [];
  const groups = access.permissionGroups || [];
  const grants = access.delegatedAccess || [];
  const propagation = access.propagation || [];

  return (
    <>
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_380px]">
        <section className="rounded-lg border border-border-default bg-surface-elevated p-5 shadow-sm">
          <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
            <SectionHeader icon={Fingerprint} title="Permission Resolution Engine" subtitle="Role inheritance, feature controls, direct grants, and field/action permission groups." />
            <Button
              variant="secondary"
              onClick={createOperatorRole}
              loading={seedingOperatorRole}
              disabled={operatorRoleExists}
              title={operatorRoleExists ? 'Operations Governor role already exists' : undefined}
              icon={operatorRoleExists ? <CheckCircle2 className="h-4 w-4" /> : <Plus className="h-4 w-4" />}
            >
              {operatorRoleExists ? 'Operator role seeded' : 'Seed operator role'}
            </Button>
          </div>
          <div className="mt-5 grid gap-3 md:grid-cols-3">
            <EngineNode icon={Users} title="Principal" detail="user, team, workspace" />
            <EngineNode icon={BadgeCheck} title="Role + Groups" detail="inherit, compose, scope" />
            <EngineNode icon={Workflow} title="Propagation" detail="menus, APIs, workflows" />
          </div>
          <div className="mt-5 grid gap-3 lg:grid-cols-2">
            {roles.map((role, index) => (
              <div
                key={`${asText(role.role_key || role.roleKey, 'role')}-${index}`}
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
              </div>
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
    </>
  );
}

function AuditCenter({ syncEvents }: { syncEvents: Array<Record<string, unknown>> }) {
  return (
    <>
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
    </>
  );
}

function SendGovernancePolicyPanel() {
  const { addToast } = useToast();
  const [policies, setPolicies] = useState<SendGovernancePolicy[]>([]);
  const [selectedPolicyId, setSelectedPolicyId] = useState('');
  const [draft, setDraft] = useState<SendGovernancePolicyDraft>(defaultPolicyDraft);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const selectedPolicy = useMemo(
    () => policies.find((policy) => policy.id === selectedPolicyId),
    [policies, selectedPolicyId]
  );
  const activePolicies = policies.filter((policy) => policy.active !== false).length;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await listSendGovernancePolicies(0, 100);
      const items = sendGovernancePolicyItems(response).map(normalizeAdminPolicy);
      setPolicies(items);
      setSelectedPolicyId((current) => {
        if (current || !items[0]) {
          return current;
        }
        setDraft(policyDraftFromPolicy(items[0]));
        return items[0].id;
      });
    } catch (error: unknown) {
      addToast({ type: 'error', title: 'Policy catalog unavailable', message: getErrorMessage(error, 'Unable to load send-governance policies.') });
    } finally {
      setLoading(false);
    }
  }, [addToast]);

  useEffect(() => {
    void load();
  }, [load]);

  const selectPolicy = (policy: SendGovernancePolicy) => {
    setSelectedPolicyId(policy.id);
    setDraft(policyDraftFromPolicy(policy));
  };

  const startNewPolicy = () => {
    setSelectedPolicyId('');
    setDraft(defaultPolicyDraft);
  };

  const savePolicy = async () => {
    const payload = policyPayloadFromDraft(draft);
    if (!payload.policyKey || !payload.name) {
      addToast({ type: 'error', title: 'Policy needs key and name' });
      return;
    }
    setSaving(true);
    try {
      const saved = selectedPolicyId
        ? await updateSendGovernancePolicy(selectedPolicyId, payload)
        : await createSendGovernancePolicy(payload);
      const normalized = normalizeAdminPolicy(saved);
      setPolicies((current) => selectedPolicyId
        ? current.map((policy) => (policy.id === normalized.id ? normalized : policy))
        : [normalized, ...current]);
      setSelectedPolicyId(normalized.id);
      setDraft(policyDraftFromPolicy(normalized));
      addToast({
        type: 'success',
        title: selectedPolicyId ? 'Policy updated' : 'Policy created',
        message: `${policyDisplayName(normalized)} is available for campaign selection.`,
      });
    } catch (error: unknown) {
      addToast({ type: 'error', title: 'Policy save failed', message: getErrorMessage(error, 'Unable to save send-governance policy.') });
    } finally {
      setSaving(false);
    }
  };

  return (
    <section data-testid="send-governance-policy-panel" className="rounded-lg border border-border-default bg-surface-elevated p-5 shadow-sm">
      <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
        <SectionHeader icon={FileCheck2} title="Send Governance Policies" subtitle="Versioned delivery policy controls for campaign approval and launch." />
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" onClick={() => void load()} loading={loading} icon={<RefreshCcw className="h-4 w-4" />}>
            Refresh
          </Button>
          <Button variant="secondary" onClick={startNewPolicy} icon={<Plus className="h-4 w-4" />}>
            New policy
          </Button>
        </div>
      </div>

      <div className="mt-4 grid gap-3 sm:grid-cols-3">
        <MiniStat label="policies" value={policies.length} />
        <MiniStat label="active" value={activePolicies} />
        <MiniStat label="inactive" value={Math.max(0, policies.length - activePolicies)} />
      </div>

      <div className="mt-4 grid gap-4 xl:grid-cols-[minmax(0,1fr)_320px]">
        <div className="space-y-3">
          {policies.map((policy) => (
            <button
              key={policy.id}
              type="button"
              aria-label={`Edit ${policyDisplayName(policy)}`}
              onClick={() => selectPolicy(policy)}
              className={clsx(
                'w-full rounded-lg border p-3 text-left transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent',
                policy.id === selectedPolicyId ? 'border-brand-400 bg-brand-50/70 dark:bg-brand-900/20' : 'border-border-default bg-surface-secondary hover:bg-surface-primary'
              )}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold text-content-primary">{policyDisplayName(policy)}</p>
                  <p className="mt-1 text-xs text-content-secondary">
                    {policyValueLabel(policy.publicationPolicy)} · {policyValueLabel(policy.unsubscribePolicy)}
                  </p>
                </div>
                <StatusBadge status={policy.active === false ? 'INACTIVE' : 'ACTIVE'} />
              </div>
              <div className="mt-3 grid gap-2 text-xs sm:grid-cols-3">
                <span className="rounded-lg border border-border-default bg-surface-primary px-2 py-1 text-content-secondary">
                  Retention {policy.sendLogRetentionDays || 365}d
                </span>
                <span className="rounded-lg border border-border-default bg-surface-primary px-2 py-1 text-content-secondary">
                  {policy.trackingAllowed === false ? 'Tracking blocked' : 'Tracking allowed'}
                </span>
                <span className="rounded-lg border border-border-default bg-surface-primary px-2 py-1 text-content-secondary">
                  Updated {formatTime(policy.updatedAt)}
                </span>
              </div>
            </button>
          ))}
          {!policies.length && !loading && (
            <EmptyBlock title="No policies" detail="Create a workspace send-governance policy before approving campaign launches." compact />
          )}
        </div>

        <aside className="space-y-3 rounded-lg border border-border-default bg-surface-secondary p-4">
          <div className="space-y-3">
            <Input label="Policy key" value={draft.policyKey} onChange={(value) => setDraft((current) => ({ ...current, policyKey: value }))} />
            <Input label="Policy name" value={draft.name} onChange={(value) => setDraft((current) => ({ ...current, name: value }))} />
            <Input label="Provider ID" value={draft.providerId} onChange={(value) => setDraft((current) => ({ ...current, providerId: value }))} />
            <Input label="Sending domain" value={draft.sendingDomain} onChange={(value) => setDraft((current) => ({ ...current, sendingDomain: value }))} />
            <Input label="Retention days" type="number" value={draft.sendLogRetentionDays} onChange={(value) => setDraft((current) => ({ ...current, sendLogRetentionDays: value }))} />
            <label className="block text-xs font-semibold text-content-secondary">
              Publication policy
              <select
                aria-label="Publication policy"
                value={draft.publicationPolicy}
                onChange={(event) => setDraft((current) => ({ ...current, publicationPolicy: event.target.value }))}
                className="mt-1 w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary outline-none focus:border-brand-400 focus:ring-2 focus:ring-brand-200 dark:focus:ring-brand-900"
              >
                {publicationPolicyOptions.map((option) => (
                  <option key={option} value={option}>{policyValueLabel(option)}</option>
                ))}
              </select>
            </label>
            <label className="block text-xs font-semibold text-content-secondary">
              Unsubscribe policy
              <select
                aria-label="Unsubscribe policy"
                value={draft.unsubscribePolicy}
                onChange={(event) => setDraft((current) => ({ ...current, unsubscribePolicy: event.target.value }))}
                className="mt-1 w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary outline-none focus:border-brand-400 focus:ring-2 focus:ring-brand-200 dark:focus:ring-brand-900"
              >
                {unsubscribePolicyOptions.map((option) => (
                  <option key={option} value={option}>{policyValueLabel(option)}</option>
                ))}
              </select>
            </label>
            <div className="grid gap-2 text-sm text-content-primary">
              {([
                ['suppressionRequired', 'Suppression required'],
                ['consentRequired', 'Consent required'],
                ['trackingAllowed', 'Tracking allowed'],
                ['active', 'Policy active'],
              ] as const).map(([key, label]) => (
                <label key={key} className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    aria-label={label}
                    checked={draft[key]}
                    onChange={(event) => setDraft((current) => ({ ...current, [key]: event.target.checked }))}
                  />
                  {label}
                </label>
              ))}
            </div>
          </div>
          <div className="space-y-2">
            <PolicyLine label="Policy version" value={selectedPolicy?.version ? `v${selectedPolicy.version}` : 'new draft'} />
            <PolicyLine label="Approval state" value={policyValueLabel(selectedPolicy?.publicationPolicy ?? draft.publicationPolicy)} />
            <PolicyLine label="Last updated" value={formatTime(selectedPolicy?.updatedAt)} />
          </div>
          <Button onClick={() => void savePolicy()} loading={saving} className="w-full">
            {selectedPolicyId ? 'Update policy' : 'Create policy'}
          </Button>
        </aside>
      </div>
    </section>
  );
}

function ConfigurationCenter() {
  return (
    <>
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_380px]">
        <div className="space-y-4">
          <SendGovernancePolicyPanel />
          <AdminConfigPanel />
        </div>
        <aside className="space-y-4">
          <BootstrapStatusPanel />
          <PlatformCorePanel />
        </aside>
      </div>
    </>
  );
}

function PlatformOperations() {
  return (
    <>
      <div className="grid gap-4 xl:grid-cols-2">
        <BrandingPanel />
        <WebhookPanel />
        <PublicContentPanel />
        <ContactRequestsPanel />
      </div>
    </>
  );
}

function DifferentiationPlatform() {
  return (
    <>
      <DifferentiationPlatformPanel />
    </>
  );
}

function GlobalEnterprise() {
  return (
    <>
      <GlobalEnterprisePanel />
    </>
  );
}

function PerformanceIntelligence() {
  return (
    <>
      <PerformanceIntelligencePanel />
    </>
  );
}

function LoadingPanel() {
  return (
    <div className="rounded-lg border border-border-default bg-surface-elevated p-8 text-center shadow-sm">
      <Loader2 className="mx-auto h-8 w-8 text-brand-500" />
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
    <div className="rounded-lg border border-border-default bg-surface-secondary p-4">
      <Icon className="h-5 w-5 text-brand-500" />
      <p className="mt-3 font-semibold">{title}</p>
      <p className="mt-1 text-xs text-content-secondary">{detail}</p>
    </div>
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
        className="mt-1 w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary outline-none focus:border-brand-400 focus:ring-2 focus:ring-brand-200 dark:focus:ring-brand-900"
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

type SendGovernancePolicyAlias = SendGovernancePolicy & {
  policy_key?: unknown;
  publication_policy?: unknown;
  unsubscribe_policy?: unknown;
  send_log_retention_days?: unknown;
  provider_id?: unknown;
  sending_domain?: unknown;
  created_at?: unknown;
  updated_at?: unknown;
  is_active?: unknown;
};

function normalizeAdminPolicy(policy: SendGovernancePolicy): SendGovernancePolicy {
  const record = policy as SendGovernancePolicyAlias;
  const version = asNumber(policy.version);
  const retentionDays = asNumber(policy.sendLogRetentionDays ?? record.send_log_retention_days);
  return {
    ...policy,
    policyKey: asText(policy.policyKey ?? record.policy_key, policy.id),
    name: asText(policy.name, asText(policy.policyKey ?? record.policy_key, policy.id)),
    publicationPolicy: asText(policy.publicationPolicy ?? record.publication_policy, 'APPROVED_CONTENT_REQUIRED'),
    unsubscribePolicy: asText(policy.unsubscribePolicy ?? record.unsubscribe_policy, 'REQUIRED'),
    sendLogRetentionDays: retentionDays || undefined,
    providerId: asText(policy.providerId ?? record.provider_id),
    sendingDomain: asText(policy.sendingDomain ?? record.sending_domain),
    active: asBooleanValue(policy.active ?? record.is_active, true),
    suppressionRequired: asBooleanValue(policy.suppressionRequired, true),
    consentRequired: asBooleanValue(policy.consentRequired, false),
    trackingAllowed: asBooleanValue(policy.trackingAllowed, true),
    version: version || undefined,
    createdAt: asText(policy.createdAt ?? record.created_at),
    updatedAt: asText(policy.updatedAt ?? record.updated_at),
  };
}

function policyDraftFromPolicy(policy: SendGovernancePolicy): SendGovernancePolicyDraft {
  return {
    policyKey: asText(policy.policyKey),
    name: asText(policy.name),
    description: asText(policy.description),
    classification: asText(policy.classification, 'COMMERCIAL'),
    providerId: asText(policy.providerId),
    sendingDomain: asText(policy.sendingDomain),
    unsubscribePolicy: asText(policy.unsubscribePolicy, 'REQUIRED'),
    publicationPolicy: asText(policy.publicationPolicy, 'APPROVED_CONTENT_REQUIRED'),
    sendLogRetentionDays: String(policy.sendLogRetentionDays || 365),
    suppressionRequired: policy.suppressionRequired !== false,
    consentRequired: policy.consentRequired === true,
    trackingAllowed: policy.trackingAllowed !== false,
    active: policy.active !== false,
  };
}

function policyPayloadFromDraft(draft: SendGovernancePolicyDraft): SendGovernancePolicyRequest {
  const retentionDays = Number(draft.sendLogRetentionDays || 365);
  return {
    policyKey: draft.policyKey.trim(),
    name: draft.name.trim(),
    description: draft.description.trim() || undefined,
    classification: draft.classification || 'COMMERCIAL',
    providerId: draft.providerId.trim() || undefined,
    sendingDomain: draft.sendingDomain.trim() || undefined,
    unsubscribePolicy: draft.unsubscribePolicy,
    publicationPolicy: draft.publicationPolicy,
    sendLogRetentionDays: Number.isFinite(retentionDays) ? Math.min(2555, Math.max(1, retentionDays)) : 365,
    suppressionRequired: draft.suppressionRequired,
    consentRequired: draft.consentRequired,
    trackingAllowed: draft.trackingAllowed,
    active: draft.active,
  };
}

function policyValueLabel(value: unknown, fallback = 'Not set') {
  return asText(value, fallback).replaceAll('_', ' ');
}

function policyDisplayName(policy: SendGovernancePolicy | undefined) {
  if (!policy) {
    return 'No policy selected';
  }
  const label = asText(policy.name, policy.policyKey || policy.id);
  return policy.version ? `${label} v${policy.version}` : label;
}

function withOperatorRole(
  access: Record<string, Array<Record<string, unknown>>>,
  role: Record<string, unknown> = operatorRoleRecord()
) {
  const roles = access.roles || [];
  if (hasRoleKey(roles, OPERATOR_ROLE_KEY)) {
    return access;
  }
  return { ...access, roles: [role, ...roles] };
}

function operatorRoleRecord(): Record<string, unknown> {
  return {
    role_key: OPERATOR_ROLE_KEY,
    display_name: 'Operations Governor',
    description: OPERATOR_ROLE_DESCRIPTION,
    is_system: false,
    permissions: [...OPERATOR_ROLE_PERMISSIONS],
  };
}

function hasRoleKey(roles: Array<Record<string, unknown>>, roleKey: string) {
  const normalizedRoleKey = roleKey.toUpperCase();
  return roles.some((role) =>
    [role.role_key, role.roleKey, role.key, role.name].some(
      (candidate) => asText(candidate).toUpperCase() === normalizedRoleKey
    )
  );
}

function isConflictError(error: unknown) {
  if (typeof error !== 'object' || error === null) {
    return false;
  }
  const candidate = error as {
    normalized?: { status?: unknown; errorCode?: unknown; message?: unknown };
    response?: { status?: unknown };
    status?: unknown;
    message?: unknown;
  };
  const status = asNumber(candidate.normalized?.status ?? candidate.response?.status ?? candidate.status);
  if (status === 409) {
    return true;
  }
  const errorCode = asText(candidate.normalized?.errorCode).toUpperCase();
  const message = `${asText(candidate.normalized?.message)} ${asText(candidate.message)}`.toLowerCase();
  return (
    ['CONFLICT', 'DUPLICATE_KEY', 'DUPLICATE_ROLE'].includes(errorCode) ||
    message.includes('already exists') ||
    message.includes('duplicate') ||
    message.includes('unique constraint')
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

function asBooleanValue(value: unknown, fallback = false) {
  if (typeof value === 'boolean') {
    return value;
  }
  if (typeof value === 'string') {
    const normalized = value.toLowerCase();
    if (normalized === 'true') {
      return true;
    }
    if (normalized === 'false') {
      return false;
    }
  }
  return fallback;
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

function getErrorMessage(error: unknown, fallback: string) {
  if (typeof error === 'object' && error !== null) {
    const normalized = 'normalized' in error ? (error as { normalized?: { message?: unknown } }).normalized : undefined;
    if (typeof normalized?.message === 'string' && normalized.message.trim()) {
      return normalized.message;
    }
    const message = 'message' in error ? (error as { message?: unknown }).message : undefined;
    if (typeof message === 'string' && message.trim()) {
      return message;
    }
  }
  return fallback;
}

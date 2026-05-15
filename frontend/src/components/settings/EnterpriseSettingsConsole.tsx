'use client';

import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { clsx } from 'clsx';
import {
  Activity,
  AlertTriangle,
  Bell,
  CheckCircle2,
  ChevronRight,
  Database,
  Globe2,
  Grid2X2,
  Laptop,
  Loader2,
  LockKeyhole,
  Mail,
  Palette,
  PlugZap,
  RefreshCcw,
  RotateCcw,
  Save,
  ShieldAlert,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  UserRound,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { MetricCard, PageHeader, Panel } from '@/components/ui/PageChrome';
import { useToast } from '@/components/ui/Toast';
import { useUIStore } from '@/stores/uiStore';
import { getUserPreferences, updateUserPreferences, type UserPreferences } from '@/lib/user-preferences-api';
import { post } from '@/lib/api-client';
import { listProviderHealth } from '@/lib/providers-api';
import { addDomain, listDomains, validateDomain } from '@/lib/deliverability-api';
import { WebhookPanel } from '@/components/admin/WebhookPanel';
import { DmarcDashboard } from '@/components/deliverability/DmarcDashboard';
import { ReputationDashboard } from '@/components/deliverability/ReputationDashboard';

type SettingsSection = 'profile' | 'security' | 'notifications' | 'modules' | 'integrations' | 'deliverability';

type SettingsMetadata = {
  profile?: Record<string, unknown>;
  security?: Record<string, unknown>;
  notifications?: Record<string, unknown>;
  modules?: Record<string, unknown>;
  integrations?: Record<string, unknown>;
};

const sectionDefs: Array<{ id: SettingsSection; label: string; detail: string; icon: typeof UserRound }> = [
  { id: 'profile', label: 'Profile', detail: 'identity, locale, workspace defaults', icon: UserRound },
  { id: 'security', label: 'Security', detail: 'sessions, recovery, MFA readiness', icon: LockKeyhole },
  { id: 'notifications', label: 'Notifications', detail: 'email, in-app, workflow alerts', icon: Bell },
  { id: 'modules', label: 'Module Preferences', detail: 'views, filters, widgets', icon: Grid2X2 },
  { id: 'integrations', label: 'Integrations', detail: 'webhooks, providers, tokens', icon: PlugZap },
  { id: 'deliverability', label: 'Deliverability', detail: 'domains, DNS, reputation', icon: Globe2 },
];

const digestOptions = ['instant', 'hourly', 'daily', 'weekly'];
const localeOptions = ['en-US', 'en-IN', 'en-GB'];
const timezoneOptions = ['Asia/Calcutta', 'UTC', 'America/New_York', 'Europe/London'];

export function EnterpriseSettingsConsole({ initialSection = 'profile' }: { initialSection?: SettingsSection }) {
  const { addToast } = useToast();
  const { setTheme, setUiMode, setDensity, theme, uiMode, density, sidebarCollapsed } = useUIStore();
  const [active, setActive] = useState<SettingsSection>(initialSection);
  const [preferences, setPreferences] = useState<UserPreferences | null>(null);
  const [metadata, setMetadata] = useState<SettingsMetadata>({});
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(true);
  const [providers, setProviders] = useState<Array<Record<string, unknown>>>([]);
  const [domains, setDomains] = useState<Array<Record<string, unknown>>>([]);
  const [newDomain, setNewDomain] = useState('');
  const [selectedDomain, setSelectedDomain] = useState('');
  const [loadIssues, setLoadIssues] = useState<string[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const sources = [
        ['Preferences', getUserPreferences()],
        ['Provider health', listProviderHealth()],
        ['Sender domains', listDomains()],
      ] as const;
      const [prefRes, providerRes, domainRes] = await Promise.allSettled(sources.map(([, request]) => request));
      const issues = sources
        .map(([label], index) => {
          const result = [prefRes, providerRes, domainRes][index];
          return result.status === 'rejected' ? label : null;
        })
        .filter(Boolean) as string[];
      setLoadIssues(issues);
      if (prefRes.status === 'fulfilled') {
        setPreferences(prefRes.value);
        setMetadata((prefRes.value.metadata || {}) as SettingsMetadata);
      }
      if (providerRes.status === 'fulfilled') {
        setProviders((providerRes.value || []) as Array<Record<string, unknown>>);
      }
      if (domainRes.status === 'fulfilled') {
        const records = Array.isArray(domainRes.value) ? domainRes.value as Array<Record<string, unknown>> : [];
        setDomains(records);
        setSelectedDomain((prev) => {
          const names = records.map(getDomainName).filter(Boolean);
          return prev && names.includes(prev) ? prev : (names[0] || '');
        });
      }
      if (issues.length) {
        addToast({
          type: 'warning',
          title: 'Some settings data unavailable',
          message: `${issues.join(', ')} did not load. Available sections remain usable.`,
        });
      }
    } finally {
      setLoading(false);
    }
  }, [addToast]);

  useEffect(() => {
    load();
  }, [load]);

  const updateMeta = (section: keyof SettingsMetadata, key: string, value: unknown) => {
    setMetadata((prev) => ({
      ...prev,
      [section]: {
        ...(prev[section] || {}),
        [key]: value,
      },
    }));
  };

  const save = async (patch: Partial<UserPreferences> = {}) => {
    setSaving(true);
    try {
      const next = await updateUserPreferences({
        uiMode,
        theme,
        density,
        sidebarCollapsed,
        metadata,
        ...patch,
      });
      setPreferences(next);
      setMetadata((next.metadata || {}) as SettingsMetadata);
      if (next.theme === 'dark' || next.theme === 'light') {
        setTheme(next.theme);
      }
      if (next.uiMode === 'BASIC' || next.uiMode === 'ADVANCED') {
        setUiMode(next.uiMode);
      }
      if (next.density) {
        setDensity(next.density);
      }
      addToast({ type: 'success', title: 'Settings saved', message: 'Preferences synced across the workspace shell.' });
    } catch (error: unknown) {
      addToast({ type: 'error', title: 'Settings save failed', message: getErrorMessage(error, 'Unable to save settings.') });
    } finally {
      setSaving(false);
    }
  };

  const addSenderDomain = async () => {
    const domainName = normalizeDomainName(newDomain);
    if (!domainName) {
      addToast({ type: 'error', title: 'Domain required', message: 'Enter a valid sender domain.' });
      return;
    }
    if (!isValidDomainName(domainName)) {
      addToast({ type: 'error', title: 'Invalid domain', message: 'Use a domain such as sender.example.com.' });
      return;
    }
    setSaving(true);
    try {
      await addDomain(domainName);
      setNewDomain('');
      setSelectedDomain(domainName);
      await load();
      addToast({ type: 'success', title: 'Sender domain added', message: 'DNS verification can run after records propagate.' });
    } catch (error: unknown) {
      addToast({ type: 'error', title: 'Domain add failed', message: getErrorMessage(error, 'Unable to add sender domain.') });
    } finally {
      setSaving(false);
    }
  };

  const verifyDomain = async (domainId: string) => {
    if (!domainId) {
      addToast({ type: 'error', title: 'Domain verification unavailable', message: 'Domain record is missing an ID.' });
      return;
    }
    setSaving(true);
    try {
      await validateDomain(domainId);
      await load();
      addToast({ type: 'success', title: 'DNS verification refreshed' });
    } catch (error: unknown) {
      addToast({ type: 'error', title: 'DNS verification failed', message: getErrorMessage(error, 'Unable to verify sender domain.') });
    } finally {
      setSaving(false);
    }
  };

  const logoutAll = async () => {
    try {
      await post('/auth/logout-all');
      addToast({ type: 'success', title: 'Other sessions revoked', message: 'Current browser may need to sign in again after refresh.' });
    } catch (error: unknown) {
      addToast({ type: 'error', title: 'Session revoke failed', message: getErrorMessage(error, 'Unable to revoke sessions.') });
    }
  };

  const summary = useMemo(() => {
    const verified = domains.filter((domain) => getDomainStatus(domain) === 'VERIFIED' || Boolean(readDomainValue(domain, ['isActive', 'is_active']))).length;
    const alerts = domains.filter((domain) => !(isDomainFlag(domain, 'spf') && isDomainFlag(domain, 'dkim') && isDomainFlag(domain, 'dmarc'))).length;
    return {
      score: domains.length ? Math.round((verified / domains.length) * 100) : 0,
      alerts,
      providers: providers.length,
    };
  }, [domains, providers]);

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Workspace settings"
        title="Enterprise Settings"
        description="Manage shell preferences, profile defaults, notifications, integrations, and deliverability controls."
        action={(
          <>
            <Button onClick={() => save()} loading={saving} icon={<Save className="h-4 w-4" />}>
              Save settings
            </Button>
            <Button variant="secondary" onClick={load} icon={<RefreshCcw className="h-4 w-4" />}>
              Refresh
            </Button>
          </>
        )}
      />

      <Panel className="p-4">
        <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
          <div className="min-w-0">
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-content-muted">Live preference mesh</p>
            <p className="mt-1 max-w-2xl text-sm text-content-secondary">
              Identity preferences sync to the active workspace shell without changing section behavior.
            </p>
          </div>
          <div className="grid min-w-0 grid-cols-3 gap-2 sm:min-w-[360px]">
            <MeshStat label="Theme" value={theme} />
            <MeshStat label="Mode" value={uiMode} />
            <MeshStat label="Density" value={density} />
          </div>
        </div>
        <div className="mt-4 grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
          {['Shell', 'Modules', 'Notifications', 'Integrations'].map((item) => (
            <div
              key={item}
              className="flex items-center justify-between rounded-lg bg-surface-secondary px-3 py-2 text-xs text-content-secondary"
            >
              <span className="font-semibold">{item}</span>
              <CheckCircle2 className="h-4 w-4 text-success" />
            </div>
          ))}
        </div>
      </Panel>

      {loadIssues.length ? (
        <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-200">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <span>Partial settings data loaded. Unavailable: {loadIssues.join(', ')}.</span>
        </div>
      ) : null}

      <div className="grid gap-4 lg:grid-cols-[260px_minmax(0,1fr)]">
        <aside className="h-fit rounded-xl border border-border-default bg-surface-elevated/95 p-2 shadow-[0_18px_45px_rgba(76,29,149,0.08)] backdrop-blur-xl">
          {sectionDefs.map((section) => {
            const Icon = section.icon;
            const selected = active === section.id;
            return (
              <button
                key={section.id}
                type="button"
                onClick={() => setActive(section.id)}
                className={clsx(
                  'mb-1 flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left',
                  selected ? 'bg-brand-500/10 text-brand-700 dark:text-brand-300' : 'text-content-secondary hover:bg-surface-secondary hover:text-content-primary'
                )}
              >
                <Icon className="h-4 w-4 shrink-0" />
                <span className="min-w-0 flex-1">
                  <span className="block text-sm font-semibold">{section.label}</span>
                  <span className="block truncate text-xs opacity-75">{section.detail}</span>
                </span>
                {selected && <ChevronRight className="h-4 w-4" />}
              </button>
            );
          })}
        </aside>

        <main className="min-w-0">
          {loading ? (
            <Panel className="p-8 text-center">
              <Loader2 className="mx-auto h-8 w-8 text-brand-500" />
              <p className="mt-4 text-sm font-semibold">Loading settings</p>
            </Panel>
          ) : (
            <>
              {active === 'profile' && <ProfileSettings key="profile" preferences={preferences} metadata={metadata} updateMeta={updateMeta} save={save} />}
              {active === 'security' && <SecuritySettings key="security" metadata={metadata} updateMeta={updateMeta} save={save} logoutAll={logoutAll} />}
              {active === 'notifications' && <NotificationSettings key="notifications" metadata={metadata} updateMeta={updateMeta} save={save} />}
              {active === 'modules' && <ModuleSettings key="modules" metadata={metadata} updateMeta={updateMeta} save={save} />}
              {active === 'integrations' && <IntegrationSettings key="integrations" providers={providers} metadata={metadata} updateMeta={updateMeta} save={save} />}
              {active === 'deliverability' && (
                <DeliverabilitySettings
                  key="deliverability"
                  domains={domains}
                  newDomain={newDomain}
                  setNewDomain={setNewDomain}
                  addSenderDomain={addSenderDomain}
                  verifyDomain={verifyDomain}
                  selectedDomain={selectedDomain}
                  setSelectedDomain={setSelectedDomain}
                  summary={summary}
                  saving={saving}
                />
              )}
            </>
          )}
        </main>
      </div>
    </div>
  );
}

function ProfileSettings({ preferences, metadata, updateMeta, save }: SettingsPaneProps & { preferences: UserPreferences | null }) {
  const profile = metadata.profile || {};
  const { setTheme, setUiMode, setDensity, theme, uiMode, density } = useUIStore();
  return (
    <Pane>
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
        <Panel>
          <PaneHeader icon={UserRound} title="Profile Settings" detail="Personal profile, localization, theme preference, and workspace display behavior." />
          <div className="mt-5 grid gap-4 md:grid-cols-2">
            <Field label="Display name" value={asText(profile.displayName)} onChange={(value) => updateMeta('profile', 'displayName', value)} />
            <Field label="Title" value={asText(profile.title)} onChange={(value) => updateMeta('profile', 'title', value)} />
            <SelectField label="Locale" value={asText(profile.locale, 'en-US')} options={localeOptions} onChange={(value) => updateMeta('profile', 'locale', value)} />
            <SelectField label="Time zone" value={asText(profile.timezone, 'Asia/Calcutta')} options={timezoneOptions} onChange={(value) => updateMeta('profile', 'timezone', value)} />
          </div>
          <div className="mt-5 grid gap-3 sm:grid-cols-3">
            <Choice label="Light" selected={theme === 'light'} onClick={() => setTheme('light')} icon={Palette} />
            <Choice label="Dark" selected={theme === 'dark'} onClick={() => setTheme('dark')} icon={Palette} />
            <Choice label={uiMode === 'ADVANCED' ? 'Advanced' : 'Basic'} selected onClick={() => setUiMode(uiMode === 'ADVANCED' ? 'BASIC' : 'ADVANCED')} icon={SlidersHorizontal} />
          </div>
          <div className="mt-5 flex flex-wrap items-center gap-3">
            {['comfortable', 'compact', 'spacious'].map((item) => (
              <button
                key={item}
                type="button"
                onClick={() => setDensity(item)}
                className={clsx('rounded-lg border px-3 py-2 text-sm font-semibold capitalize', density === item ? 'border-brand-300 bg-brand-500/10 text-brand-700 dark:border-brand-700 dark:text-brand-300' : 'border-border-default bg-surface-secondary text-content-secondary hover:text-content-primary')}
              >
                {item}
              </button>
            ))}
          </div>
          <Button className="mt-5" onClick={() => save({ theme, uiMode, density })} icon={<Save className="h-4 w-4" />}>
            Save profile
          </Button>
        </Panel>
        <Panel>
          <PaneHeader icon={Laptop} title="Session Context" detail="Hydrated from identity preferences and active tenant session." />
          <div className="mt-5 space-y-3">
            <InfoLine label="User ID" value={preferences?.userId || 'current session'} />
            <InfoLine label="Tenant" value={preferences?.tenantId || 'active tenant'} />
            <InfoLine label="Sidebar" value={preferences?.sidebarCollapsed ? 'collapsed' : 'expanded'} />
            <InfoLine label="Sync source" value="identity user_preferences" />
          </div>
        </Panel>
      </div>
    </Pane>
  );
}

function SecuritySettings({ metadata, updateMeta, save, logoutAll }: SettingsPaneProps & { logoutAll: () => Promise<void> }) {
  const security = metadata.security || {};
  return (
    <Pane>
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
        <Panel>
          <PaneHeader icon={LockKeyhole} title="Security Settings" detail="Password, MFA readiness, session revocation, trusted devices, and recovery controls." />
          <div className="mt-5 grid gap-4 md:grid-cols-2">
            <Toggle label="Require MFA prompt" value={Boolean(security.mfaPrompt)} onChange={(value) => updateMeta('security', 'mfaPrompt', value)} />
            <Toggle label="New device alerts" value={security.deviceAlerts !== false} onChange={(value) => updateMeta('security', 'deviceAlerts', value)} />
            <Toggle label="Recovery email required" value={security.recoveryEmailRequired !== false} onChange={(value) => updateMeta('security', 'recoveryEmailRequired', value)} />
            <Toggle label="Session expiry warnings" value={security.sessionWarnings !== false} onChange={(value) => updateMeta('security', 'sessionWarnings', value)} />
          </div>
          <div className="mt-5 flex flex-wrap gap-3">
            <Button onClick={() => save()} icon={<Save className="h-4 w-4" />}>Save security</Button>
            <Button variant="danger" onClick={logoutAll} icon={<RotateCcw className="h-4 w-4" />}>Revoke sessions</Button>
          </div>
        </Panel>
        <Panel>
          <PaneHeader icon={ShieldCheck} title="Security Posture" detail="Controls currently enforced or ready for enforcement." />
          <div className="mt-5 space-y-3">
            <Posture label="HTTP-only auth cookies" ok />
            <Posture label="Refresh token revocation" ok />
            <Posture label="Password reset tokens" ok />
            <Posture label="MFA provider binding" ok={Boolean(security.mfaPrompt)} />
          </div>
        </Panel>
      </div>
    </Pane>
  );
}

function NotificationSettings({ metadata, updateMeta, save }: SettingsPaneProps) {
  const notifications = metadata.notifications || {};
  return (
    <Pane>
      <Panel>
        <PaneHeader icon={Bell} title="Notification Settings" detail="Choose which workflow, delivery, and governance signals reach you." />
        <div className="mt-5 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          <Toggle label="Email notifications" value={notifications.email !== false} onChange={(value) => updateMeta('notifications', 'email', value)} />
          <Toggle label="In-app alerts" value={notifications.inApp !== false} onChange={(value) => updateMeta('notifications', 'inApp', value)} />
          <Toggle label="Push notifications" value={Boolean(notifications.push)} onChange={(value) => updateMeta('notifications', 'push', value)} />
          <Toggle label="Workflow alerts" value={notifications.workflow !== false} onChange={(value) => updateMeta('notifications', 'workflow', value)} />
          <Toggle label="Audit alerts" value={notifications.audit !== false} onChange={(value) => updateMeta('notifications', 'audit', value)} />
          <Toggle label="Provider health alerts" value={notifications.provider !== false} onChange={(value) => updateMeta('notifications', 'provider', value)} />
        </div>
        <div className="mt-5 max-w-sm">
          <SelectField label="Digest schedule" value={asText(notifications.digest, 'daily')} options={digestOptions} onChange={(value) => updateMeta('notifications', 'digest', value)} />
        </div>
        <Button className="mt-5" onClick={() => save()} icon={<Save className="h-4 w-4" />}>Save notifications</Button>
      </Panel>
    </Pane>
  );
}

function ModuleSettings({ metadata, updateMeta, save }: SettingsPaneProps) {
  const modules = metadata.modules || {};
  return (
    <Pane>
      <Panel>
        <PaneHeader icon={Grid2X2} title="Module Preferences" detail="User-specific defaults for dashboards, filters, grids, saved views, and widgets." />
        <div className="mt-5 grid gap-4 lg:grid-cols-3">
          {['Dashboard', 'Audience', 'Campaigns', 'Automation', 'Delivery', 'Analytics'].map((module) => {
            const key = module.toLowerCase();
            const value = (modules[key] || {}) as Record<string, unknown>;
            return (
              <div key={module} className="rounded-lg border border-border-default bg-surface-secondary p-4">
                <Database className="h-5 w-5 text-brand-500" />
                <p className="mt-3 font-semibold">{module}</p>
                <div className="mt-3 space-y-2">
                  <SelectField
                    label="Default view"
                    value={asText(value.view, 'operational')}
                    options={['operational', 'compact', 'analytics', 'review']}
                    onChange={(next) => updateMeta('modules', key, { ...value, view: next })}
                  />
                  <Toggle
                    label="Show advanced widgets"
                    value={value.advanced !== false}
                    onChange={(next) => updateMeta('modules', key, { ...value, advanced: next })}
                  />
                </div>
              </div>
            );
          })}
        </div>
        <Button className="mt-5" onClick={() => save()} icon={<Save className="h-4 w-4" />}>Save module preferences</Button>
      </Panel>
    </Pane>
  );
}

function IntegrationSettings({ providers, metadata, updateMeta, save }: SettingsPaneProps & { providers: Array<Record<string, unknown>> }) {
  const integrations = metadata.integrations || {};
  return (
    <Pane>
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_380px]">
        <Panel>
          <PaneHeader icon={PlugZap} title="Integration Settings" detail="API token posture, webhook delivery, provider health, and sync schedules." />
          <div className="mt-5 grid gap-4 md:grid-cols-2">
            <Field label="Primary webhook owner" value={asText(integrations.owner)} onChange={(value) => updateMeta('integrations', 'owner', value)} />
            <SelectField label="Sync schedule" value={asText(integrations.syncSchedule, 'hourly')} options={['15 minutes', 'hourly', 'daily']} onChange={(value) => updateMeta('integrations', 'syncSchedule', value)} />
            <Toggle label="Credential rotation reminders" value={integrations.rotationReminders !== false} onChange={(value) => updateMeta('integrations', 'rotationReminders', value)} />
            <Toggle label="Webhook failure alerts" value={integrations.webhookAlerts !== false} onChange={(value) => updateMeta('integrations', 'webhookAlerts', value)} />
          </div>
          <Button className="mt-5" onClick={() => save()} icon={<Save className="h-4 w-4" />}>Save integration preferences</Button>
          <div className="mt-5">
            <WebhookPanel />
          </div>
        </Panel>
        <Panel>
          <PaneHeader icon={Activity} title="Provider Health" detail="Delivery provider status from platform service." />
          <div className="mt-5 space-y-3">
            {providers.map((provider, index) => (
              <div key={`${asText(provider.id, 'provider')}-${index}`} className="rounded-lg border border-border-default bg-surface-secondary p-3">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <p className="text-sm font-semibold">{asText(provider.name, 'Provider')}</p>
                    <p className="text-xs text-content-secondary">{asText(provider.type, 'SMTP')} - priority {asText(provider.priority, 'n/a')}</p>
                  </div>
                  {provider.isActive ? <Status label={asText(provider.healthStatus, 'ACTIVE')} ok /> : <Status label="INACTIVE" />}
                </div>
              </div>
            ))}
            {!providers.length && <Empty text="No provider health records available." />}
          </div>
        </Panel>
      </div>
    </Pane>
  );
}

function DeliverabilitySettings({
  domains,
  newDomain,
  setNewDomain,
  addSenderDomain,
  verifyDomain,
  selectedDomain,
  setSelectedDomain,
  summary,
  saving,
}: {
  domains: Array<Record<string, unknown>>;
  newDomain: string;
  setNewDomain: (value: string) => void;
  addSenderDomain: () => Promise<void>;
  verifyDomain: (domainId: string) => Promise<void>;
  selectedDomain: string;
  setSelectedDomain: (domain: string) => void;
  summary: { score: number; alerts: number; providers: number };
  saving: boolean;
}) {
  return (
    <Pane>
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
        <Panel>
          <PaneHeader icon={Globe2} title="Deliverability Settings" detail="Authenticated domains, DNS checks, reputation posture, and compliance visibility." />
          <div className="mt-5 flex flex-col gap-3 sm:flex-row">
            <input
              value={newDomain}
              onChange={(event) => setNewDomain(event.target.value)}
              className="min-w-0 flex-1 rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm"
              placeholder="sender.example.com"
            />
            <Button onClick={addSenderDomain} loading={saving} icon={<Globe2 className="h-4 w-4" />}>Add domain</Button>
          </div>
          <div className="mt-5 overflow-hidden rounded-lg border border-border-default">
            <div className="overflow-x-auto">
              <div className="min-w-[720px]">
                <div className="grid grid-cols-[minmax(180px,1fr)_82px_82px_82px_190px] gap-0 bg-surface-secondary px-3 py-2 text-xs font-semibold text-content-muted">
                  <span>Domain</span>
                  <span>SPF</span>
                  <span>DKIM</span>
                  <span>DMARC</span>
                  <span className="text-right">Action</span>
                </div>
                <div className="divide-y divide-border-default">
                  {domains.map((domain, index) => {
                    const name = getDomainName(domain) || `domain-${index}`;
                    const id = asText(readDomainValue(domain, ['id']));
                    const selected = selectedDomain === name;
                    return (
                      <div key={`${id || name}-${index}`} className={clsx('grid grid-cols-[minmax(180px,1fr)_82px_82px_82px_190px] items-center gap-0 px-3 py-3 text-sm', selected ? 'bg-brand-500/10' : 'hover:bg-surface-secondary/70')}>
                        <button type="button" onClick={() => setSelectedDomain(name)} className="truncate text-left font-semibold text-content-primary hover:text-brand-600">{name}</button>
                        <DnsIcon ok={isDomainFlag(domain, 'spf')} />
                        <DnsIcon ok={isDomainFlag(domain, 'dkim')} />
                        <DnsIcon ok={isDomainFlag(domain, 'dmarc')} />
                        <div className="flex justify-end gap-2">
                          <Button variant="secondary" size="sm" onClick={() => setSelectedDomain(name)}>View</Button>
                          <Button variant="outline" size="sm" onClick={() => verifyDomain(id)} disabled={!id || saving}>Verify</Button>
                        </div>
                      </div>
                    );
                  })}
                  {!domains.length && <Empty text="No sender domains registered." />}
                </div>
              </div>
            </div>
          </div>
        </Panel>
        <aside className="space-y-4">
          <MetricBox label="Reputation" value={`${summary.score}/100`} icon={Activity} />
          <MetricBox label="Compliance alerts" value={String(summary.alerts)} icon={ShieldAlert} />
          <MetricBox label="Provider routes" value={String(summary.providers)} icon={Mail} />
        </aside>
      </div>
      {selectedDomain && (
        <div className="mt-4 grid gap-4 xl:grid-cols-2">
          <ReputationDashboard domain={selectedDomain} />
          <DmarcDashboard domain={selectedDomain} />
        </div>
      )}
    </Pane>
  );
}

type SettingsPaneProps = {
  metadata: SettingsMetadata;
  updateMeta: (section: keyof SettingsMetadata, key: string, value: unknown) => void;
  save: (patch?: Partial<UserPreferences>) => Promise<void>;
};

function Pane({ children }: { children: ReactNode }) {
  return <div>{children}</div>;
}

function PaneHeader({ icon: Icon, title, detail }: { icon: typeof UserRound; title: string; detail: string }) {
  return (
    <div className="flex items-start gap-3">
      <div className="rounded-lg border border-border-default bg-surface-secondary p-2 text-brand-600 dark:text-brand-300">
        <Icon className="h-4 w-4" />
      </div>
      <div>
        <h2 className="text-base font-semibold text-content-primary">{title}</h2>
        <p className="mt-1 text-sm text-content-secondary">{detail}</p>
      </div>
    </div>
  );
}

function MeshStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-border-default bg-surface-secondary p-3">
      <p className="text-[10px] uppercase tracking-[0.12em] text-content-muted">{label}</p>
      <p className="mt-1 truncate text-sm font-semibold capitalize">{value}</p>
    </div>
  );
}

function Field({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <label className="block text-xs font-semibold text-content-secondary">
      {label}
      <input
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="mt-1 w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary outline-none focus:border-accent focus:ring-2 focus:ring-accent/20"
      />
    </label>
  );
}

function SelectField({ label, value, options, onChange }: { label: string; value: string; options: string[]; onChange: (value: string) => void }) {
  return (
    <label className="block text-xs font-semibold text-content-secondary">
      {label}
      <select
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="mt-1 w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary"
      >
        {options.map((option) => <option key={option} value={option}>{option}</option>)}
      </select>
    </label>
  );
}

function Toggle({ label, value, onChange }: { label: string; value: boolean; onChange: (value: boolean) => void }) {
  return (
    <button
      type="button"
      onClick={() => onChange(!value)}
      className="flex items-center justify-between gap-4 rounded-lg border border-border-default bg-surface-secondary px-3 py-3 text-left hover:border-brand-300"
    >
      <span className="text-sm font-semibold">{label}</span>
      <span className={clsx('relative h-6 w-11 rounded-full', value ? 'bg-brand-600' : 'bg-border-strong')}>
        <span className={clsx('absolute top-1 h-4 w-4 rounded-full bg-white', value ? 'left-6' : 'left-1')} />
      </span>
    </button>
  );
}

function Choice({ label, selected, onClick, icon: Icon }: { label: string; selected: boolean; onClick: () => void; icon: typeof UserRound }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={clsx('rounded-lg border p-4 text-left', selected ? 'border-brand-300 bg-brand-500/10 text-brand-700 dark:border-brand-700 dark:text-brand-300' : 'border-border-default bg-surface-secondary text-content-secondary hover:text-content-primary')}
    >
      <Icon className="h-5 w-5" />
      <span className="mt-3 block text-sm font-semibold">{label}</span>
    </button>
  );
}

function InfoLine({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4 rounded-lg border border-border-default bg-surface-secondary px-3 py-2">
      <span className="text-xs font-semibold uppercase tracking-[0.12em] text-content-muted">{label}</span>
      <span className="truncate text-right text-xs font-medium text-content-primary">{value}</span>
    </div>
  );
}

function Posture({ label, ok }: { label: string; ok: boolean }) {
  return (
    <div className="flex items-center gap-3 rounded-lg border border-border-default bg-surface-secondary px-3 py-3">
      {ok ? <ShieldCheck className="h-5 w-5 text-emerald-500" /> : <ShieldAlert className="h-5 w-5 text-amber-500" />}
      <span className="text-sm font-semibold">{label}</span>
    </div>
  );
}

function Status({ label, ok = false }: { label: string; ok?: boolean }) {
  return (
    <span className={clsx('rounded-full border px-2 py-0.5 text-[11px] font-semibold', ok ? 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/30 dark:text-emerald-300' : 'border-amber-200 bg-amber-50 text-amber-700')}>
      {label}
    </span>
  );
}

function DnsIcon({ ok }: { ok: boolean }) {
  return ok ? <CheckCircle2 className="h-4 w-4 text-emerald-500" /> : <ShieldAlert className="h-4 w-4 text-amber-500" />;
}

function MetricBox({ label, value, icon: Icon }: { label: string; value: string; icon: typeof Activity }) {
  return (
    <MetricCard label={label} value={value} icon={<Icon className="h-5 w-5" />} />
  );
}

function Empty({ text }: { text: string }) {
  return (
    <div className="p-5 text-center text-sm text-content-secondary">
      <Sparkles className="mx-auto mb-2 h-5 w-5 text-content-muted" />
      {text}
    </div>
  );
}

function asText(value: unknown, fallback = '') {
  if (value === null || value === undefined) {
    return fallback;
  }
  return String(value);
}

function readDomainValue(domain: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = domain[key];
    if (value !== undefined && value !== null && value !== '') {
      return value;
    }
  }
  return undefined;
}

function getDomainName(domain: Record<string, unknown>) {
  return asText(readDomainValue(domain, ['domainName', 'domain_name'])).trim();
}

function getDomainStatus(domain: Record<string, unknown>) {
  return asText(readDomainValue(domain, ['status'])).toUpperCase();
}

function isDomainFlag(domain: Record<string, unknown>, key: 'spf' | 'dkim' | 'dmarc') {
  const value = readDomainValue(domain, [`${key}Verified`, `${key}_verified`]);
  return value === true || String(value).toLowerCase() === 'true';
}

function normalizeDomainName(value: string) {
  return value.trim().toLowerCase().replace(/^https?:\/\//, '').replace(/\/.*$/, '').replace(/\.$/, '');
}

function isValidDomainName(value: string) {
  return /^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/.test(value);
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

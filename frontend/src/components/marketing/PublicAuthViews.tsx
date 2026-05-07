'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { motion } from 'framer-motion';
import { ArrowRight, CheckCircle2, LockKeyhole, MailCheck, Moon, ShieldCheck, Sparkles, Sun, Users, Zap } from 'lucide-react';
import { get, post } from '@/lib/api-client';
import { authApi } from '@/lib/auth-api';
import { useAuth } from '@/hooks/useAuth';
import { useTenantStore } from '@/stores/tenantStore';
import { ensureActiveContext } from '@/lib/context-bootstrap';
import { ROUTES } from '@/lib/constants';
import {
  ENVIRONMENT_STORAGE_KEY,
  ROLES_STORAGE_KEY,
  TENANT_STORAGE_KEY,
  USER_STORAGE_KEY,
  WORKSPACE_STORAGE_KEY,
} from '@/lib/auth';

type PublicTheme = 'dark' | 'light';
type Step = 1 | 2 | 3;

const PUBLIC_THEME_KEY = 'legent_public_theme';

export function LoginView() {
  const router = useRouter();
  const { isAuthenticated, login, logout } = useAuth();
  const setCurrentTenant = useTenantStore((state) => state.setCurrentTenant);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [tenantId, setTenantId] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const redirectingRef = useRef(false);

  useEffect(() => {
    if (typeof window === 'undefined' || redirectingRef.current) return;
    const savedTenant = localStorage.getItem(TENANT_STORAGE_KEY);
    if (savedTenant) setTenantId(savedTenant);

    if (isAuthenticated) {
      get<{ status: string; userId?: string; tenantId?: string; workspaceId?: string | null; environmentId?: string | null }>('/auth/session')
        .then((session) => {
          if (session?.status === 'success' && session.userId) {
            ensureActiveContext({
              preferredTenantId: session.tenantId ?? null,
              preferredWorkspaceId: session.workspaceId ?? null,
              preferredEnvironmentId: session.environmentId ?? null,
            })
              .then((activeContext) => {
                if (activeContext?.workspaceId) router.replace(ROUTES.EMAIL);
                else logout();
              })
              .catch(() => logout());
          } else {
            logout();
          }
        })
        .catch(() => {
          logout();
          localStorage.removeItem(TENANT_STORAGE_KEY);
          localStorage.removeItem(WORKSPACE_STORAGE_KEY);
          localStorage.removeItem(ENVIRONMENT_STORAGE_KEY);
        });
    }
  }, [isAuthenticated, logout, router]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const formData = new FormData(event.currentTarget);
      const submittedTenantId = String(formData.get('tenantId') ?? tenantId).trim();
      const submittedEmail = String(formData.get('email') ?? email).trim();
      const submittedPassword = String(formData.get('password') ?? password);
      if (submittedTenantId) localStorage.setItem(TENANT_STORAGE_KEY, submittedTenantId);

      const response = await post<{ status: string; userId: string; tenantId: string; roles: string[]; workspaceId?: string | null; environmentId?: string | null }>(
        '/auth/login',
        { email: submittedEmail, password: submittedPassword },
        submittedTenantId ? { headers: { 'X-Tenant-Id': submittedTenantId } } : undefined
      );
      const data = (response as any).data || response;
      if (data?.status !== 'success') throw new Error('Login failed.');

      const userId = data.userId || 'anonymous';
      const roles = data.roles ?? [];
      localStorage.setItem(ROLES_STORAGE_KEY, JSON.stringify(roles));
      localStorage.setItem(USER_STORAGE_KEY, userId);
      setCurrentTenant({ id: data.tenantId || submittedTenantId, name: data.tenantId || submittedTenantId, slug: data.tenantId || submittedTenantId, status: 'ACTIVE', plan: 'STARTER' });

      const activeContext = await ensureActiveContext({
        preferredTenantId: data.tenantId || submittedTenantId,
        preferredWorkspaceId: data.workspaceId ?? null,
        preferredEnvironmentId: data.environmentId ?? null,
      });
      if (!activeContext?.workspaceId) {
        logout();
        throw new Error('Workspace context setup failed. Please sign in again.');
      }
      redirectingRef.current = true;
      login(userId, roles);
      router.replace(ROUTES.EMAIL);
    } catch (err: any) {
      redirectingRef.current = false;
      setError(err?.response?.data?.error?.message || err?.message || 'Unable to login. Please check your credentials.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthShell title="Welcome back to the command room" eyebrow="Secure operator access" supporting="Sign in with tenant context, then continue directly into your workspace.">
      <form onSubmit={handleSubmit} className="grid gap-4">
        <AuthField label="Organization/Tenant ID (Optional)" id="tenantId" name="tenantId" value={tenantId} onChange={setTenantId} placeholder="Optional when you have a default membership" />
        <AuthField label="Email address" id="email" name="email" type="email" autoComplete="username" required value={email} onChange={setEmail} placeholder="you@example.com" />
        <AuthField label="Password" id="password" name="password" type="password" autoComplete="current-password" required value={password} onChange={setPassword} placeholder="********" />
        <StatusMessage error={error} />
        <AuthButton loading={loading}>{loading ? 'Signing in...' : 'Sign in'}</AuthButton>
        <div className="flex items-center justify-between text-sm">
          <Link href="/forgot-password" className="text-[var(--public-accent)] hover:underline">Forgot password?</Link>
          <Link href="/signup" className="text-[var(--public-accent)] hover:underline">Create account</Link>
        </div>
      </form>
    </AuthShell>
  );
}

export function SignupView() {
  const router = useRouter();
  const { login } = useAuth();
  const setCurrentTenant = useTenantStore((state) => state.setCurrentTenant);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState({ firstName: '', lastName: '', companyName: '', email: '', password: '' });
  const onChange = (key: keyof typeof form) => (value: string) => setForm((prev) => ({ ...prev, [key]: value }));

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const result = await post<{ status: string; userId: string; tenantId: string; roles: string[]; workspaceId?: string | null; environmentId?: string | null }>('/auth/signup', form);
      const data = (result as any).data || result;
      localStorage.setItem(USER_STORAGE_KEY, data.userId);
      localStorage.setItem(ROLES_STORAGE_KEY, JSON.stringify(data.roles || []));
      login(data.userId, data.roles || []);
      setCurrentTenant({ id: data.tenantId, name: data.tenantId, slug: data.tenantId, status: 'ACTIVE', plan: 'STARTER' });
      await ensureActiveContext({ preferredTenantId: data.tenantId, preferredWorkspaceId: data.workspaceId ?? null, preferredEnvironmentId: data.environmentId ?? null });
      router.push('/onboarding');
    } catch (err: any) {
      setError(err?.response?.data?.error?.message || err?.message || 'Unable to create account.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthShell title="Create your operating workspace" eyebrow="Start free" supporting="Launch audience, campaigns, automation, delivery, and analytics in one governed flow.">
      <form className="grid gap-4" onSubmit={handleSubmit}>
        <div className="grid gap-4 md:grid-cols-2">
          <AuthField label="First name" id="firstName" value={form.firstName} onChange={onChange('firstName')} required placeholder="Ada" />
          <AuthField label="Last name" id="lastName" value={form.lastName} onChange={onChange('lastName')} required placeholder="Lovelace" />
        </div>
        <AuthField label="Company name" id="companyName" value={form.companyName} onChange={onChange('companyName')} required placeholder="Company name" />
        <AuthField label="Work email" id="signupEmail" type="email" value={form.email} onChange={onChange('email')} required placeholder="you@company.com" />
        <AuthField label="Password" id="signupPassword" type="password" value={form.password} onChange={onChange('password')} required placeholder="Create password" />
        <StatusMessage error={error} />
        <AuthButton loading={loading}>{loading ? 'Creating account...' : 'Create account'}</AuthButton>
      </form>
      <p className="mt-6 text-center text-sm text-[var(--public-muted)]">
        Already have account? <Link href={ROUTES.LOGIN} className="text-[var(--public-accent)] hover:underline">Login</Link>
      </p>
    </AuthShell>
  );
}

export function ForgotPasswordView() {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await authApi.forgotPassword(email.trim());
      setSent(true);
    } catch (err: any) {
      setError(err?.response?.data?.error?.message || err?.message || 'Unable to submit request.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthShell title="Recover secure access" eyebrow="Password recovery" supporting="We will send reset instructions to the verified operator email.">
      <form className="grid gap-4" onSubmit={submit}>
        <AuthField label="Email address" id="recoveryEmail" type="email" required value={email} onChange={setEmail} placeholder="you@company.com" />
        <StatusMessage error={error} success={sent ? 'Request accepted. Check your inbox.' : null} />
        <AuthButton loading={loading}>{loading ? 'Sending...' : 'Send reset link'}</AuthButton>
      </form>
      <p className="mt-6 text-center text-sm"><Link href="/login" className="text-[var(--public-accent)] hover:underline">Back to login</Link></p>
    </AuthShell>
  );
}

export function ResetPasswordView() {
  const [token, setToken] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    setToken(params.get('token') ?? '');
  }, []);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await authApi.resetPassword(token, password);
      setDone(true);
    } catch (err: any) {
      setError(err?.response?.data?.error?.message || err?.message || 'Unable to reset password.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthShell title="Set a new workspace password" eyebrow="Secure reset" supporting="Choose a strong password before returning to the Legent workspace.">
      {!token ? (
        <p className="rounded-2xl border border-red-400/30 bg-red-400/10 p-4 text-sm text-red-500">Reset token missing from URL.</p>
      ) : (
        <form className="grid gap-4" onSubmit={submit}>
          <AuthField label="New password" id="newPassword" type="password" required value={password} onChange={setPassword} placeholder="New password" minLength={8} />
          <StatusMessage error={error} success={done ? 'Password updated. You can login now.' : null} />
          <AuthButton loading={loading} disabled={done}>{loading ? 'Updating...' : 'Update password'}</AuthButton>
        </form>
      )}
      <p className="mt-6 text-center text-sm"><Link href="/login" className="text-[var(--public-accent)] hover:underline">Back to login</Link></p>
    </AuthShell>
  );
}

export function OnboardingView() {
  const router = useRouter();
  const [step, setStep] = useState<Step>(1);
  const [workspaceId, setWorkspaceId] = useState('workspace-default');
  const [senderEmail, setSenderEmail] = useState('');
  const [provider, setProvider] = useState('MAILHOG');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const progress = `${Math.round((step / 3) * 100)}%`;

  const start = async () => {
    setLoading(true);
    setError(null);
    try {
      await authApi.startOnboarding({ workspaceId, stepKey: step === 1 ? 'workspace' : step === 2 ? 'sender' : 'provider', payload: { workspaceId, senderEmail, provider } });
      setStep((prev) => Math.min(prev + 1, 3) as Step);
    } catch (err: any) {
      setError(err?.response?.data?.error?.message || err?.message || 'Unable to continue onboarding.');
    } finally {
      setLoading(false);
    }
  };

  const complete = async () => {
    setLoading(true);
    setError(null);
    try {
      await authApi.completeOnboarding({ workspaceId, payload: { workspaceId, senderEmail, provider } });
      router.push('/app/email');
    } catch (err: any) {
      setError(err?.response?.data?.error?.message || err?.message || 'Unable to complete onboarding.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthShell title="Finish workspace readiness" eyebrow="Guided setup" supporting="Create the minimum runtime context needed for safe first sends.">
      <div className="mb-7">
        <div className="h-2 w-full overflow-hidden rounded-full bg-[var(--public-panel-strong)]">
          <motion.div className="h-full rounded-full bg-gradient-to-r from-emerald-400 to-fuchsia-400" animate={{ width: progress }} />
        </div>
        <p className="mt-2 text-xs text-[var(--public-muted)]">Step {step} of 3</p>
      </div>
      <div className="grid gap-4">
        {step === 1 && <AuthField label="Workspace setup" id="workspaceId" value={workspaceId} onChange={setWorkspaceId} placeholder="workspace-default" />}
        {step === 2 && <AuthField label="Sender defaults" id="senderEmail" value={senderEmail} onChange={setSenderEmail} placeholder="updates@company.com" />}
        {step === 3 && (
          <label className="grid gap-2 text-sm font-medium text-[var(--public-text)]">
            Provider readiness
            <select value={provider} onChange={(event) => setProvider(event.target.value)} className="public-field">
              <option value="MAILHOG">MAILHOG</option>
              <option value="SMTP">SMTP</option>
              <option value="SES">AWS SES</option>
              <option value="SENDGRID">SendGrid</option>
            </select>
          </label>
        )}
      </div>
      <StatusMessage error={error} />
      <div className="mt-7 flex items-center justify-between gap-3">
        <button disabled={loading || step === 1} onClick={() => setStep((prev) => Math.max(prev - 1, 1) as Step)} className="public-button-secondary rounded-xl px-4 py-2 text-sm font-semibold disabled:opacity-50">Back</button>
        {step < 3 ? (
          <button disabled={loading} onClick={start} className="public-button-primary rounded-xl px-5 py-2 text-sm font-semibold disabled:opacity-60">{loading ? 'Saving...' : 'Continue'}</button>
        ) : (
          <button disabled={loading} onClick={complete} className="public-button-primary rounded-xl px-5 py-2 text-sm font-semibold disabled:opacity-60">{loading ? 'Finishing...' : 'Complete setup'}</button>
        )}
      </div>
    </AuthShell>
  );
}

function AuthShell({ title, eyebrow, supporting, children }: { title: string; eyebrow: string; supporting: string; children: React.ReactNode }) {
  const [theme, setTheme] = useState<PublicTheme>('light');

  useEffect(() => {
    const stored = window.localStorage.getItem(PUBLIC_THEME_KEY);
    setTheme(stored === 'dark' ? 'dark' : 'light');
  }, []);

  const toggleTheme = () => {
    setTheme((current) => {
      const next = current === 'dark' ? 'light' : 'dark';
      window.localStorage.setItem(PUBLIC_THEME_KEY, next);
      return next;
    });
  };

  return (
    <main className={`public-site ${theme === 'light' ? 'public-light' : 'public-dark'} min-h-screen overflow-hidden px-4 py-4`}>
      <div className="mx-auto flex max-w-7xl items-center justify-between">
        <Link href="/" className="public-heading rounded-xl text-lg font-semibold focus:outline-none focus:ring-2 focus:ring-[var(--public-accent)]">
          Legent <span className="public-muted ml-2 hidden text-xs font-medium uppercase tracking-[0.16em] sm:inline">Lifecycle Email OS</span>
        </Link>
        <button type="button" onClick={toggleTheme} aria-label="Toggle public theme" className="public-border rounded-xl border bg-[var(--public-panel)] p-2 text-[var(--public-text)] transition hover:-translate-y-0.5">
          {theme === 'dark' ? <Sun size={17} /> : <Moon size={17} />}
        </button>
      </div>
      <div className="public-hero-shell public-hero-grid mx-auto max-w-7xl lg:grid-cols-[0.9fr_1.1fr]">
        <motion.section initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} className="public-panel rounded-[1.45rem] p-5 md:p-7">
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--public-accent)]">{eyebrow}</p>
          <h1 className="public-heading mt-3 text-balance text-3xl font-semibold md:text-5xl">{title}</h1>
          <p className="public-muted mt-4 leading-7">{supporting}</p>
          <MiniAuthVisual />
          <div className="mt-6">{children}</div>
        </motion.section>
        <AuthVisual />
      </div>
    </main>
  );
}

function AuthVisual() {
  const items = [
    { label: 'Tenant scoped', icon: ShieldCheck },
    { label: 'Provider safe', icon: MailCheck },
    { label: 'Audience ready', icon: Users },
    { label: 'Fast setup', icon: Zap },
  ];
  return (
    <motion.aside initial={{ opacity: 0, scale: 0.96 }} animate={{ opacity: 1, scale: 1 }} className="public-panel public-hero-visual public-art-glow relative hidden rounded-[1.8rem] p-6 lg:block">
      <div className="public-visual-clip">
        <div className="public-mock-bitmap" aria-hidden="true" />
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_30%_20%,rgba(16,185,129,0.16),transparent_26%),radial-gradient(circle_at_78%_30%,rgba(217,70,239,0.18),transparent_30%)]" />
      </div>
      <div className="relative">
        <span className="inline-flex items-center gap-2 rounded-full bg-fuchsia-300/12 px-3 py-1 text-xs font-semibold text-[var(--public-accent)]"><Sparkles size={14} /> Workspace readiness</span>
        <h2 className="public-heading mt-5 text-3xl font-semibold">A secure entry point for serious email operations.</h2>
        <div className="mt-8 grid gap-4">
          {items.map((item, index) => {
            const Icon = item.icon;
            return (
              <motion.div key={item.label} animate={{ x: [0, index % 2 ? 12 : -12, 0] }} transition={{ duration: 5, repeat: Infinity, delay: index * 0.2 }} className="rounded-2xl bg-[var(--public-panel-strong)] p-4">
                <div className="flex items-center gap-3">
                  <span className="grid h-11 w-11 place-items-center rounded-2xl bg-fuchsia-300/12 text-[var(--public-accent)]"><Icon size={20} /></span>
                  <div>
                    <p className="public-heading font-semibold">{item.label}</p>
                    <p className="public-muted text-sm">Ready before operators enter the app.</p>
                  </div>
                </div>
              </motion.div>
            );
          })}
        </div>
      </div>
    </motion.aside>
  );
}

function MiniAuthVisual() {
  const steps = ['Identity', 'Workspace', 'Provider'];
  return (
    <div className="mt-5 grid gap-2 rounded-2xl bg-[var(--public-panel-strong)] p-3 lg:hidden">
      {steps.map((step, index) => (
        <motion.div key={step} animate={{ x: [0, index % 2 ? 5 : -5, 0] }} transition={{ duration: 4.5 + index * 0.2, repeat: Infinity }} className="flex items-center justify-between rounded-xl bg-[var(--public-panel)] px-3 py-2 text-xs">
          <span className="public-heading font-semibold">{step}</span>
          <span className="text-[var(--public-accent)]">{index === 2 ? 'Ready' : 'Verified'}</span>
        </motion.div>
      ))}
    </div>
  );
}

function AuthField({ label, id, value, onChange, placeholder, type = 'text', required = false, autoComplete, name, minLength }: { label: string; id: string; value: string; onChange: (value: string) => void; placeholder: string; type?: string; required?: boolean; autoComplete?: string; name?: string; minLength?: number }) {
  return (
    <label htmlFor={id} className="grid gap-2 text-sm font-medium text-[var(--public-text)]">
      {label}
      <input
        id={id}
        name={name ?? id}
        type={type}
        required={required}
        autoComplete={autoComplete}
        minLength={minLength}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        className="public-field"
      />
    </label>
  );
}

function AuthButton({ children, loading, disabled = false }: { children: React.ReactNode; loading: boolean; disabled?: boolean }) {
  return (
    <button disabled={disabled || loading} className="public-button-primary inline-flex w-full items-center justify-center gap-2 rounded-xl px-4 py-3 text-sm font-semibold disabled:opacity-60">
      {loading ? <LockKeyhole size={16} /> : <ArrowRight size={16} />}
      {children}
    </button>
  );
}

function StatusMessage({ error, success }: { error?: string | null; success?: string | null }) {
  if (error) return <p className="rounded-2xl bg-red-400/10 px-4 py-3 text-sm text-red-500">{error}</p>;
  if (success) return <p className="rounded-2xl bg-emerald-400/10 px-4 py-3 text-sm text-emerald-500"><CheckCircle2 className="mr-2 inline" size={16} />{success}</p>;
  return null;
}

'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { post } from '@/lib/api-client';
import { ROUTES } from '@/lib/constants';
import { ensureActiveContext } from '@/lib/context-bootstrap';
import { useAuth } from '@/hooks/useAuth';
import { useTenantStore } from '@/stores/tenantStore';
import { ROLES_STORAGE_KEY, USER_STORAGE_KEY } from '@/lib/auth';

export default function SignupPage() {
  const router = useRouter();
  const { login } = useAuth();
  const setCurrentTenant = useTenantStore((state) => state.setCurrentTenant);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState({
    firstName: '',
    lastName: '',
    companyName: '',
    email: '',
    password: '',
  });

  const onChange = (key: keyof typeof form) => (event: React.ChangeEvent<HTMLInputElement>) =>
    setForm((prev) => ({ ...prev, [key]: event.target.value }));

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const result = await post<{
        status: string;
        userId: string;
        tenantId: string;
        roles: string[];
        workspaceId?: string | null;
        environmentId?: string | null;
      }>('/auth/signup', form);

      const data = (result as any).data || result;
      localStorage.setItem(USER_STORAGE_KEY, data.userId);
      localStorage.setItem(ROLES_STORAGE_KEY, JSON.stringify(data.roles || []));
      login(data.userId, data.roles || []);
      setCurrentTenant({
        id: data.tenantId,
        name: data.tenantId,
        slug: data.tenantId,
        status: 'ACTIVE',
        plan: 'STARTER',
      });

      await ensureActiveContext({
        preferredTenantId: data.tenantId,
        preferredWorkspaceId: data.workspaceId ?? null,
        preferredEnvironmentId: data.environmentId ?? null,
      });

      router.push('/onboarding');
    } catch (err: any) {
      setError(err?.response?.data?.error?.message || err?.message || 'Unable to create account.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-surface-primary px-4 py-10">
      <div className="mx-auto max-w-lg rounded-3xl border border-border-default bg-surface-secondary p-8 shadow-xl shadow-black/5">
        <h1 className="text-3xl font-semibold tracking-tight">Create your workspace</h1>
        <p className="mt-2 text-sm text-content-secondary">Launch audience, campaigns, automation and analytics in one flow.</p>
        <form className="mt-8 space-y-4" onSubmit={handleSubmit}>
          <div className="grid gap-4 md:grid-cols-2">
            <input className="rounded-xl border border-border-default bg-surface-primary px-4 py-3 text-sm" placeholder="First name" value={form.firstName} onChange={onChange('firstName')} required />
            <input className="rounded-xl border border-border-default bg-surface-primary px-4 py-3 text-sm" placeholder="Last name" value={form.lastName} onChange={onChange('lastName')} required />
          </div>
          <input className="w-full rounded-xl border border-border-default bg-surface-primary px-4 py-3 text-sm" placeholder="Company name" value={form.companyName} onChange={onChange('companyName')} required />
          <input type="email" className="w-full rounded-xl border border-border-default bg-surface-primary px-4 py-3 text-sm" placeholder="you@company.com" value={form.email} onChange={onChange('email')} required />
          <input type="password" className="w-full rounded-xl border border-border-default bg-surface-primary px-4 py-3 text-sm" placeholder="Create password" value={form.password} onChange={onChange('password')} required />
          {error ? <p className="text-sm text-danger">{error}</p> : null}
          <button disabled={loading} className="w-full rounded-xl bg-brand-600 px-4 py-3 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-60">
            {loading ? 'Creating account…' : 'Create account'}
          </button>
        </form>
        <p className="mt-6 text-center text-sm text-content-secondary">
          Already have account?{' '}
          <Link href={ROUTES.LOGIN} className="text-brand-600 hover:text-brand-700">
            Login
          </Link>
        </p>
      </div>
    </div>
  );
}


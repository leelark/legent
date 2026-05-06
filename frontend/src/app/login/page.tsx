'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { post } from '@/lib/api-client';
import { useAuth } from '@/hooks/useAuth';
import { useTenantStore } from '@/stores/tenantStore';
import { TENANT_STORAGE_KEY, USER_STORAGE_KEY, ROLES_STORAGE_KEY } from '@/lib/auth';
import { ROUTES } from '@/lib/constants';
import { ensureActiveContext } from '@/lib/context-bootstrap';

export default function LoginPage() {
  const router = useRouter();
  const { isAuthenticated, login, logout } = useAuth();
  const setCurrentTenant = useTenantStore((state) => state.setCurrentTenant);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [tenantId, setTenantId] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (typeof window === 'undefined') {
      return;
    }

    const savedTenant = localStorage.getItem(TENANT_STORAGE_KEY);
    if (savedTenant) {
      setTenantId(savedTenant);
    }

    if (isAuthenticated) {
      router.replace(ROUTES.EMAIL);
    }
  }, [isAuthenticated, router]);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);

    setLoading(true);

    try {
      if (tenantId.trim()) {
        localStorage.setItem(TENANT_STORAGE_KEY, tenantId.trim());
      }

      // Call login endpoint - token is set in HTTP-only cookie by backend
      const response = await post<{
        status: string;
        userId: string;
        tenantId: string;
        roles: string[];
        workspaceId?: string | null;
        environmentId?: string | null;
      }>(
        '/auth/login',
        { email, password },
        tenantId.trim() ? { headers: { 'X-Tenant-Id': tenantId.trim() } } : undefined
      );

      const data = (response as any).data || response;
      if (data?.status !== 'success') {
        throw new Error('Login failed.');
      }

      const userId = data.userId || 'anonymous';
      const roles = data.roles ?? [];

      // Token is in HTTP-only cookie - store only non-sensitive data
      localStorage.setItem(ROLES_STORAGE_KEY, JSON.stringify(roles));
      localStorage.setItem(USER_STORAGE_KEY, userId);

      login(userId, roles);
      setCurrentTenant({
        id: data.tenantId || tenantId.trim(),
        name: data.tenantId || tenantId.trim(),
        slug: data.tenantId || tenantId.trim(),
        status: 'ACTIVE',
        plan: 'STARTER',
      });

      try {
        const activeContext = await ensureActiveContext({
          preferredTenantId: data.tenantId || tenantId.trim(),
          preferredWorkspaceId: data.workspaceId ?? null,
          preferredEnvironmentId: data.environmentId ?? null,
        });
        if (!activeContext?.workspaceId) {
          throw new Error('Workspace context unavailable for this account.');
        }
      } catch {
        logout();
        throw new Error('Workspace context setup failed. Please sign in again.');
      }

      router.push(ROUTES.EMAIL);
    } catch (err: any) {
      const message = err?.response?.data?.error?.message || err?.message || 'Unable to login. Please check your credentials.';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-surface-primary flex items-center justify-center px-4 py-10">
      <div className="mx-auto w-full max-w-md rounded-3xl border border-border-default bg-surface-secondary px-8 py-10 shadow-xl shadow-black/5">
        <div className="mb-8 text-center">
          <h1 className="text-3xl font-semibold text-content-primary">Legent Login</h1>
          <p className="mt-2 text-sm text-content-secondary">
            Sign in to manage tenants, system settings, and feature flags.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            <label className="block text-sm font-medium text-content-secondary" htmlFor="tenantId">
              Organization/Tenant ID (Optional)
            </label>
            <input
              id="tenantId"
              value={tenantId}
              onChange={(event) => setTenantId(event.target.value)}
              placeholder="Optional when you have a default membership"
              className="mt-2 w-full rounded-2xl border border-border-default bg-surface-primary px-4 py-3 text-sm text-content-primary placeholder:text-content-muted focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-content-secondary" htmlFor="email">
              Email address
            </label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="you@example.com"
              className="mt-2 w-full rounded-2xl border border-border-default bg-surface-primary px-4 py-3 text-sm text-content-primary placeholder:text-content-muted focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-content-secondary" htmlFor="password">
              Password
            </label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="••••••••"
              className="mt-2 w-full rounded-2xl border border-border-default bg-surface-primary px-4 py-3 text-sm text-content-primary placeholder:text-content-muted focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            />
          </div>

          {error && (
            <div className="rounded-2xl bg-danger/10 px-4 py-3 text-sm text-danger">
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full rounded-2xl bg-brand-600 px-4 py-3 text-sm font-semibold text-white transition hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {loading ? 'Signing in...' : 'Sign in'}
          </button>
        </form>
      </div>
    </div>
  );
}

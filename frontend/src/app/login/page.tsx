'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { post } from '@/lib/api-client';
import { useAuth } from '@/hooks/useAuth';
import { useTenantStore } from '@/stores/tenantStore';
import { parseJwtClaims, TENANT_STORAGE_KEY, USER_STORAGE_KEY, TOKEN_STORAGE_KEY, ROLES_STORAGE_KEY } from '@/lib/auth';
import { ROUTES } from '@/lib/constants';

export default function LoginPage() {
  const router = useRouter();
  const { isAuthenticated, login } = useAuth();
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

    const existingToken = localStorage.getItem(TOKEN_STORAGE_KEY);
    if (existingToken && isAuthenticated) {
      router.replace(ROUTES.EMAIL);
    }
  }, [isAuthenticated, router]);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);

    if (!tenantId.trim()) {
      setError('Tenant ID is required.');
      return;
    }

    setLoading(true);

    try {
      localStorage.setItem(TENANT_STORAGE_KEY, tenantId.trim());

      const response = await post<{ token: string }>('/auth/login', {
        email,
        password,
      });

      const token = (response as any).data?.token || (response as any).token;
      if (!token) {
        throw new Error('Login response did not return a token.');
      }

      const claims = parseJwtClaims(token);
      const roles = claims?.roles ?? [];
      const userId = claims?.sub ?? 'anonymous';

      localStorage.setItem(TOKEN_STORAGE_KEY, token);
      localStorage.setItem(ROLES_STORAGE_KEY, JSON.stringify(roles));
      localStorage.setItem(USER_STORAGE_KEY, userId);

      login(userId, token, roles);
      setCurrentTenant({
        id: tenantId.trim(),
        name: tenantId.trim(),
        slug: tenantId.trim(),
        status: 'ACTIVE',
        plan: 'STARTER',
      });

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
              Tenant ID
            </label>
            <input
              id="tenantId"
              value={tenantId}
              onChange={(event) => setTenantId(event.target.value)}
              placeholder="Enter tenant ID"
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

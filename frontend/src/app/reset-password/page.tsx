'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { authApi } from '@/lib/auth-api';

export default function ResetPasswordPage() {
  const [token, setToken] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (typeof window === 'undefined') {
      return;
    }
    const params = new URLSearchParams(window.location.search);
    setToken(params.get('token') ?? '');
  }, []);

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
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
    <div className="min-h-screen bg-surface-primary px-4 py-10">
      <div className="mx-auto max-w-md rounded-3xl border border-border-default bg-surface-secondary p-8 shadow-xl shadow-black/5">
        <h1 className="text-2xl font-semibold tracking-tight">Reset password</h1>
        {!token ? (
          <p className="mt-4 rounded-xl border border-danger/30 bg-danger/10 p-3 text-sm text-danger">Reset token missing from URL.</p>
        ) : (
          <form className="mt-6 space-y-4" onSubmit={submit}>
            <input
              type="password"
              required
              minLength={8}
              placeholder="New password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className="w-full rounded-xl border border-border-default bg-surface-primary px-4 py-3 text-sm"
            />
            {error ? <p className="text-sm text-danger">{error}</p> : null}
            {done ? <p className="text-sm text-success">Password updated. You can login now.</p> : null}
            <button disabled={loading || done} className="w-full rounded-xl bg-brand-600 px-4 py-3 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-60">
              {loading ? 'Updating…' : 'Update password'}
            </button>
          </form>
        )}
        <p className="mt-6 text-center text-sm text-content-secondary">
          <Link href="/login" className="text-brand-600 hover:text-brand-700">
            Back to login
          </Link>
        </p>
      </div>
    </div>
  );
}

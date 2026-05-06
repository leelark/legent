'use client';

import { useState } from 'react';
import Link from 'next/link';
import { authApi } from '@/lib/auth-api';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
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
    <div className="min-h-screen bg-surface-primary px-4 py-10">
      <div className="mx-auto max-w-md rounded-3xl border border-border-default bg-surface-secondary p-8 shadow-xl shadow-black/5">
        <h1 className="text-2xl font-semibold tracking-tight">Forgot password</h1>
        <p className="mt-2 text-sm text-content-secondary">We will send reset link to your email.</p>
        <form className="mt-6 space-y-4" onSubmit={submit}>
          <input
            type="email"
            required
            placeholder="you@company.com"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            className="w-full rounded-xl border border-border-default bg-surface-primary px-4 py-3 text-sm"
          />
          {error ? <p className="text-sm text-danger">{error}</p> : null}
          {sent ? <p className="text-sm text-success">Request accepted. Check your inbox.</p> : null}
          <button disabled={loading} className="w-full rounded-xl bg-brand-600 px-4 py-3 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-60">
            {loading ? 'Sending…' : 'Send reset link'}
          </button>
        </form>
        <p className="mt-6 text-center text-sm text-content-secondary">
          <Link href="/login" className="text-brand-600 hover:text-brand-700">
            Back to login
          </Link>
        </p>
      </div>
    </div>
  );
}


'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { authApi } from '@/lib/auth-api';

type Step = 1 | 2 | 3;

export default function OnboardingPage() {
  const router = useRouter();
  const [step, setStep] = useState<Step>(1);
  const [workspaceId, setWorkspaceId] = useState('workspace-default');
  const [senderEmail, setSenderEmail] = useState('');
  const [provider, setProvider] = useState('MAILHOG');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const start = async () => {
    setLoading(true);
    setError(null);
    try {
      await authApi.startOnboarding({
        workspaceId,
        stepKey: step === 1 ? 'workspace' : step === 2 ? 'sender' : 'provider',
        payload: { workspaceId, senderEmail, provider },
      });
      setStep((prev) => (Math.min(prev + 1, 3) as Step));
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
      await authApi.completeOnboarding({
        workspaceId,
        payload: { workspaceId, senderEmail, provider },
      });
      router.push('/app/email');
    } catch (err: any) {
      setError(err?.response?.data?.error?.message || err?.message || 'Unable to complete onboarding.');
    } finally {
      setLoading(false);
    }
  };

  const progress = `${Math.round((step / 3) * 100)}%`;

  return (
    <div className="min-h-screen bg-surface-primary px-4 py-10">
      <div className="mx-auto max-w-2xl rounded-3xl border border-border-default bg-surface-secondary p-8 shadow-xl shadow-black/5">
        <h1 className="text-3xl font-semibold tracking-tight">Welcome to Legent</h1>
        <p className="mt-2 text-sm text-content-secondary">Complete setup to launch first campaigns faster.</p>
        <div className="mt-6">
          <div className="h-2 w-full overflow-hidden rounded-full bg-surface-primary">
            <div className="h-full rounded-full bg-brand-600 transition-all" style={{ width: progress }} />
          </div>
          <p className="mt-2 text-xs text-content-secondary">Step {step} of 3</p>
        </div>

        <div className="mt-8 space-y-6">
          {step === 1 && (
            <div className="space-y-3">
              <h2 className="text-xl font-semibold">Workspace setup</h2>
              <input
                value={workspaceId}
                onChange={(event) => setWorkspaceId(event.target.value)}
                className="w-full rounded-xl border border-border-default bg-surface-primary px-4 py-3 text-sm"
                placeholder="workspace-default"
              />
            </div>
          )}

          {step === 2 && (
            <div className="space-y-3">
              <h2 className="text-xl font-semibold">Sender defaults</h2>
              <input
                value={senderEmail}
                onChange={(event) => setSenderEmail(event.target.value)}
                className="w-full rounded-xl border border-border-default bg-surface-primary px-4 py-3 text-sm"
                placeholder="updates@company.com"
              />
            </div>
          )}

          {step === 3 && (
            <div className="space-y-3">
              <h2 className="text-xl font-semibold">Provider readiness</h2>
              <select
                value={provider}
                onChange={(event) => setProvider(event.target.value)}
                className="w-full rounded-xl border border-border-default bg-surface-primary px-4 py-3 text-sm"
              >
                <option value="MAILHOG">MAILHOG</option>
                <option value="SMTP">SMTP</option>
                <option value="SES">AWS SES</option>
                <option value="SENDGRID">SendGrid</option>
              </select>
            </div>
          )}
        </div>

        {error ? <p className="mt-4 text-sm text-danger">{error}</p> : null}

        <div className="mt-8 flex items-center justify-between">
          <button
            disabled={loading || step === 1}
            onClick={() => setStep((prev) => (Math.max(prev - 1, 1) as Step))}
            className="rounded-xl border border-border-default px-4 py-2 text-sm text-content-secondary disabled:opacity-50"
          >
            Back
          </button>
          {step < 3 ? (
            <button disabled={loading} onClick={start} className="rounded-xl bg-brand-600 px-5 py-2 text-sm font-semibold text-white disabled:opacity-60">
              {loading ? 'Saving…' : 'Continue'}
            </button>
          ) : (
            <button disabled={loading} onClick={complete} className="rounded-xl bg-brand-600 px-5 py-2 text-sm font-semibold text-white disabled:opacity-60">
              {loading ? 'Finishing…' : 'Complete setup'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}


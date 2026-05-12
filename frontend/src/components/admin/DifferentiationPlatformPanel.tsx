'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { BrainCircuit, Code2, GitBranch, RadioTower, RefreshCcw, ShieldCheck, Sparkles, Target } from 'lucide-react';
import { AdminMetricCard, AdminPanel, AdminSkeletonRows, StatusPill } from '@/components/admin/AdminChrome';
import { Button } from '@/components/ui/Button';
import { differentiationApi } from '@/lib/differentiation-api';

type Summary = {
  copilot: any[];
  decisionPolicies: any[];
  omniFlows: any[];
  developerPackages: any[];
  sloPolicies: any[];
};

const emptySummary: Summary = {
  copilot: [],
  decisionPolicies: [],
  omniFlows: [],
  developerPackages: [],
  sloPolicies: [],
};

export function DifferentiationPlatformPanel() {
  const [summary, setSummary] = useState<Summary>(emptySummary);
  const [loading, setLoading] = useState(true);
  const [seeding, setSeeding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    Promise.allSettled([
      differentiationApi.listCopilotRecommendations(),
      differentiationApi.listDecisionPolicies(),
      differentiationApi.listOmnichannelFlows(),
      differentiationApi.listDeveloperPackages(),
      differentiationApi.listSloPolicies(),
    ])
      .then((results) => {
        const read = (index: number) => {
          const result = results[index];
          return result.status === 'fulfilled' && Array.isArray(result.value) ? result.value : [];
        };
        setSummary({
          copilot: read(0),
          decisionPolicies: read(1),
          omniFlows: read(2),
          developerPackages: read(3),
          sloPolicies: read(4),
        });
        const rejected = results.filter((result) => result.status === 'rejected').length;
        setError(rejected ? `${rejected} differentiation source${rejected > 1 ? 's' : ''} unavailable.` : null);
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const seed = async () => {
    setSeeding(true);
    try {
      await Promise.all([
        differentiationApi.upsertDecisionPolicy({
          policyKey: 'next-best-offer',
          name: 'Next Best Offer',
          triggerEvent: 'PROFILE_UPDATED',
          channel: 'ANY',
          variants: [
            { key: 'engage-email', channel: 'EMAIL', weight: 40, tag: 'engagement' },
            { key: 'upgrade-web', channel: 'WEB', weight: 60, tag: 'upgrade' },
          ],
          guardrails: { blockedChannels: [] },
        }),
        differentiationApi.upsertOmnichannelFlow({
          flowKey: 'commercial-launch',
          name: 'Commercial Launch',
          channels: ['EMAIL', 'SMS', 'PUSH', 'ADS', 'WEB', 'TRANSACTIONAL'],
          guardrails: { consentRequired: true },
        }),
        differentiationApi.upsertDeveloperPackage({
          appKey: 'phase4-developer-platform',
          displayName: 'Phase 4 Developer Platform',
          scopes: ['campaign:read', 'workflow:*', 'webhook:replay'],
          sdkTargets: ['typescript', 'java', 'cli'],
          marketplaceStatus: 'PRIVATE',
          sandboxEnabled: true,
          webhookReplayEnabled: true,
        }),
        differentiationApi.upsertSloPolicy({
          serviceName: 'delivery-service',
          sloTargetPercent: 99.9,
          errorBudgetMinutes: 43.2,
          syntheticProbe: { p95LatencyMs: 1200 },
          selfHealingActions: [{ action: 'scale_workers' }, { action: 'pause_low_priority_tenants' }],
          capacityForecast: { unit: 'workers' },
        }),
      ]);
      await load();
    } finally {
      setSeeding(false);
    }
  };

  const cards = useMemo(
    () => [
      { label: 'Copilot Reviews', value: summary.copilot.length, tone: 'brand' as const },
      { label: 'Decision Policies', value: summary.decisionPolicies.length, tone: 'info' as const },
      { label: 'Omni Flows', value: summary.omniFlows.length, tone: 'success' as const },
      { label: 'Developer Apps', value: summary.developerPackages.length, tone: 'neutral' as const },
      { label: 'SLO Policies', value: summary.sloPolicies.length, tone: 'warning' as const },
    ],
    [summary]
  );

  return (
    <AdminPanel
      title="Differentiation Platform"
      subtitle="AI approval workflow, real-time decisioning, omnichannel orchestration, developer packages, and SLO automation."
      action={
        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant="secondary" onClick={load} loading={loading} icon={<RefreshCcw className="h-4 w-4" />}>
            Refresh
          </Button>
          <Button size="sm" onClick={seed} loading={seeding} icon={<Sparkles className="h-4 w-4" />}>
            Seed controls
          </Button>
        </div>
      }
    >
      {loading ? (
        <AdminSkeletonRows rows={5} />
      ) : (
        <div className="space-y-5">
          {error ? <div className="rounded-xl border border-warning/30 bg-warning/10 px-4 py-3 text-sm text-warning">{error}</div> : null}
          <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
            {cards.map((card) => (
              <AdminMetricCard key={card.label} label={card.label} value={card.value} tone={card.tone} />
            ))}
          </div>

          <div className="grid gap-4 xl:grid-cols-3">
            <SignalCard
              icon={BrainCircuit}
              title="AI Copilot"
              status={summary.copilot[0]?.status || 'READY'}
              lines={[
                `${summary.copilot.filter((item) => item.approval_required || item.approvalRequired).length} approvals pending`,
                'Policy findings stored with recommendation payload',
              ]}
            />
            <SignalCard
              icon={Target}
              title="Decisioning"
              status={summary.decisionPolicies[0]?.status || 'DRAFT'}
              lines={[
                'Profile update events map to weighted variants',
                'Guardrails block unsafe channel selection',
              ]}
            />
            <SignalCard
              icon={RadioTower}
              title="Omnichannel"
              status={summary.omniFlows[0]?.status || 'DRAFT'}
              lines={[
                'Email, SMS, push, ads, web, transactional route order',
                'Simulation writes route evidence',
              ]}
            />
            <SignalCard
              icon={Code2}
              title="Developer Platform"
              status={summary.developerPackages[0]?.marketplace_status || summary.developerPackages[0]?.marketplaceStatus || 'PRIVATE'}
              lines={[
                'SDK targets, scopes, sandbox flag, marketplace state',
                'Webhook replay jobs support dry-run first',
              ]}
            />
            <SignalCard
              icon={ShieldCheck}
              title="SLO Automation"
              status={summary.sloPolicies[0]?.status || 'ACTIVE'}
              lines={[
                'Error budget burn, latency, queue, saturation checks',
                'Self-healing action evidence created on incident',
              ]}
            />
            <SignalCard
              icon={GitBranch}
              title="Human Gates"
              status="CONTROLLED"
              lines={[
                'High-risk AI recommendations wait for approval',
                'Auto-winner guarded by confidence and sample checks',
              ]}
            />
          </div>
        </div>
      )}
    </AdminPanel>
  );
}

function SignalCard({
  icon: Icon,
  title,
  status,
  lines,
}: {
  icon: typeof BrainCircuit;
  title: string;
  status: string;
  lines: string[];
}) {
  return (
    <div className="rounded-xl border border-border-default bg-surface-secondary p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2">
          <div className="rounded-lg border border-border-default bg-surface-primary p-2 text-brand-600">
            <Icon className="h-4 w-4" />
          </div>
          <p className="text-sm font-semibold text-content-primary">{title}</p>
        </div>
        <StatusPill status={status} />
      </div>
      <div className="mt-4 space-y-2 text-sm text-content-secondary">
        {lines.map((line) => (
          <div key={line} className="rounded-lg border border-border-default bg-surface-primary px-3 py-2">
            {line}
          </div>
        ))}
      </div>
    </div>
  );
}

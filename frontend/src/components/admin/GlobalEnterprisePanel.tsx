'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Bot,
  Database,
  Globe2,
  KeyRound,
  MapPinned,
  PlugZap,
  RefreshCcw,
  RotateCcw,
  Scale,
  ServerCog,
  ShieldCheck,
  Sparkles,
} from 'lucide-react';
import { AdminMetricCard, AdminPanel, AdminSkeletonRows, StatusPill } from '@/components/admin/AdminChrome';
import { Button } from '@/components/ui/Button';
import { globalEnterpriseApi, type GlobalRecord } from '@/lib/global-enterprise-api';

type Summary = {
  operatingModels: GlobalRecord[];
  failoverDrills: GlobalRecord[];
  residencyPolicies: GlobalRecord[];
  encryptionPolicies: GlobalRecord[];
  legalHolds: GlobalRecord[];
  lineage: GlobalRecord[];
  policySimulations: GlobalRecord[];
  evidencePacks: GlobalRecord[];
  marketplaceTemplates: GlobalRecord[];
  marketplaceInstances: GlobalRecord[];
  syncJobs: GlobalRecord[];
  optimizationPolicies: GlobalRecord[];
  recommendations: GlobalRecord[];
  rollbacks: GlobalRecord[];
};

const emptySummary: Summary = {
  operatingModels: [],
  failoverDrills: [],
  residencyPolicies: [],
  encryptionPolicies: [],
  legalHolds: [],
  lineage: [],
  policySimulations: [],
  evidencePacks: [],
  marketplaceTemplates: [],
  marketplaceInstances: [],
  syncJobs: [],
  optimizationPolicies: [],
  recommendations: [],
  rollbacks: [],
};

export function GlobalEnterprisePanel() {
  const [summary, setSummary] = useState<Summary>(emptySummary);
  const [loading, setLoading] = useState(true);
  const [seeding, setSeeding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    Promise.allSettled([
      globalEnterpriseApi.listOperatingModels(),
      globalEnterpriseApi.listFailoverDrills({ limit: 50 }),
      globalEnterpriseApi.listDataResidencyPolicies(),
      globalEnterpriseApi.listEncryptionPolicies(),
      globalEnterpriseApi.listLegalHolds({ limit: 50 }),
      globalEnterpriseApi.listLineage({ limit: 50 }),
      globalEnterpriseApi.listPolicySimulations({ limit: 50 }),
      globalEnterpriseApi.listEvidencePacks({ limit: 50 }),
      globalEnterpriseApi.listMarketplaceTemplates(),
      globalEnterpriseApi.listMarketplaceInstances(),
      globalEnterpriseApi.listMarketplaceSyncJobs({ limit: 50 }),
      globalEnterpriseApi.listOptimizationPolicies(),
      globalEnterpriseApi.listOptimizationRecommendations({ limit: 50 }),
      globalEnterpriseApi.listOptimizationRollbacks({ limit: 50 }),
    ])
      .then((results) => {
        const read = (index: number) => {
          const result = results[index];
          return result.status === 'fulfilled' && Array.isArray(result.value) ? result.value : [];
        };
        setSummary({
          operatingModels: read(0),
          failoverDrills: read(1),
          residencyPolicies: read(2),
          encryptionPolicies: read(3),
          legalHolds: read(4),
          lineage: read(5),
          policySimulations: read(6),
          evidencePacks: read(7),
          marketplaceTemplates: read(8),
          marketplaceInstances: read(9),
          syncJobs: read(10),
          optimizationPolicies: read(11),
          recommendations: read(12),
          rollbacks: read(13),
        });
        const rejected = results.filter((result) => result.status === 'rejected').length;
        setError(rejected ? `${rejected} global operation source${rejected > 1 ? 's' : ''} unavailable.` : null);
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const seed = async () => {
    setSeeding(true);
    try {
      await globalEnterpriseApi.upsertOperatingModel({
        modelKey: 'global-active-warm-default',
        name: 'Global Active Warm Default',
        topologyMode: 'ACTIVE_WARM',
        primaryRegion: 'us-east-1',
        standbyRegions: ['us-west-2'],
        activeRegions: ['us-east-1'],
        rpoTargetMinutes: 15,
        rtoTargetMinutes: 60,
        trafficPolicy: { failClosed: true, promotionRequiresPassingDrill: true },
        promotionState: 'PRIMARY_HEALTHY',
        failoverState: 'LOCKED',
      });
      await globalEnterpriseApi.upsertDataResidencyPolicy({
        policyKey: 'profile-us-controlled-failover',
        dataClass: 'PROFILE',
        homeRegion: 'us-east-1',
        allowedRegions: ['us-east-1', 'us-west-2', 'eu-west-1'],
        blockedRegions: [],
        failoverAllowed: true,
        legalBasis: 'CONTRACT',
        enforcementMode: 'ENFORCE',
      });
      await globalEnterpriseApi.upsertEncryptionPolicy({
        policyKey: 'profile-external-kms',
        dataClass: 'PROFILE',
        keyProvider: 'EXTERNAL_KMS',
        keyRef: 'external-secret://kms/profile-default',
        algorithm: 'AES-256-GCM',
        rotationDays: 90,
      });
      await globalEnterpriseApi.runPolicySimulation({
        simulationKey: `global-seed-${Date.now()}`,
        policyType: 'GLOBAL_FAILOVER',
        artifactType: 'OPERATING_MODEL',
        artifactId: 'global-active-warm-default',
        inputContext: {
          legalHoldActive: false,
          residencyViolation: false,
          complianceViolation: false,
          brandViolation: false,
        },
      });
      await globalEnterpriseApi.createEvidencePack({
        packKey: `phase5-seed-${Date.now()}`,
        name: 'Phase 5 seed evidence',
        scope: { topology: 'ACTIVE_WARM', dataClass: 'PROFILE' },
        evidenceRefs: ['global-active-warm-default', 'profile-us-controlled-failover', 'profile-external-kms'],
      });
      await globalEnterpriseApi.seedMarketplaceTemplates();
      const instance = await globalEnterpriseApi.upsertMarketplaceInstance({
        instanceKey: 'salesforce-crm-dry-run',
        connectorKey: 'salesforce-crm',
        displayName: 'Salesforce CRM dry-run',
        category: 'CRM',
        authMode: 'OAUTH2',
        credentialRef: 'external-secret://connectors/salesforce-crm',
        status: 'DRY_RUN',
        config: { mode: 'dry-run', objects: ['Contact', 'Campaign'] },
      });
      if (instance?.id) {
        await globalEnterpriseApi.createMarketplaceSyncJob({
          connectorInstanceId: instance.id,
          syncType: 'CONTACT_IMPORT',
          direction: 'INBOUND',
          dryRun: true,
          request: { limit: 1000 },
        });
      }
      await globalEnterpriseApi.upsertOptimizationPolicy({
        policyKey: 'global-send-safety',
        name: 'Global Send Safety',
        mode: 'SUGGEST_ONLY',
        targetScope: { artifacts: ['campaign', 'journey', 'audience'] },
        constraints: { brandApprovalRequired: true, residencyRequired: true },
        guardrails: { autoApplyBlockedForAudienceChanges: true },
        rollbackPolicy: { snapshotRequired: true },
      });
      await load();
    } finally {
      setSeeding(false);
    }
  };

  const model = useMemo(() => summary.operatingModels[0] || {}, [summary.operatingModels]);
  const latestDrill = summary.failoverDrills[0] || {};
  const latestSimulation = summary.policySimulations[0] || {};
  const latestRecommendation = summary.recommendations[0] || {};
  const regionCount = useMemo(() => {
    const regions = new Set<string>();
    [model.primary_region, model.primaryRegion].filter(Boolean).forEach((region) => regions.add(String(region)));
    [...toList(model.standby_regions || model.standbyRegions), ...toList(model.active_regions || model.activeRegions)]
      .forEach((region) => regions.add(region));
    return regions.size;
  }, [model]);

  const metrics = [
    { label: 'Regions', value: regionCount, tone: 'info' as const },
    { label: 'Residency Rules', value: summary.residencyPolicies.length, tone: 'success' as const },
    { label: 'Evidence Packs', value: summary.evidencePacks.length, tone: 'brand' as const },
    { label: 'Connectors', value: summary.marketplaceTemplates.length, tone: 'neutral' as const },
    { label: 'Optimization Gates', value: summary.optimizationPolicies.length, tone: 'warning' as const },
  ];

  return (
    <AdminPanel
      title="Global Ops"
      subtitle="Region topology, data residency, encryption policy, governance evidence, marketplace connectors, and autonomous optimization approvals."
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
            {metrics.map((card) => (
              <AdminMetricCard key={card.label} label={card.label} value={card.value} tone={card.tone} />
            ))}
          </div>

          <div className="grid gap-4 xl:grid-cols-3">
            <SignalCard
              icon={Globe2}
              title="Topology Control"
              status={asText(model.topology_mode || model.topologyMode, 'ACTIVE_WARM')}
              lines={[
                `Primary ${asText(model.primary_region || model.primaryRegion, 'not set')}`,
                `RPO/RTO ${asText(model.rpo_target_minutes || model.rpoTargetMinutes, '15')}m/${asText(model.rto_target_minutes || model.rtoTargetMinutes, '60')}m`,
                `Failover ${asText(model.failover_state || model.failoverState, 'LOCKED')}`,
              ]}
            />
            <SignalCard
              icon={MapPinned}
              title="Residency Policy"
              status={summary.residencyPolicies[0]?.enforcement_mode || summary.residencyPolicies[0]?.enforcementMode || 'ENFORCE'}
              lines={[
                `${summary.residencyPolicies.length} data class policies`,
                `${summary.residencyPolicies.filter((item) => item.failover_allowed || item.failoverAllowed).length} allow failover`,
                'Missing policy blocks regional movement',
              ]}
            />
            <SignalCard
              icon={KeyRound}
              title="Encryption Policy"
              status={summary.encryptionPolicies[0]?.status || 'PENDING'}
              lines={[
                `${summary.encryptionPolicies.length} key policies`,
                `${summary.encryptionPolicies.filter((item) => asText(item.key_provider || item.keyProvider).includes('EXTERNAL')).length} external key refs`,
                'Rotation and residency bindings tracked',
              ]}
            />
            <SignalCard
              icon={Scale}
              title="Governance Evidence"
              status={latestSimulation.verdict || 'READY'}
              lines={[
                `${summary.legalHolds.filter((item) => asText(item.status) === 'ACTIVE').length} active legal holds`,
                `${summary.lineage.length} lineage edges loaded`,
                `${summary.evidencePacks.length} evidence packs`,
              ]}
            />
            <SignalCard
              icon={PlugZap}
              title="Marketplace"
              status={summary.marketplaceInstances[0]?.status || 'DRY_RUN'}
              lines={[
                `${summary.marketplaceTemplates.length} seeded connector templates`,
                `${summary.marketplaceInstances.length} tenant connector instances`,
                `${summary.syncJobs.filter((item) => item.dry_run || item.dryRun).length} dry-run sync jobs`,
              ]}
            />
            <SignalCard
              icon={Bot}
              title="Autonomous Optimization"
              status={latestRecommendation.status || 'SUGGEST_ONLY'}
              lines={[
                `${summary.optimizationPolicies.length} policies`,
                `${summary.recommendations.length} recommendations`,
                `${summary.rollbacks.length} rollback records`,
              ]}
            />
          </div>

          <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
            <section className="rounded-xl border border-border-default bg-surface-secondary p-4">
              <div className="flex items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <ServerCog className="h-4 w-4 text-brand-500" />
                  <p className="text-sm font-semibold text-content-primary">Failover Drill Ledger</p>
                </div>
                <StatusPill status={latestDrill.verdict || 'NO_DRILL'} />
              </div>
              <div className="mt-4 grid gap-2 md:grid-cols-2">
                {summary.failoverDrills.slice(0, 6).map((drill, index) => (
                  <LedgerRow
                    key={`${asText(drill.id, 'drill')}-${index}`}
                    title={`${asText(drill.source_region || drill.sourceRegion, 'source')} -> ${asText(drill.target_region || drill.targetRegion, 'target')}`}
                    detail={`RPO/RTO ${asText(drill.actual_rpo_minutes || drill.actualRpoMinutes, '0')}m/${asText(drill.actual_rto_minutes || drill.actualRtoMinutes, '0')}m`}
                    status={asText(drill.verdict, 'PENDING')}
                  />
                ))}
                {!summary.failoverDrills.length ? (
                  <p className="rounded-lg border border-dashed border-border-default bg-surface-primary px-4 py-6 text-center text-sm text-content-secondary">
                    No drill evidence recorded yet.
                  </p>
                ) : null}
              </div>
            </section>
            <section className="rounded-xl border border-border-default bg-surface-secondary p-4">
              <div className="flex items-center gap-2">
                <RotateCcw className="h-4 w-4 text-brand-500" />
                <p className="text-sm font-semibold text-content-primary">Rollback Ready</p>
              </div>
              <div className="mt-4 space-y-2">
                <PolicyLine label="Default topology" value="ACTIVE_WARM" />
                <PolicyLine label="Auto apply" value="guardrail gated" />
                <PolicyLine label="Connector traffic" value="dry-run first" />
                <PolicyLine label="Residency missing" value="fail closed" />
              </div>
            </section>
          </div>

          <div className="rounded-xl border border-border-default bg-surface-secondary p-4">
            <div className="flex items-center gap-2">
              <ShieldCheck className="h-4 w-4 text-brand-500" />
              <p className="text-sm font-semibold text-content-primary">Future Admin Configuration Surface</p>
            </div>
            <div className="mt-3 grid gap-2 text-sm text-content-secondary md:grid-cols-3">
              <InlineFact icon={Database} label="Policy objects" value="Stored as tenant-scoped primitives" />
              <InlineFact icon={Globe2} label="Region model" value="Cloud and K8s neutral" />
              <InlineFact icon={Bot} label="Optimization" value="Human approval and rollback snapshots" />
            </div>
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
  icon: typeof Globe2;
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

function LedgerRow({ title, detail, status }: { title: string; detail: string; status: string }) {
  return (
    <div className="rounded-lg border border-border-default bg-surface-primary p-3">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-content-primary">{title}</p>
          <p className="mt-1 text-xs text-content-secondary">{detail}</p>
        </div>
        <StatusPill status={status} />
      </div>
    </div>
  );
}

function PolicyLine({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-lg border border-border-default bg-surface-primary px-3 py-2">
      <span className="text-xs font-semibold uppercase tracking-[0.12em] text-content-muted">{label}</span>
      <span className="text-right text-xs font-medium text-content-primary">{value}</span>
    </div>
  );
}

function InlineFact({ icon: Icon, label, value }: { icon: typeof Globe2; label: string; value: string }) {
  return (
    <div className="rounded-lg border border-border-default bg-surface-primary p-3">
      <div className="flex items-center gap-2">
        <Icon className="h-4 w-4 text-brand-500" />
        <span className="text-xs font-semibold uppercase tracking-[0.12em] text-content-muted">{label}</span>
      </div>
      <p className="mt-2 text-sm font-medium text-content-primary">{value}</p>
    </div>
  );
}

function toList(value: unknown) {
  if (Array.isArray(value)) {
    return value.map(String).filter(Boolean);
  }
  if (typeof value === 'string' && value.trim()) {
    try {
      const parsed = JSON.parse(value);
      return Array.isArray(parsed) ? parsed.map(String).filter(Boolean) : [value];
    } catch {
      return value.split(',').map((item) => item.trim()).filter(Boolean);
    }
  }
  return [];
}

function asText(value: unknown, fallback = '') {
  if (value === null || value === undefined || value === '') {
    return fallback;
  }
  return String(value);
}

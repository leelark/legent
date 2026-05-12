'use client';

import { useCallback, useEffect, useState } from 'react';
import { RefreshCcw, ShieldCheck, Sparkles } from 'lucide-react';
import { AdminPanel, AdminSkeletonRows } from '@/components/admin/AdminChrome';
import { ExtensionGovernanceCard } from '@/components/admin/performance/ExtensionGovernanceCard';
import { EvidenceLedger } from '@/components/admin/performance/EvidenceLedger';
import { OperationsAssistanceCard } from '@/components/admin/performance/OperationsAssistanceCard';
import { OptimizationEvidenceCard } from '@/components/admin/performance/OptimizationEvidenceCard';
import { PerformanceMetricStrip } from '@/components/admin/performance/PerformanceMetricStrip';
import { PerformanceSignalCard } from '@/components/admin/performance/PerformanceSignalCard';
import { PersonalizationEvidenceCard } from '@/components/admin/performance/PersonalizationEvidenceCard';
import { WorkflowBenchmarkCard } from '@/components/admin/performance/WorkflowBenchmarkCard';
import {
  emptyPerformanceSummary,
  normalizeSummary,
  readList,
} from '@/components/admin/performance/utils';
import { Button } from '@/components/ui/Button';
import {
  performanceIntelligenceApi,
  type PerformanceSummary,
} from '@/lib/performance-intelligence-api';

export function PerformanceIntelligencePanel() {
  const [summary, setSummary] = useState<PerformanceSummary>(emptyPerformanceSummary);
  const [loading, setLoading] = useState(true);
  const [runningDemo, setRunningDemo] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    Promise.allSettled([
      performanceIntelligenceApi.summary(),
      performanceIntelligenceApi.listOptimizationPolicies(),
      performanceIntelligenceApi.listExtensionPackages(),
      performanceIntelligenceApi.listOperationsReviews(),
      performanceIntelligenceApi.listWorkflowBenchmarks(),
    ])
      .then((results) => {
        const base = results[0].status === 'fulfilled' ? normalizeSummary(results[0].value) : emptyPerformanceSummary;
        setSummary({
          ...base,
          optimizationPolicies: readList(results[1], base.optimizationPolicies),
          extensionPackages: readList(results[2], base.extensionPackages),
          operationsReviews: readList(results[3], base.operationsReviews),
          workflowBenchmarks: readList(results[4], base.workflowBenchmarks),
        });
        const rejected = results.filter((result) => result.status === 'rejected').length;
        setError(rejected ? 'Some performance intelligence sources are unavailable.' : null);
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const runDemoEvaluation = async () => {
    setRunningDemo(true);
    try {
      await performanceIntelligenceApi.evaluatePersonalization({
        evaluationKey: 'performance-next-best-action',
        region: 'global-control-plane',
        subjectId: 'subscriber-demo',
        eventType: 'PROFILE_UPDATED',
        profile: { interest: 'upgrade', consent: 'OPT_IN', lifecycleStage: 'ENGAGED' },
        event: { channel: 'EMAIL', frequencyCount: 1 },
        segmentRules: [
          {
            key: 'engaged-upgrade',
            name: 'Engaged upgrade intent',
            rules: { conditions: [{ field: 'interest', op: 'EQUALS', value: 'upgrade' }] },
          },
        ],
        variants: [
          { key: 'upgrade-offer', channel: 'EMAIL', weight: 60, tag: 'upgrade', segmentKey: 'engaged-upgrade', payload: { offer: 'annual-plan' } },
        ],
        guardrails: { consentRequired: true, maxFrequency: 3 },
        personalizationDefaults: { surface: 'campaign-builder' },
      });
      await performanceIntelligenceApi.upsertOptimizationPolicy({
        policyKey: 'performance-revenue-consent-policy',
        name: 'Revenue and Consent Policy',
        optimizationType: 'REVENUE',
        objective: 'Optimize revenue while consent guardrails win.',
        targetMetric: 'revenue_per_consenting_recipient',
        guardrails: { minConsentCoveragePercent: 95, maxOptOutRatePercent: 2 },
        rollbackPolicy: { snapshotRequired: true },
      });
      await performanceIntelligenceApi.evaluateOptimization({
        policyKey: 'performance-revenue-consent-policy',
        artifactType: 'CAMPAIGN',
        artifactId: 'campaign-demo',
        signals: { consentCoveragePercent: 98, revenueAtRisk: 18000, conversionRateDelta: 0.03, changesContent: true },
      });
      const pkg = await performanceIntelligenceApi.upsertExtensionPackage({
        packageKey: 'performance-safe-extension',
        displayName: 'Performance Safe Extension',
        packageType: 'HYBRID',
        scopes: ['campaign:read', 'tracking:read'],
        manifest: { name: 'performance-safe-extension', version: '1.0.0', entrypoint: 'index.js' },
        scripts: [{ name: 'score', source: 'return context.score;' }],
        testRequirements: ['unit', 'policy'],
        approvalStatus: 'APPROVED',
      });
      if (pkg?.id) {
        await performanceIntelligenceApi.validateExtensionPackage(pkg.id, { evidence: { source: 'admin-demo' } });
      }
      await performanceIntelligenceApi.assistOperations({
        operationType: 'INCIDENT',
        artifactType: 'SERVICE',
        artifactId: 'delivery-service',
        telemetry: { successRatePercent: 98.8, saturationPercent: 88, p95LatencyMs: 2100, errors: 12 },
        evidenceRefs: ['prometheus:error-budget', 'otel:delivery-trace'],
      });
      await performanceIntelligenceApi.recordWorkflowBenchmark({
        benchmarkKey: 'campaign-create-launch',
        flowName: 'Campaign create and launch',
        campaignCreationSeconds: 240,
        launchErrors: 1,
        observabilityScore: 92,
        competitorCreationSeconds: 420,
        competitorLaunchErrors: 4,
        competitorObservabilityScore: 74,
        evidence: { source: 'admin-demo' },
      });
      await load();
    } finally {
      setRunningDemo(false);
    }
  };

  const latestPersonalization = summary.personalizationEvaluations[0] || {};
  const latestOptimization = summary.optimizationRuns[0] || {};
  const latestValidation = summary.extensionValidationRuns[0] || {};
  const latestOps = summary.operationsReviews[0] || {};
  const latestBenchmark = summary.workflowBenchmarks[0] || {};

  return (
    <AdminPanel
      title="Performance Intelligence"
      subtitle="Realtime personalization, closed-loop optimization, governed extensibility, operations assistance, and Salesforce workflow benchmarks."
      action={(
        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant="secondary" onClick={load} loading={loading} icon={<RefreshCcw className="h-4 w-4" />}>
            Refresh
          </Button>
          <Button size="sm" onClick={runDemoEvaluation} loading={runningDemo} icon={<Sparkles className="h-4 w-4" />}>
            Run demo evaluation
          </Button>
        </div>
      )}
    >
      {loading ? (
        <AdminSkeletonRows rows={5} />
      ) : (
        <div className="space-y-5">
          {error ? <div className="rounded-xl border border-warning/30 bg-warning/10 px-4 py-3 text-sm text-warning">{error}</div> : null}
          <PerformanceMetricStrip summary={summary} />

          <div className="grid gap-4 xl:grid-cols-3">
            <PersonalizationEvidenceCard latest={latestPersonalization} />
            <OptimizationEvidenceCard latest={latestOptimization} summary={summary} />
            <ExtensionGovernanceCard latest={latestValidation} summary={summary} />
            <OperationsAssistanceCard latest={latestOps} summary={summary} />
            <WorkflowBenchmarkCard latest={latestBenchmark} />
            <PerformanceSignalCard
              icon={ShieldCheck}
              title="Governed Evidence"
              status="CONTROLLED"
              lines={[
                'Every evaluation writes tenant/workspace evidence',
                'Approval and rollback flags included in optimization runs',
                'Benchmarks compare against Salesforce MCE baseline',
              ]}
            />
          </div>

          <EvidenceLedger
            personalization={latestPersonalization}
            optimization={latestOptimization}
            extensionValidation={latestValidation}
            benchmark={latestBenchmark}
          />
        </div>
      )}
    </AdminPanel>
  );
}

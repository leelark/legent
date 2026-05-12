import { AdminMetricCard } from '@/components/admin/AdminChrome';
import type { PerformanceSummary } from '@/lib/performance-intelligence-api';
import { asText, boolValue } from '@/components/admin/performance/utils';

export function PerformanceMetricStrip({ summary }: { summary: PerformanceSummary }) {
  const metrics = [
    {
      label: 'Personalization SLO',
      value: `${summary.personalizationEvaluations.filter((item) => boolValue(item.slo_pass ?? item.sloPass)).length}/${summary.personalizationEvaluations.length}`,
      tone: 'success' as const,
    },
    { label: 'Optimization Policies', value: summary.optimizationPolicies.length, tone: 'brand' as const },
    { label: 'Extensions', value: summary.extensionPackages.length, tone: 'info' as const },
    { label: 'Ops Reviews', value: summary.operationsReviews.length, tone: 'warning' as const },
    {
      label: 'Workflow Leaders',
      value: summary.workflowBenchmarks.filter((item) => asText(item.verdict) === 'LEADER').length,
      tone: 'neutral' as const,
    },
  ];

  return (
    <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
      {metrics.map((metric) => (
        <AdminMetricCard key={metric.label} label={metric.label} value={metric.value} tone={metric.tone} />
      ))}
    </div>
  );
}

import { Target } from 'lucide-react';
import type { PerformanceRecord, PerformanceSummary } from '@/lib/performance-intelligence-api';
import { PerformanceSignalCard } from '@/components/admin/performance/PerformanceSignalCard';
import { asText, toList } from '@/components/admin/performance/utils';

export function OptimizationEvidenceCard({
  latest,
  summary,
}: {
  latest: PerformanceRecord;
  summary: PerformanceSummary;
}) {
  return (
    <PerformanceSignalCard
      icon={Target}
      title="Closed-loop Optimization"
      status={asText(latest.risk_band ?? latest.riskBand, 'READY')}
      lines={[
        `${summary.optimizationPolicies.length} policies across deliverability, engagement, revenue, consent`,
        `${toList(latest.blocked_reasons ?? latest.blockedReasons).length} latest guardrail blocks`,
        'Consent and compliance override commercial optimization',
      ]}
    />
  );
}

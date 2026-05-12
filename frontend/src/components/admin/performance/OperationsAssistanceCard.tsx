import { Bot } from 'lucide-react';
import type { PerformanceRecord, PerformanceSummary } from '@/lib/performance-intelligence-api';
import { PerformanceSignalCard } from '@/components/admin/performance/PerformanceSignalCard';
import { asText, toList } from '@/components/admin/performance/utils';

export function OperationsAssistanceCard({
  latest,
  summary,
}: {
  latest: PerformanceRecord;
  summary: PerformanceSummary;
}) {
  return (
    <PerformanceSignalCard
      icon={Bot}
      title="Operations Assistance"
      status={asText(latest.severity, 'P3')}
      lines={[
        `${summary.operationsReviews.length} deterministic operation reviews`,
        `${toList(latest.recommended_actions ?? latest.recommendedActions).length} latest actions`,
        'Build, QA, launch, monitor, incident modes covered',
      ]}
    />
  );
}

import { Trophy } from 'lucide-react';
import type { PerformanceRecord } from '@/lib/performance-intelligence-api';
import { PerformanceSignalCard } from '@/components/admin/performance/PerformanceSignalCard';
import { asText, deltaValue } from '@/components/admin/performance/utils';

export function WorkflowBenchmarkCard({ latest }: { latest: PerformanceRecord }) {
  return (
    <PerformanceSignalCard
      icon={Trophy}
      title="Workflow Benchmarks"
      status={asText(latest.verdict, 'WATCH')}
      lines={[
        `Creation delta ${deltaValue(latest, 'campaignCreationSeconds')}s`,
        `Launch error delta ${deltaValue(latest, 'launchErrors')}`,
        `Observability delta ${deltaValue(latest, 'observabilityScore')}`,
      ]}
    />
  );
}

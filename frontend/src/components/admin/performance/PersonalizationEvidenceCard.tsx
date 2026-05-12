import { Zap } from 'lucide-react';
import type { PerformanceRecord } from '@/lib/performance-intelligence-api';
import { PerformanceSignalCard } from '@/components/admin/performance/PerformanceSignalCard';
import { asText, boolValue, toList } from '@/components/admin/performance/utils';

export function PersonalizationEvidenceCard({ latest }: { latest: PerformanceRecord }) {
  return (
    <PerformanceSignalCard
      icon={Zap}
      title="Realtime Personalization"
      status={boolValue(latest.slo_pass ?? latest.sloPass) ? 'SLO_PASS' : 'WATCH'}
      lines={[
        `${asText(latest.latency_ms ?? latest.latencyMs, '0')}ms latest evaluation`,
        `${toList(latest.segment_hits ?? latest.segmentHits).length} segment hits stored`,
        'Sub-second target enforced at response and ledger level',
      ]}
    />
  );
}

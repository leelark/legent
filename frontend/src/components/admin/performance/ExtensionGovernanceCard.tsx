import { Code2 } from 'lucide-react';
import type { PerformanceRecord, PerformanceSummary } from '@/lib/performance-intelligence-api';
import { PerformanceSignalCard } from '@/components/admin/performance/PerformanceSignalCard';
import { asText, toList } from '@/components/admin/performance/utils';

export function ExtensionGovernanceCard({
  latest,
  summary,
}: {
  latest: PerformanceRecord;
  summary: PerformanceSummary;
}) {
  return (
    <PerformanceSignalCard
      icon={Code2}
      title="Extension Governance"
      status={asText(latest.status, summary.extensionPackages.length ? 'VALIDATION_READY' : 'DRAFT')}
      lines={[
        `${summary.extensionPackages.length} governed packages`,
        `${toList(latest.forbidden_tokens ?? latest.forbiddenTokens).length} forbidden tokens in latest scan`,
        'Static package validation only; user scripts are never executed',
      ]}
    />
  );
}

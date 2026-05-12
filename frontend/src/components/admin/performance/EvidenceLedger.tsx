import { Activity } from 'lucide-react';
import { StatusPill } from '@/components/admin/AdminChrome';
import type { PerformanceRecord } from '@/lib/performance-intelligence-api';
import { asText, boolValue } from '@/components/admin/performance/utils';

export function EvidenceLedger({
  personalization,
  optimization,
  extensionValidation,
  benchmark,
}: {
  personalization: PerformanceRecord;
  optimization: PerformanceRecord;
  extensionValidation: PerformanceRecord;
  benchmark: PerformanceRecord;
}) {
  return (
    <section className="rounded-xl border border-border-default bg-surface-secondary p-4">
      <div className="flex items-center gap-2">
        <Activity className="h-4 w-4 text-brand-500" />
        <p className="text-sm font-semibold text-content-primary">Latest Evidence Ledger</p>
      </div>
      <div className="mt-4 grid gap-2 md:grid-cols-2 xl:grid-cols-4">
        <LedgerRow label="Personalization" value={asText(personalization.evaluation_key ?? personalization.evaluationKey, 'no evaluation')} status={boolValue(personalization.slo_pass ?? personalization.sloPass) ? 'PASS' : 'WATCH'} />
        <LedgerRow label="Optimization" value={asText(optimization.optimization_type ?? optimization.optimizationType, 'no run')} status={asText(optimization.risk_band ?? optimization.riskBand, 'READY')} />
        <LedgerRow label="Extension" value={asText(extensionValidation.package_id ?? extensionValidation.packageId, 'no package')} status={asText(extensionValidation.status, 'PENDING')} />
        <LedgerRow label="Benchmark" value={asText(benchmark.flow_name ?? benchmark.flowName, 'no benchmark')} status={asText(benchmark.verdict, 'WATCH')} />
      </div>
    </section>
  );
}

function LedgerRow({ label, value, status }: { label: string; value: string; status: string }) {
  return (
    <div className="rounded-lg border border-border-default bg-surface-primary p-3">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-xs font-semibold uppercase tracking-[0.12em] text-content-muted">{label}</p>
          <p className="mt-1 truncate text-sm font-medium text-content-primary">{value}</p>
        </div>
        <StatusPill status={status} />
      </div>
    </div>
  );
}

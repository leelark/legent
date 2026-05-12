import type { PerformanceRecord, PerformanceSummary } from '@/lib/performance-intelligence-api';

export const emptyPerformanceSummary: PerformanceSummary = {
  personalizationEvaluations: [],
  optimizationPolicies: [],
  optimizationRuns: [],
  extensionPackages: [],
  extensionValidationRuns: [],
  operationsReviews: [],
  workflowBenchmarks: [],
};

export function normalizeSummary(value?: Partial<PerformanceSummary>): PerformanceSummary {
  return {
    personalizationEvaluations: Array.isArray(value?.personalizationEvaluations) ? value.personalizationEvaluations : [],
    optimizationPolicies: Array.isArray(value?.optimizationPolicies) ? value.optimizationPolicies : [],
    optimizationRuns: Array.isArray(value?.optimizationRuns) ? value.optimizationRuns : [],
    extensionPackages: Array.isArray(value?.extensionPackages) ? value.extensionPackages : [],
    extensionValidationRuns: Array.isArray(value?.extensionValidationRuns) ? value.extensionValidationRuns : [],
    operationsReviews: Array.isArray(value?.operationsReviews) ? value.operationsReviews : [],
    workflowBenchmarks: Array.isArray(value?.workflowBenchmarks) ? value.workflowBenchmarks : [],
    generatedAt: value?.generatedAt,
  };
}

export function readList(result: PromiseSettledResult<PerformanceRecord[]>, fallback: PerformanceRecord[]) {
  return result.status === 'fulfilled' && Array.isArray(result.value) ? result.value : fallback;
}

export function toList(value: unknown): unknown[] {
  if (Array.isArray(value)) return value;
  if (typeof value === 'string' && value.trim()) {
    try {
      const parsed = JSON.parse(value);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }
  return [];
}

export function deltaValue(row: PerformanceRecord, key: string) {
  const deltas = row?.deltas;
  if (deltas && typeof deltas === 'object' && !Array.isArray(deltas)) {
    return asText(deltas[key], '0');
  }
  if (typeof deltas === 'string') {
    try {
      const parsed = JSON.parse(deltas);
      return asText(parsed[key], '0');
    } catch {
      return '0';
    }
  }
  return '0';
}

export function boolValue(value: unknown) {
  return value === true || String(value).toLowerCase() === 'true';
}

export function asText(value: unknown, fallback = '') {
  if (value === null || value === undefined || value === '') {
    return fallback;
  }
  return String(value);
}

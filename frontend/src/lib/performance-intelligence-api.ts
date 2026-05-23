import { get, post } from '@/lib/api-client';

export type PerformanceRecord = Record<string, unknown> & {
  deltas?: Record<string, unknown> | string;
  id?: string;
};

export type PerformanceSummary = {
  personalizationEvaluations: PerformanceRecord[];
  optimizationPolicies: PerformanceRecord[];
  optimizationRuns: PerformanceRecord[];
  extensionPackages: PerformanceRecord[];
  extensionValidationRuns: PerformanceRecord[];
  operationsReviews: PerformanceRecord[];
  workflowBenchmarks: PerformanceRecord[];
  aiContentAssistancePolicies?: PerformanceRecord[];
  aiContentAssistanceAudits?: PerformanceRecord[];
  generatedAt?: string;
};

const query = (params?: Record<string, string | number | undefined>) => {
  const search = new URLSearchParams();
  Object.entries(params ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== '') {
      search.set(key, String(value));
    }
  });
  const serialized = search.toString();
  return serialized ? `?${serialized}` : '';
};

export const performanceIntelligenceApi = {
  summary: () => get<PerformanceSummary>('/performance-intelligence/summary'),
  evaluatePersonalization: (payload: PerformanceRecord) => post<PerformanceRecord>('/performance-intelligence/personalization/evaluate', payload),
  upsertOptimizationPolicy: (payload: PerformanceRecord) => post<PerformanceRecord>('/performance-intelligence/optimization/policies', payload),
  listOptimizationPolicies: () => get<PerformanceRecord[]>('/performance-intelligence/optimization/policies'),
  evaluateOptimization: (payload: PerformanceRecord) => post<PerformanceRecord>('/performance-intelligence/optimization/evaluate', payload),
  upsertAiContentPolicy: (payload: PerformanceRecord) => post<PerformanceRecord>('/performance-intelligence/ai-content/policies', payload),
  listAiContentPolicies: (workspaceId?: string) =>
    get<PerformanceRecord[]>(`/performance-intelligence/ai-content/policies${query({ workspaceId })}`),
  evaluateAiContentAssistance: (payload: PerformanceRecord) =>
    post<PerformanceRecord>('/performance-intelligence/ai-content/evaluate', payload),
  listAiContentAudits: (params?: { workspaceId?: string; limit?: number }) =>
    get<PerformanceRecord[]>(`/performance-intelligence/ai-content/audits${query(params)}`),
  upsertExtensionPackage: (payload: PerformanceRecord) => post<PerformanceRecord>('/performance-intelligence/extensions/packages', payload),
  listExtensionPackages: () => get<PerformanceRecord[]>('/performance-intelligence/extensions/packages'),
  validateExtensionPackage: (id: string, payload: PerformanceRecord = {}) => post<PerformanceRecord>(`/performance-intelligence/extensions/packages/${id}/validate`, payload),
  assistOperations: (payload: PerformanceRecord) => post<PerformanceRecord>('/performance-intelligence/operations/assist', payload),
  listOperationsReviews: () => get<PerformanceRecord[]>('/performance-intelligence/operations/reviews'),
  recordWorkflowBenchmark: (payload: PerformanceRecord) => post<PerformanceRecord>('/performance-intelligence/workflow-benchmarks', payload),
  listWorkflowBenchmarks: () => get<PerformanceRecord[]>('/performance-intelligence/workflow-benchmarks'),
};

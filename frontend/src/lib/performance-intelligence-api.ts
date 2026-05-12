import { get, post } from '@/lib/api-client';

export type PerformanceRecord = Record<string, any>;

export type PerformanceSummary = {
  personalizationEvaluations: PerformanceRecord[];
  optimizationPolicies: PerformanceRecord[];
  optimizationRuns: PerformanceRecord[];
  extensionPackages: PerformanceRecord[];
  extensionValidationRuns: PerformanceRecord[];
  operationsReviews: PerformanceRecord[];
  workflowBenchmarks: PerformanceRecord[];
  generatedAt?: string;
};

export const performanceIntelligenceApi = {
  summary: () => get<PerformanceSummary>('/performance-intelligence/summary'),
  evaluatePersonalization: (payload: PerformanceRecord) => post<PerformanceRecord>('/performance-intelligence/personalization/evaluate', payload),
  upsertOptimizationPolicy: (payload: PerformanceRecord) => post<PerformanceRecord>('/performance-intelligence/optimization/policies', payload),
  listOptimizationPolicies: () => get<PerformanceRecord[]>('/performance-intelligence/optimization/policies'),
  evaluateOptimization: (payload: PerformanceRecord) => post<PerformanceRecord>('/performance-intelligence/optimization/evaluate', payload),
  upsertExtensionPackage: (payload: PerformanceRecord) => post<PerformanceRecord>('/performance-intelligence/extensions/packages', payload),
  listExtensionPackages: () => get<PerformanceRecord[]>('/performance-intelligence/extensions/packages'),
  validateExtensionPackage: (id: string, payload: PerformanceRecord = {}) => post<PerformanceRecord>(`/performance-intelligence/extensions/packages/${id}/validate`, payload),
  assistOperations: (payload: PerformanceRecord) => post<PerformanceRecord>('/performance-intelligence/operations/assist', payload),
  listOperationsReviews: () => get<PerformanceRecord[]>('/performance-intelligence/operations/reviews'),
  recordWorkflowBenchmark: (payload: PerformanceRecord) => post<PerformanceRecord>('/performance-intelligence/workflow-benchmarks', payload),
  listWorkflowBenchmarks: () => get<PerformanceRecord[]>('/performance-intelligence/workflow-benchmarks'),
};

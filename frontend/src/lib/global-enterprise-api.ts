import { get, post } from '@/lib/api-client';

export type GlobalRecord = Record<string, any>;

type ListOptions = {
  workspaceId?: string;
  status?: string;
  resourceType?: string;
  resourceId?: string;
  limit?: number;
};

function params(options?: ListOptions) {
  const query: Record<string, string | number> = {};
  Object.entries(options || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query[key] = value as string | number;
    }
  });
  return Object.keys(query).length ? { params: query } : undefined;
}

export const globalEnterpriseApi = {
  listOperatingModels: (options?: ListOptions) => get<GlobalRecord[]>('/global/operating-models', params(options)),
  upsertOperatingModel: (payload: GlobalRecord) => post<GlobalRecord>('/global/operating-models', payload),
  listFailoverDrills: (options?: ListOptions) => get<GlobalRecord[]>('/global/failover-drills', params(options)),
  createFailoverDrill: (payload: GlobalRecord) => post<GlobalRecord>('/global/failover-drills', payload),
  evaluateFailover: (payload: GlobalRecord) => post<GlobalRecord>('/global/failover/evaluate', payload),

  listDataResidencyPolicies: (options?: ListOptions) => get<GlobalRecord[]>('/global/data-residency-policies', params(options)),
  upsertDataResidencyPolicy: (payload: GlobalRecord) => post<GlobalRecord>('/global/data-residency-policies', payload),
  listEncryptionPolicies: (options?: ListOptions) => get<GlobalRecord[]>('/global/encryption-policies', params(options)),
  upsertEncryptionPolicy: (payload: GlobalRecord) => post<GlobalRecord>('/global/encryption-policies', payload),

  listLegalHolds: (options?: ListOptions) => get<GlobalRecord[]>('/global/legal-holds', params(options)),
  createLegalHold: (payload: GlobalRecord) => post<GlobalRecord>('/global/legal-holds', payload),
  releaseLegalHold: (id: string, payload: GlobalRecord) => post<GlobalRecord>(`/global/legal-holds/${id}/release`, payload),
  listLineage: (options?: ListOptions) => get<GlobalRecord[]>('/global/lineage', params(options)),
  recordLineageEdge: (payload: GlobalRecord) => post<GlobalRecord>('/global/lineage', payload),
  listPolicySimulations: (options?: ListOptions) => get<GlobalRecord[]>('/global/policy-simulations', params(options)),
  runPolicySimulation: (payload: GlobalRecord) => post<GlobalRecord>('/global/policy-simulations', payload),
  listEvidencePacks: (options?: ListOptions) => get<GlobalRecord[]>('/global/evidence-packs', params(options)),
  createEvidencePack: (payload: GlobalRecord) => post<GlobalRecord>('/global/evidence-packs', payload),

  listMarketplaceTemplates: () => get<GlobalRecord[]>('/global/marketplace/templates'),
  upsertMarketplaceTemplate: (payload: GlobalRecord) => post<GlobalRecord>('/global/marketplace/templates', payload),
  seedMarketplaceTemplates: () => post<GlobalRecord[]>('/global/marketplace/templates/seed'),
  listMarketplaceInstances: (options?: ListOptions) => get<GlobalRecord[]>('/global/marketplace/instances', params(options)),
  upsertMarketplaceInstance: (payload: GlobalRecord) => post<GlobalRecord>('/global/marketplace/instances', payload),
  listMarketplaceSyncJobs: (options?: ListOptions) => get<GlobalRecord[]>('/global/marketplace/sync-jobs', params(options)),
  createMarketplaceSyncJob: (payload: GlobalRecord) => post<GlobalRecord>('/global/marketplace/sync-jobs', payload),

  listOptimizationPolicies: (options?: ListOptions) => get<GlobalRecord[]>('/global/optimization/policies', params(options)),
  upsertOptimizationPolicy: (payload: GlobalRecord) => post<GlobalRecord>('/global/optimization/policies', payload),
  listOptimizationRecommendations: (options?: ListOptions) => get<GlobalRecord[]>('/global/optimization/recommendations', params(options)),
  createOptimizationRecommendation: (payload: GlobalRecord) => post<GlobalRecord>('/global/optimization/recommendations', payload),
  decideOptimizationRecommendation: (id: string, payload: GlobalRecord) =>
    post<GlobalRecord>(`/global/optimization/recommendations/${id}/decision`, payload),
  listOptimizationRollbacks: (options?: ListOptions) => get<GlobalRecord[]>('/global/optimization/rollbacks', params(options)),
  createOptimizationRollback: (payload: GlobalRecord) => post<GlobalRecord>('/global/optimization/rollbacks', payload),
};

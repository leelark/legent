import { get, post } from '@/lib/api-client';

export const differentiationApi = {
  listCopilotRecommendations: () => get<any[]>('/differentiation/copilot/recommendations'),
  createCopilotRecommendation: (payload: any) => post<any>('/differentiation/copilot/recommendations', payload),
  decideCopilotRecommendation: (id: string, payload: any) => post<any>(`/differentiation/copilot/recommendations/${id}/decision`, payload),
  listDecisionPolicies: () => get<any[]>('/differentiation/decisioning/policies'),
  upsertDecisionPolicy: (payload: any) => post<any>('/differentiation/decisioning/policies', payload),
  evaluateDecisionPolicy: (payload: any) => post<any>('/differentiation/decisioning/evaluate', payload),
  listOmnichannelFlows: () => get<any[]>('/differentiation/omnichannel/flows'),
  upsertOmnichannelFlow: (payload: any) => post<any>('/differentiation/omnichannel/flows', payload),
  simulateOmnichannelFlow: (payload: any) => post<any>('/differentiation/omnichannel/simulate', payload),
  listDeveloperPackages: () => get<any[]>('/differentiation/developer/packages'),
  upsertDeveloperPackage: (payload: any) => post<any>('/differentiation/developer/packages', payload),
  createSandbox: (payload: any) => post<any>('/differentiation/developer/sandboxes', payload),
  createWebhookReplay: (payload: any) => post<any>('/differentiation/developer/webhook-replays', payload),
  listSloPolicies: () => get<any[]>('/differentiation/ops/slo-policies'),
  upsertSloPolicy: (payload: any) => post<any>('/differentiation/ops/slo-policies', payload),
  evaluateSloPolicy: (payload: any) => post<any>('/differentiation/ops/slo-policies/evaluate', payload),
};

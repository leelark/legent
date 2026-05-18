import { get, post } from '@/lib/api-client';

export type DifferentiationRecord = Record<string, unknown>;
export type DifferentiationJsonRecord = Record<string, unknown>;

export type CopilotRecommendationRequest = {
  workspaceId?: string;
  artifactType: string;
  artifactId?: string;
  objective: string;
  audienceSummary?: string;
  riskTolerance?: string;
  requireHumanApproval?: boolean;
  policyContext?: DifferentiationJsonRecord;
  candidateContent?: DifferentiationJsonRecord;
  constraints?: string[];
};

export type CopilotDecisionRequest = {
  decision: string;
  decisionNote?: string;
};

export type DecisionPolicyRequest = {
  workspaceId?: string;
  policyKey: string;
  name: string;
  status?: string;
  triggerEvent?: string;
  channel?: string;
  rules?: DifferentiationJsonRecord;
  variants?: DifferentiationJsonRecord[];
  guardrails?: DifferentiationJsonRecord;
  metadata?: DifferentiationJsonRecord;
};

export type DecisionEvaluateRequest = {
  policyKey: string;
  eventType?: string;
  channel?: string;
  profileUpdates?: DifferentiationJsonRecord;
  context?: DifferentiationJsonRecord;
};

export type OmnichannelFlowRequest = {
  workspaceId?: string;
  flowKey: string;
  name: string;
  status?: string;
  transactional?: boolean;
  channels?: string[];
  routingRules?: DifferentiationJsonRecord;
  guardrails?: DifferentiationJsonRecord;
  metadata?: DifferentiationJsonRecord;
};

export type OmnichannelSimulationRequest = {
  flowKey: string;
  preferredChannels?: string[];
  recipient?: DifferentiationJsonRecord;
  event?: DifferentiationJsonRecord;
};

export type DeveloperPackageRequest = {
  appKey: string;
  displayName: string;
  status?: string;
  apiVersion?: string;
  scopes?: string[];
  sdkTargets?: string[];
  sandboxEnabled?: boolean;
  marketplaceStatus?: string;
  webhookReplayEnabled?: boolean;
  metadata?: DifferentiationJsonRecord;
};

export type SandboxRequest = {
  appPackageId: string;
  dataProfile?: string;
  seedOptions?: DifferentiationJsonRecord;
};

export type WebhookReplayRequest = {
  appPackageId: string;
  sourceWebhookId?: string;
  targetUrl?: string;
  dryRun?: boolean;
  fromTime?: string | Date;
  toTime?: string | Date;
  eventTypes?: string[];
};

export type SloPolicyRequest = {
  workspaceId?: string;
  serviceName: string;
  status?: string;
  sloTargetPercent?: number;
  window?: string;
  errorBudgetMinutes?: number;
  syntheticProbe?: DifferentiationJsonRecord;
  selfHealingActions?: DifferentiationJsonRecord[];
  capacityForecast?: DifferentiationJsonRecord;
  incidentAutomation?: DifferentiationJsonRecord;
};

export type SloEvaluateRequest = {
  serviceName: string;
  successRatePercent?: number;
  p95LatencyMs?: number;
  saturationPercent?: number;
  queueDepth?: number;
  requests?: number;
  errors?: number;
};

export type CopilotRecommendationRecord = DifferentiationRecord & {
  status?: string;
  approval_required?: boolean;
  approvalRequired?: boolean;
};

export type DecisionPolicyRecord = DifferentiationRecord & {
  status?: string;
};

export type OmnichannelFlowRecord = DifferentiationRecord & {
  status?: string;
};

export type DeveloperPackageRecord = DifferentiationRecord & {
  status?: string;
  marketplace_status?: string;
  marketplaceStatus?: string;
};

export type SloPolicyRecord = DifferentiationRecord & {
  status?: string;
};

export type DecisionEvaluationResult = DifferentiationRecord & {
  policyId?: string;
  policyKey?: string;
  eventType?: string;
  selectedVariant?: DifferentiationJsonRecord;
  confidence?: number;
  eligible?: boolean;
  explanation?: string;
  guardrailResult?: DifferentiationJsonRecord;
  decidedAt?: string;
};

export type OmnichannelSimulationResult = DifferentiationRecord & {
  flowId?: string;
  flowKey?: string;
  route?: string[];
  primaryChannel?: string | null;
  transactional?: boolean;
  blocked?: boolean;
  simulationNotes?: string[];
  simulatedAt?: string;
};

export type SandboxRecord = DifferentiationRecord & {
  status?: string;
};

export type WebhookReplayRecord = DifferentiationRecord & {
  status?: string;
  dry_run?: boolean;
};

export type SloEvaluationResult = DifferentiationRecord & {
  policyId?: string;
  serviceName?: string;
  targetPercent?: number;
  successRatePercent?: number;
  errorBudgetBurnPercent?: number;
  p95LatencyMs?: number;
  p95ThresholdMs?: number;
  saturationPercent?: number;
  incidentStatus?: string;
  selfHealingTriggered?: boolean;
  capacityForecast?: DifferentiationJsonRecord;
  recommendedActions?: string[];
  evaluatedAt?: string;
};

export const differentiationApi = {
  listCopilotRecommendations: () => get<CopilotRecommendationRecord[]>('/differentiation/copilot/recommendations'),
  createCopilotRecommendation: (payload: CopilotRecommendationRequest) =>
    post<CopilotRecommendationRecord>('/differentiation/copilot/recommendations', payload),
  decideCopilotRecommendation: (id: string, payload: CopilotDecisionRequest) =>
    post<CopilotRecommendationRecord>(`/differentiation/copilot/recommendations/${id}/decision`, payload),
  listDecisionPolicies: () => get<DecisionPolicyRecord[]>('/differentiation/decisioning/policies'),
  upsertDecisionPolicy: (payload: DecisionPolicyRequest) =>
    post<DecisionPolicyRecord>('/differentiation/decisioning/policies', payload),
  evaluateDecisionPolicy: (payload: DecisionEvaluateRequest) =>
    post<DecisionEvaluationResult>('/differentiation/decisioning/evaluate', payload),
  listOmnichannelFlows: () => get<OmnichannelFlowRecord[]>('/differentiation/omnichannel/flows'),
  upsertOmnichannelFlow: (payload: OmnichannelFlowRequest) =>
    post<OmnichannelFlowRecord>('/differentiation/omnichannel/flows', payload),
  simulateOmnichannelFlow: (payload: OmnichannelSimulationRequest) =>
    post<OmnichannelSimulationResult>('/differentiation/omnichannel/simulate', payload),
  listDeveloperPackages: () => get<DeveloperPackageRecord[]>('/differentiation/developer/packages'),
  upsertDeveloperPackage: (payload: DeveloperPackageRequest) =>
    post<DeveloperPackageRecord>('/differentiation/developer/packages', payload),
  createSandbox: (payload: SandboxRequest) => post<SandboxRecord>('/differentiation/developer/sandboxes', payload),
  createWebhookReplay: (payload: WebhookReplayRequest) =>
    post<WebhookReplayRecord>('/differentiation/developer/webhook-replays', payload),
  listSloPolicies: () => get<SloPolicyRecord[]>('/differentiation/ops/slo-policies'),
  upsertSloPolicy: (payload: SloPolicyRequest) => post<SloPolicyRecord>('/differentiation/ops/slo-policies', payload),
  evaluateSloPolicy: (payload: SloEvaluateRequest) =>
    post<SloEvaluationResult>('/differentiation/ops/slo-policies/evaluate', payload),
};

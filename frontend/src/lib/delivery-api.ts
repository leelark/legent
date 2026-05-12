import { get, post } from './api-client';

export type DeliveryQueueStats = {
  pending: number;
  processing: number;
  sent: number;
  failed: number;
  replayPending: number;
  replayFailed: number;
  unhealthyProviders: number;
  updatedAt: string;
};

export type DeliveryMessage = {
  id: string;
  messageId: string;
  campaignId?: string;
  email: string;
  status: string;
  attemptCount?: number;
  failureClass?: string;
  providerResponse?: string;
  createdAt?: string;
};

export type ProviderCapacityProfile = {
  id: string;
  providerId: string;
  senderDomain?: string;
  ispDomain?: string;
  hourlyCap: number;
  dailyCap: number;
  currentMaxPerMinute: number;
  observedSuccessRate: number;
  bounceRate: number;
  complaintRate: number;
  backpressureScore: number;
  failoverProviderId?: string;
  status: string;
  lastEvaluatedAt?: string;
};

export type ProviderThrottleDecision = {
  providerId: string;
  throttleState: string;
  recommendedPerMinute: number;
  recommendedPerSecond: number;
  failoverProviderId?: string;
  reason: string;
  evaluatedAt: string;
};

export type ProviderFailoverTest = {
  id: string;
  primaryProviderId: string;
  failoverProviderId?: string;
  status: string;
  resultCode: string;
  diagnostic?: string;
  startedAt: string;
  completedAt?: string;
};

export const getQueueStats = async () => get<DeliveryQueueStats>('/delivery/queue/stats');

export const listMessages = async (limit = 50) =>
  get<DeliveryMessage[]>(`/delivery/messages?limit=${limit}`);

export const retryMessage = async (messageId: string, reason?: string) =>
  post<DeliveryMessage>(`/delivery/messages/${encodeURIComponent(messageId)}/retry`, { reason });

export const enqueueReplay = async (messageId: string, reason?: string) =>
  post('/delivery/replay', { messageId, reason });

export const processReplay = async (maxItems = 100) =>
  post<{ processed: number; processedAt: string }>('/delivery/replay/process', { maxItems });

export const getFailureDiagnostics = async () =>
  get<{ failedRecent: number; byFailureClass: Record<string, number>; replayQueueDepth: number }>('/delivery/diagnostics/failures');

export const getWarmupStatus = async () =>
  get<{ activeProviders: number; healthyProviders: number; degradedProviders: number; unhealthyProviders: number; readiness: number; updatedAt: string }>(
    '/delivery/warmup/status'
  );

export const listProviderCapacity = async () => get<ProviderCapacityProfile[]>('/delivery/provider-capacity');

export const evaluateProviderCapacity = async (payload: {
  providerId: string;
  senderDomain?: string;
  ispDomain?: string;
  riskScore?: number;
}) => post<ProviderThrottleDecision>('/delivery/provider-capacity/evaluate', payload);

export const runProviderFailoverTest = async (payload: { primaryProviderId: string; failoverProviderId?: string }) =>
  post<ProviderFailoverTest>('/delivery/provider-failover/tests', payload);

export const listProviderFailoverTests = async (limit = 25) =>
  get<ProviderFailoverTest[]>(`/delivery/provider-failover/tests?limit=${limit}`);

export const getDeadLetters = async (limit = 100) =>
  get<{ items: DeliveryMessage[]; byFailureClass: Record<string, number>; count: number; updatedAt: string }>(
    `/delivery/dead-letters?limit=${limit}`
  );

export const replayDeadLetters = async (messageIds: string[], reason?: string) =>
  post<{ queued: number; requested: number; queuedAt: string }>('/delivery/dead-letters/replay', { messageIds, reason });

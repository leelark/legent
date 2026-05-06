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


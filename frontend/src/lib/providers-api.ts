import { get } from './api-client';

export type Provider = {
  id: string;
  name: string;
  type: string;
  host?: string;
  port?: number;
  username?: string;
  isActive: boolean;
  priority?: number;
  maxSendRate?: number;
  healthCheckEnabled?: boolean;
  healthCheckUrl?: string;
  healthCheckIntervalSeconds?: number;
  healthStatus?: string;
  lastHealthCheckAt?: string;
  createdAt?: string;
};

export type ProviderCreateRequest = {
  name: string;
  type: string;
  host?: string;
  port?: number;
  username?: string;
  password?: string;
  isActive?: boolean;
  priority?: number;
  maxSendRate?: number;
  healthCheckEnabled?: boolean;
  healthCheckUrl?: string;
  healthCheckIntervalSeconds?: number;
};

export const listProviders = async (includeInactive = true) =>
  get<Provider[]>(`/providers?includeInactive=${includeInactive ? 'true' : 'false'}`);

export const listProviderHealth = async () =>
  get<Array<{
    id: string;
    name: string;
    type: string;
    isActive: boolean;
    healthStatus?: string;
    lastHealthCheckAt?: string;
    priority?: number;
  }>>('/providers/health');

import { get, post } from './api-client';

export type AdminConfig = {
  id?: string;
  key: string;
  value: string;
  description?: string;
  category?: string;
  configType?: string;
  module?: string;
  scope?: string;
  dependencyKeys?: string[];
  updatedAt?: string;
  editable?: boolean;
  tenantId?: string;
  workspaceId?: string;
  environmentId?: string;
};

function normalizeConfig(config: any): AdminConfig {
  return {
    ...config,
    configType: config?.configType || config?.type || 'STRING',
    scope: config?.scope || config?.scopeType || 'TENANT',
    module: config?.module || (config?.category ? String(config.category).toLowerCase() : undefined),
    category: config?.category || (config?.module ? String(config.module).toUpperCase() : undefined),
  };
}

export type Branding = {
  id?: string;
  name?: string;
  logoUrl?: string;
  primaryColor?: string;
  secondaryColor?: string;
};

export type WebhookIntegration = {
  id?: string;
  name: string;
  url: string;
  eventType: string;
  isActive?: boolean;
  secretKey?: string;
};

export const getAdminConfigs = async () => (await get<any[]>('/admin/configs')).map(normalizeConfig);
export const saveAdminConfig = async (config: AdminConfig) => normalizeConfig(await post<any>('/admin/configs', config));
export const getBranding = async () => get<Branding>('/admin/branding');
export const saveBranding = async (branding: Branding) => post<Branding>('/admin/branding', branding);

export const getAdminSettings = async (params?: { module?: string; category?: string; scope?: string }) =>
  (await get<any[]>('/admin/settings', { params })).map(normalizeConfig);

export const validateAdminSetting = async (payload: {
  key: string;
  value: string;
  module?: string;
  category?: string;
  type?: string;
  scope?: string;
  workspaceId?: string;
  environmentId?: string;
  dependencyKeys?: string[];
}) => post<{ valid: boolean; errors: string[] }>('/admin/settings/validate', payload);

export const applyAdminSetting = async (payload: {
  key: string;
  value: string;
  module?: string;
  category?: string;
  type?: string;
  scope?: string;
  workspaceId?: string;
  environmentId?: string;
  dependencyKeys?: string[];
  validationSchema?: Record<string, unknown>;
  metadata?: Record<string, unknown>;
  description?: string;
}) => normalizeConfig(await post<any>('/admin/settings/apply', payload));

export const resetAdminSetting = async (payload: {
  key: string;
  scope?: string;
  workspaceId?: string;
  environmentId?: string;
}) => normalizeConfig(await post<any>('/admin/settings/reset', payload));

export const getSettingImpact = async (params: {
  key: string;
  module?: string;
  scope?: string;
  workspaceId?: string;
  environmentId?: string;
}) => get<{ key: string; module: string; impactedModules: string[]; notices: string[] }>('/admin/settings/impact', { params });

export const getSettingHistory = async (key?: string) =>
  get<Array<Record<string, unknown>>>('/admin/settings/history', { params: key ? { key } : undefined });

export const rollbackSetting = async (key: string, version: number) =>
  normalizeConfig(await post<any>(`/admin/settings/rollback?key=${encodeURIComponent(key)}&version=${version}`));

export const getBootstrapStatus = async () =>
  get<{
    tenantId: string;
    workspaceId?: string;
    environmentId?: string;
    status: string;
    message?: string;
    retryCount: number;
    lastAttemptAt?: string;
    completedAt?: string;
    modules?: Record<string, unknown>;
  }>('/admin/bootstrap/status');

export const repairBootstrap = async (force = true) =>
  post('/admin/bootstrap/repair', { force });

type PlatformWebhookPayload = {
  id?: string;
  name: string;
  endpointUrl: string;
  eventsSubscribed: string;
  isActive?: boolean;
  secretKey?: string;
};

function parseEvents(eventsSubscribed: string | undefined): string {
  if (!eventsSubscribed) {
    return '';
  }
  try {
    const parsed = JSON.parse(eventsSubscribed);
    return Array.isArray(parsed) ? parsed.join(', ') : String(parsed);
  } catch {
    return eventsSubscribed;
  }
}

function toUiWebhook(payload: PlatformWebhookPayload): WebhookIntegration {
  return {
    id: payload.id,
    name: payload.name,
    url: payload.endpointUrl,
    eventType: parseEvents(payload.eventsSubscribed),
    isActive: payload.isActive,
    secretKey: payload.secretKey,
  };
}

function toPlatformWebhookPayload(wh: WebhookIntegration): PlatformWebhookPayload {
  const events = wh.eventType
    .split(',')
    .map((event) => event.trim())
    .filter(Boolean);

  return {
    id: wh.id,
    name: wh.name,
    endpointUrl: wh.url,
    eventsSubscribed: JSON.stringify(events),
    isActive: wh.isActive ?? true,
    secretKey: wh.secretKey,
  };
}

export const getWebhooks = async () => {
  const webhooks = await get<PlatformWebhookPayload[]>('/platform/webhooks');
  return (webhooks || []).map(toUiWebhook);
};

export const saveWebhook = async (wh: WebhookIntegration) => {
  const saved = await post<PlatformWebhookPayload>('/platform/webhooks', toPlatformWebhookPayload(wh));
  return toUiWebhook(saved);
};

export const search = async (q: string) => get<unknown>('/platform/search', { params: { q } });

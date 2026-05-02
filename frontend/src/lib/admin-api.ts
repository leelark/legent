import { get, post } from './api-client';

export type AdminConfig = {
  id?: string;
  key: string;
  value: string;
  description?: string;
  category?: string;
  configType?: string;
  editable?: boolean;
  tenantId?: string;
};

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

export const getAdminConfigs = async () => get<AdminConfig[]>('/admin/configs');
export const saveAdminConfig = async (config: AdminConfig) => post<AdminConfig>('/admin/configs', config);
export const getBranding = async () => get<Branding>('/admin/branding');
export const saveBranding = async (branding: Branding) => post<Branding>('/admin/branding', branding);

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

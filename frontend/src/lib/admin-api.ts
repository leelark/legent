import { get, post } from './api-client';

export type AdminConfig = {
  id?: string;
  key: string;
  value: string;
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
};

export const getAdminConfigs = async () => get<AdminConfig[]>('/admin/configs');
export const saveAdminConfig = async (config: AdminConfig) => post<AdminConfig>('/admin/configs', config);
export const getBranding = async () => get<Branding>('/admin/branding');
export const saveBranding = async (branding: Branding) => post<Branding>('/admin/branding', branding);
export const getWebhooks = async () => get<WebhookIntegration[]>('/admin/webhooks');
export const saveWebhook = async (wh: WebhookIntegration) => post<WebhookIntegration>('/admin/webhooks', wh);
export const search = async (q: string) => get<unknown>('/admin/search', { params: { q } });

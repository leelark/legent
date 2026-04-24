import { get, post } from './api-client';

export const getAdminConfigs = async () => get('/admin/configs');
export const saveAdminConfig = async (config: any) => post('/admin/configs', config);
export const getBranding = async () => get('/admin/branding');
export const saveBranding = async (branding: any) => post('/admin/branding', branding);
export const getWebhooks = async () => get('/admin/webhooks');
export const saveWebhook = async (wh: any) => post('/admin/webhooks', wh);
export const search = async (q: string) => get('/admin/search', { params: { q } });

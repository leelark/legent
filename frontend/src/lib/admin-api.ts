import apiClient from './api-client';

export const getAdminConfigs = async () => (await apiClient.get('/admin/configs')).data;
export const saveAdminConfig = async (config: any) => (await apiClient.post('/admin/configs', config)).data;
export const getBranding = async () => (await apiClient.get('/admin/branding')).data;
export const saveBranding = async (branding: any) => (await apiClient.post('/admin/branding', branding)).data;
export const getWebhooks = async () => (await apiClient.get('/admin/webhooks')).data;
export const saveWebhook = async (wh: any) => (await apiClient.post('/admin/webhooks', wh)).data;
export const search = async (q: string) => (await apiClient.get('/admin/search', { params: { q } })).data;

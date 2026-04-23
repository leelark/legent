import apiClient from './api-client';

export const getEventCounts = async () => {
  const res = await apiClient.get('/api/v1/analytics/events/counts');
  return res.data;
};

export const getEventTimeline = async (eventType: string) => {
  const res = await apiClient.get('/api/v1/analytics/events/timeline', { params: { eventType } });
  return res.data;
};

import apiClient from './api-client';

export const getEventCounts = async () => {
  const res = await apiClient.get('/analytics/event-counts');
  return res.data;
};

export const getEventTimeline = async (eventType: string) => {
  const res = await apiClient.get('/analytics/timeline', { params: { eventType } });
  return res.data;
};

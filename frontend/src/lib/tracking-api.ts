import { get } from './api-client';

export type TrackingAggregate = {
  event_type: string;
  count: number;
};

export const getEventCounts = async () => get<any>('/analytics/events/counts');

export const getEventTimeline = async (eventType: string) =>
  get<any>('/analytics/events/timeline', { params: { eventType } });

export const getFunnel = async (campaignId: string) =>
  get<TrackingAggregate[]>('/analytics/funnel', { params: { campaignId } });

export const getSegment = async (field: string, value: string) =>
  get<TrackingAggregate[]>('/analytics/segment', { params: { field, value } });

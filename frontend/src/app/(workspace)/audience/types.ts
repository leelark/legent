import type { SegmentRules } from '@/components/audience/SegmentRuleBuilder';

export type PagedResponse<T> = {
  content?: T[];
  data?: T[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
  first?: boolean;
  last?: boolean;
};

export type CountResponse = number | {
  count?: number;
};

export type Subscriber = {
  id: string;
  email: string;
  subscriberKey?: string;
  firstName?: string;
  lastName?: string;
  phone?: string;
  status: string;
  source?: string;
  createdAt?: string;
};

export type AudienceList = {
  id: string;
  name?: string;
  description?: string;
  listType?: string;
  memberCount?: number;
  status: string;
};

export type DataExtension = {
  id: string;
  name?: string;
  description?: string;
  sendable?: boolean;
  recordCount?: number;
};

export type ImportJob = {
  id: string;
  fileName?: string;
  source?: string;
  status: string;
  progressPercent?: number;
  totalRows?: number;
  processedRows?: number;
  successRows?: number;
  errorRows?: number;
  targetType?: string;
};

export type Segment = {
  id: string;
  name?: string;
  description?: string;
  status: string;
  memberCount?: number;
  createdAt?: string;
  rules?: SegmentRules | null;
};

export function pageItems<T>(response: PagedResponse<T> | T[] | null | undefined): T[] {
  if (!response) {
    return [];
  }
  return Array.isArray(response) ? response : response.content ?? response.data ?? [];
}

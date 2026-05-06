import { get, put } from '@/lib/api-client';

export type UiMode = 'BASIC' | 'ADVANCED';

export type UserPreferences = {
  tenantId: string;
  userId: string;
  uiMode: UiMode;
  theme: 'light' | 'dark';
  density: string;
  sidebarCollapsed: boolean;
  metadata?: Record<string, unknown>;
};

export const getUserPreferences = async () =>
  get<UserPreferences>('/users/preferences');

export const updateUserPreferences = async (payload: Partial<UserPreferences>) =>
  put<UserPreferences>('/users/preferences', payload);


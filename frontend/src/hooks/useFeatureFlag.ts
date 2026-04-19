'use client';

import { useCallback } from 'react';
import { get } from '@/lib/api-client';
import { useApi } from './useApi';

interface FeatureFlagResult {
  flagKey: string;
  enabled: boolean;
  resolvedScope: string;
}

/**
 * Hook to evaluate a feature flag for the current tenant.
 * Returns whether the flag is enabled.
 */
export function useFeatureFlag(flagKey: string) {
  const { data, loading } = useApi<FeatureFlagResult>(
    `/feature-flags/evaluate/${flagKey}`
  );

  return {
    isEnabled: data?.enabled ?? false,
    loading,
    scope: data?.resolvedScope ?? 'DEFAULT',
  };
}

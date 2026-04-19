'use client';

import { useCallback, useEffect, useState } from 'react';
import { get } from '@/lib/api-client';

interface UseApiOptions {
  immediate?: boolean;
}

interface UseApiReturn<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  refetch: () => Promise<void>;
}

/**
 * Generic data fetching hook with loading/error states.
 */
export function useApi<T>(url: string, options: UseApiOptions = {}): UseApiReturn<T> {
  const { immediate = true } = options;
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await get<{ data: T }>(url);
      setData((result as any).data ?? result);
    } catch (err: any) {
      const message = err.response?.data?.error?.message || err.message || 'An error occurred';
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [url]);

  useEffect(() => {
    if (immediate) {
      fetchData();
    }
  }, [immediate, fetchData]);

  return { data, loading, error, refetch: fetchData };
}

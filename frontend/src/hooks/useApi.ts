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

function readRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}

function readString(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value : null;
}

function readPath(value: unknown, path: string[]): unknown {
  return path.reduce<unknown>((current, key) => readRecord(current)?.[key], value);
}

function readApiErrorMessage(error: unknown): string {
  return (
    readString(readPath(error, ['normalized', 'message'])) ||
    readString(readPath(error, ['response', 'data', 'error', 'message'])) ||
    (error instanceof Error ? readString(error.message) : null) ||
    readString(readRecord(error)?.message) ||
    'An error occurred'
  );
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
      const result = await get<T>(url);
      setData(result);
    } catch (err: unknown) {
      setError(readApiErrorMessage(err));
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

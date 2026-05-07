'use client';

import React, { useMemo, useState } from 'react';
import { search } from '@/lib/admin-api';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { AdminEmptyState, AdminPanel, AdminSkeletonRows, StatusPill } from '@/components/admin/AdminChrome';

type SearchRow = Record<string, unknown>;

function normalizeResults(results: unknown): SearchRow[] {
  if (!results) return [];
  if (Array.isArray(results)) return results.filter((item): item is SearchRow => item !== null && typeof item === 'object');
  if (typeof results === 'object') {
    const value = results as Record<string, unknown>;
    const possible = value.results || value.items || value.content || value.data;
    if (Array.isArray(possible)) {
      return possible.filter((item): item is SearchRow => item !== null && typeof item === 'object');
    }
    return [value];
  }
  return [];
}

function read(row: SearchRow, keys: string[]) {
  for (const key of keys) {
    const value = row[key];
    if (value !== undefined && value !== null && value !== '') return String(value);
  }
  return '-';
}

export const SearchPanel: React.FC = () => {
  const [q, setQ] = useState('');
  const [results, setResults] = useState<unknown>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSearch = async () => {
    setLoading(true);
    setError(null);
    try {
      setResults(await search(q.trim()));
    } catch (err: any) {
      setError(err?.normalized?.message || err?.message || 'Search failed.');
      setResults(null);
    } finally {
      setLoading(false);
    }
  };

  const rows = useMemo(() => normalizeResults(results), [results]);

  return (
    <AdminPanel title="Global Search" subtitle="Grouped admin search results without raw JSON output.">
      <div className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-[1fr_auto]">
          <Input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && q.trim()) {
                handleSearch();
              }
            }}
            label="Search term"
            placeholder="Search tenants, workspaces, settings, contacts..."
          />
          <div className="flex items-end">
            <Button className="w-full sm:w-auto" onClick={handleSearch} disabled={!q.trim()} loading={loading}>
              Search
            </Button>
          </div>
        </div>

        {error ? <div className="rounded-xl border border-danger/30 bg-danger/10 px-4 py-3 text-sm text-danger">{error}</div> : null}

        {loading ? (
          <AdminSkeletonRows rows={4} />
        ) : !results ? (
          <AdminEmptyState title="Run a search" description="Search across platform entities, public content, runtime settings, and operational records." />
        ) : rows.length === 0 ? (
          <AdminEmptyState title="No matching records" description="Try a broader search term or validate that the entity is in the current workspace context." />
        ) : (
          <div className="grid gap-3 lg:grid-cols-2">
            {rows.map((row, index) => {
              const title = read(row, ['title', 'name', 'label', 'key', 'id']);
              return (
                <div key={`${title}-${index}`} className="rounded-xl border border-border-default bg-surface-primary p-4 shadow-sm transition-all hover:border-brand-300/60 hover:shadow-lg">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="truncate font-semibold text-content-primary">{title}</p>
                      <p className="mt-1 text-xs text-content-muted">{read(row, ['id', 'resourceId', 'slug'])}</p>
                    </div>
                    <StatusPill status={read(row, ['type', 'entityType', 'status'])} />
                  </div>
                  <p className="mt-3 line-clamp-2 text-sm text-content-secondary">
                    {read(row, ['description', 'message', 'summary', 'email', 'workEmail'])}
                  </p>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </AdminPanel>
  );
};

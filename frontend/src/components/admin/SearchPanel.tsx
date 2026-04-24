'use client';

import React, { useState } from 'react';
import { search } from '@/lib/admin-api';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';

export const SearchPanel: React.FC = () => {
  const [q, setQ] = useState('');
  const [results, setResults] = useState<unknown>(null);

  const handleSearch = async () => {
    setResults(await search(q));
  };

  return (
    <Card className="p-4">
      <h3 className="font-bold mb-2">Global Search</h3>
      <div className="mb-4 flex gap-2">
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Search..."
          className="rounded border px-2 py-1"
        />
        <Button onClick={handleSearch} disabled={!q}>
          Search
        </Button>
      </div>
      <pre className="max-h-64 overflow-auto rounded bg-slate-100 p-2 text-xs">
        {results ? JSON.stringify(results, null, 2) : ''}
      </pre>
    </Card>
  );
};

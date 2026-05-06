'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Upload } from '@phosphor-icons/react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Table } from '@/components/ui/Table';
import { get } from '@/lib/api-client';

const statusBadgeMap: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'default'> = {
  COMPLETED: 'success',
  COMPLETED_WITH_ERRORS: 'warning',
  FAILED: 'danger',
  RUNNING: 'info',
  PENDING: 'default',
};

const columns = [
  { key: 'fileName', header: 'File Name' },
  {
    key: 'status',
    header: 'Status',
    render: (row: any) => <Badge variant={statusBadgeMap[row.status] || 'default'}>{row.status}</Badge>,
  },
  {
    key: 'progress',
    header: 'Progress',
    render: (row: any) => (
      <div className="flex items-center gap-2">
        <div className="h-1.5 w-24 overflow-hidden rounded-full bg-surface-secondary">
          <div
            className="h-full rounded-full bg-brand-500 transition-all duration-500"
            style={{ width: `${row.progressPercent || 0}%` }}
          />
        </div>
        <span className="text-xs text-content-muted">{row.progressPercent?.toFixed(0) || 0}%</span>
      </div>
    ),
  },
  {
    key: 'rows',
    header: 'Rows',
    render: (row: any) => `${row.successRows || 0} / ${row.totalRows || 0}`,
  },
  {
    key: 'actions',
    header: '',
    render: (row: any) => (
      <Link href={`/app/audience/imports/${row.id}`} className="text-xs text-brand-600 hover:underline">
        Details
      </Link>
    ),
  },
];

export default function ImportsPage() {
  const [imports, setImports] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchImports = async () => {
    setLoading(true);
    try {
      const response = await get<any>('/imports?page=0&size=50');
      setImports(response.content || response || []);
    } catch {
      setImports([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchImports();
  }, []);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-content-primary">Imports</h1>
          <p className="mt-1 text-sm text-content-secondary">Import subscriber data from CSV files</p>
        </div>
        <Link href="/app/audience/imports/new">
          <Button icon={<Upload size={16} />}>New Import</Button>
        </Link>
      </div>

      <Card className="overflow-hidden !p-0">
        <Table
          columns={columns}
          data={imports}
          rowKey={(row: any) => row.id}
          emptyMessage="No imports yet"
          loading={loading}
        />
      </Card>
    </div>
  );
}

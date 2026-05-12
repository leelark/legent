'use client';


import { useEffect, useState } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Table } from '@/components/ui/Table';
import { EmptyState } from '@/components/ui/EmptyState';
import { PageHeader } from '@/components/ui/PageChrome';
import { Plus, PencilSimple, Trash } from '@phosphor-icons/react';
import { useRouter } from 'next/navigation';
import { get, del } from '@/lib/api-client';

const statusBadgeMap: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'default'> = {
  ACTIVE: 'success',
  DRAFT: 'default',
  COMPUTING: 'info',
  ERROR: 'danger',
};

export default function SegmentsPage() {
  const [segments, setSegments] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const router = useRouter();

  const fetchSegments = async () => {
    setLoading(true);
    try {
      const res = await get<any>('/segments?page=0&size=50');
      setSegments(res.content || res || []);
    } catch {
      setSegments([]);
    }
    setLoading(false);
  };

  useEffect(() => {
    fetchSegments();
  }, []);

  const handleDelete = async (id: string) => {
    if (confirm('Delete this segment?')) {
      try {
        await del(`/segments/${id}`);
        fetchSegments();
      } catch {
        alert('Failed to delete segment');
      }
    }
  };

  const columns = [
    { key: 'name', header: 'Name' },
    { key: 'status', header: 'Status', render: (row: any) => <Badge variant={statusBadgeMap[row.status] || 'default'}>{row.status}</Badge> },
    { key: 'memberCount', header: 'Members' },
    { key: 'createdAt', header: 'Created', render: (row: any) => row.createdAt ? new Date(row.createdAt).toLocaleDateString() : '\u2014' },
    {
      key: 'actions', header: '',
      render: (row: any) => (
        <div className="flex justify-end gap-2">
          <button onClick={() => router.push(`/app/audience/segments/${row.id}`)} className="text-content-muted hover:text-accent p-1"><PencilSimple size={16} /></button>
          <button onClick={() => handleDelete(row.id)} className="text-content-muted hover:text-danger p-1"><Trash size={16} /></button>
        </div>
      )
    }
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Audience rules"
        title="Segments"
        description="Define reusable subscriber groups using rules, status, and computed audience state."
        action={<Button icon={<Plus size={16} />} onClick={() => router.push('/app/audience/segments/new')}>Create Segment</Button>}
      />

      <Card className="!p-0 overflow-hidden">
        {segments.length === 0 && !loading ? (
          <EmptyState
            type="empty"
            title="No segments yet"
            description="Create your first segment to target specific subscriber groups."
            action={<Button icon={<Plus size={16} />} onClick={() => router.push('/app/audience/segments/new')}>Create Segment</Button>}
          />
        ) : (
          <Table
            columns={columns}
            data={segments}
            rowKey={(row: any) => row.id}
            emptyMessage="No segments found"
            loading={loading}
          />
        )}
      </Card>
    </div>
  );
}

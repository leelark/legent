'use client';


import { useCallback, useEffect, useState } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Table } from '@/components/ui/Table';
import { Modal } from '@/components/ui/Modal';
import { EmptyState } from '@/components/ui/EmptyState';
import { PageHeader } from '@/components/ui/PageChrome';
import { useToast } from '@/components/ui/Toast';
import { Plus, PencilSimple, Trash } from '@phosphor-icons/react';
import { useRouter } from 'next/navigation';
import { get, del } from '@/lib/api-client';
import { pageItems, type PagedResponse, type Segment } from '../types';

const statusBadgeMap: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'default'> = {
  ACTIVE: 'success',
  DRAFT: 'default',
  COMPUTING: 'info',
  ERROR: 'danger',
};

export default function SegmentsPage() {
  const { addToast } = useToast();
  const [segments, setSegments] = useState<Segment[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [deleteSegment, setDeleteSegment] = useState<Segment | null>(null);
  const router = useRouter();

  const fetchSegments = useCallback(async () => {
    setLoading(true);
    try {
      const res = await get<PagedResponse<Segment> | Segment[]>('/segments?page=0&size=50');
      setSegments(pageItems(res));
      setError(null);
    } catch {
      setSegments([]);
      setError('Failed to load segments');
      addToast({ type: 'error', title: 'Segments unavailable', message: 'Unable to load segment inventory.' });
    }
    setLoading(false);
  }, [addToast]);

  useEffect(() => {
    fetchSegments();
  }, [fetchSegments]);

  const remove = async () => {
    if (!deleteSegment) return;
    try {
      await del(`/segments/${deleteSegment.id}`);
      addToast({ type: 'success', title: 'Segment deleted', message: deleteSegment.name });
      setDeleteSegment(null);
      fetchSegments();
    } catch {
      setError('Failed to delete segment');
      addToast({ type: 'error', title: 'Segment delete failed', message: 'No segment was removed.' });
    }
  };

  const columns = [
    { key: 'name', header: 'Name' },
    { key: 'status', header: 'Status', render: (row: Segment) => <Badge variant={statusBadgeMap[row.status] || 'default'}>{row.status}</Badge> },
    { key: 'memberCount', header: 'Members' },
    { key: 'createdAt', header: 'Created', render: (row: Segment) => row.createdAt ? new Date(row.createdAt).toLocaleDateString() : '\u2014' },
    {
      key: 'actions', header: '',
      render: (row: Segment) => (
        <div className="flex justify-end gap-2">
          <button aria-label={`Edit ${row.name}`} onClick={() => router.push(`/app/audience/segments/${row.id}`)} className="text-content-muted hover:text-accent p-1"><PencilSimple size={16} /></button>
          <button aria-label={`Delete ${row.name}`} onClick={() => setDeleteSegment(row)} className="text-content-muted hover:text-danger p-1"><Trash size={16} /></button>
        </div>
      )
    }
  ];

  return (
    <div className="space-y-6">
      {error && <div className="rounded-lg bg-red-100 px-4 py-2 text-sm text-red-700">{error}</div>}
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
            rowKey={(row: Segment) => row.id}
            emptyMessage="No segments found"
            loading={loading}
          />
        )}
      </Card>

      <Modal
        open={Boolean(deleteSegment)}
        onClose={() => setDeleteSegment(null)}
        title="Delete segment?"
        description="This removes the segment definition from audience targeting. Confirm only after checking campaign targeting impact."
        size="sm"
        footer={(
          <>
            <Button variant="secondary" onClick={() => setDeleteSegment(null)}>Cancel</Button>
            <Button variant="danger" onClick={remove}>Delete</Button>
          </>
        )}
      >
        <p className="text-sm leading-6 text-content-secondary">
          Delete {deleteSegment?.name ? `"${deleteSegment.name}"` : 'this segment'}?
        </p>
      </Modal>
    </div>
  );
}

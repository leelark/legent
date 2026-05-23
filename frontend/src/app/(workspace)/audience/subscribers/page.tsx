'use client';

import { useState } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { Table } from '@/components/ui/Table';
import { Modal } from '@/components/ui/Modal';
import { PageHeader } from '@/components/ui/PageChrome';
import { useToast } from '@/components/ui/Toast';
import { useApi } from '@/hooks/useApi';
import { useDebounce } from '@/hooks/useDebounce';
import { post, put, del } from '@/lib/api-client';
import { MagnifyingGlass, Plus, Trash, PencilSimple } from '@phosphor-icons/react';
import type { PagedResponse, Subscriber } from '../types';

const statusBadgeMap: Record<string, 'success' | 'warning' | 'danger' | 'default'> = {
  ACTIVE: 'success',
  SUBSCRIBED: 'success',
  PENDING: 'default',
  UNSUBSCRIBED: 'warning',
  BOUNCED: 'danger',
  COMPLAINED: 'danger',
  SUPPRESSED: 'warning',
  INACTIVE: 'default',
  HELD: 'default',
  BLOCKED: 'danger',
};

export default function SubscribersPage() {
  const { addToast } = useToast();
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(0);
  const debouncedSearch = useDebounce(search, 300);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  
  const [formData, setFormData] = useState({
    email: '',
    subscriberKey: '',
    firstName: '',
    lastName: '',
    phone: ''
  });
  const [selected, setSelected] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [deleteRequest, setDeleteRequest] = useState<{
    mode: 'single' | 'bulk';
    id?: string;
    count: number;
  } | null>(null);

  const queryParams = new URLSearchParams({
    page: String(page),
    size: '20',
  });
  if (debouncedSearch.trim()) {
    queryParams.set('search', debouncedSearch.trim());
  }
  if (statusFilter) {
    queryParams.set('status', statusFilter);
  }

  const { data, loading, refetch } = useApi<PagedResponse<Subscriber>>(`/subscribers?${queryParams.toString()}`);
  const rows = data?.content ?? data?.data ?? [];
  const totalElements = data?.totalElements ?? 0;
  const isFirstPage = data?.first ?? true;
  const isLastPage = data?.last ?? true;

  const columns = [
    {
      key: 'email', header: 'Email',
      render: (row: Subscriber) => (
        <div>
          <p className="font-medium text-content-primary">{row.email}</p>
          <p className="text-xs text-content-muted">{row.subscriberKey}</p>
        </div>
      ),
    },
    {
      key: 'name', header: 'Name',
      render: (row: Subscriber) => `${row.firstName || ''} ${row.lastName || ''}`.trim() || '-',
    },
    {
      key: 'status', header: 'Status',
      render: (row: Subscriber) => <Badge variant={statusBadgeMap[row.status] || 'default'}>{row.status}</Badge>,
    },
    {
      key: 'source', header: 'Source',
      render: (row: Subscriber) => row.source || '-',
    },
    {
      key: 'createdAt', header: 'Created',
      render: (row: Subscriber) => row.createdAt ? new Date(row.createdAt).toLocaleDateString() : '-',
    },
    {
      key: 'actions', header: '',
      render: (row: Subscriber) => (
        <div className="flex justify-end gap-2">
          <button aria-label={`Edit ${row.email}`} onClick={() => openEdit(row)} className="text-content-muted hover:text-accent p-1"><PencilSimple size={16} /></button>
          <button aria-label={`Delete ${row.email}`} onClick={() => handleDelete(row.id)} className="text-content-muted hover:text-danger p-1"><Trash size={16} /></button>
        </div>
      )
    }
  ];

  const handleSave = async () => {
    try {
      const payload = {
        ...formData,
        subscriberKey: formData.subscriberKey?.trim() ? formData.subscriberKey.trim() : undefined,
      };
      if (editingId) {
        await put(`/subscribers/${editingId}`, payload);
      } else {
        await post('/subscribers', payload);
      }
      setIsModalOpen(false);
      addToast({ type: 'success', title: editingId ? 'Subscriber updated' : 'Subscriber added', message: payload.email });
      refetch();
    } catch {
      setError('Failed to save subscriber');
      addToast({ type: 'error', title: 'Subscriber save failed', message: 'Unable to save subscriber.' });
    }
  };

  const handleDelete = async (id: string) => {
    setDeleteRequest({ mode: 'single', id, count: 1 });
  };

  const handleBulkDelete = async () => {
    if (selected.length === 0) return;
    setDeleteRequest({ mode: 'bulk', count: selected.length });
  };

  const confirmDelete = async () => {
    if (!deleteRequest) return;
    try {
      if (deleteRequest.mode === 'single' && deleteRequest.id) {
        await del(`/subscribers/${deleteRequest.id}`);
      } else {
        await post('/subscribers/bulk-actions', {
          action: 'DELETE',
          subscriberIds: selected,
        });
        setSelected([]);
      }
      addToast({
        type: 'success',
        title: deleteRequest.count === 1 ? 'Subscriber deleted' : 'Subscribers deleted',
        message: `${deleteRequest.count} record${deleteRequest.count === 1 ? '' : 's'} removed.`,
      });
      setDeleteRequest(null);
      refetch();
    } catch {
      setError(deleteRequest.mode === 'single' ? 'Failed to delete subscriber' : 'Bulk delete failed');
      addToast({ type: 'error', title: 'Delete failed', message: 'No subscriber records were removed.' });
    }
  };

  const openEdit = (row: Subscriber) => {
    setEditingId(row.id);
    setFormData({
      email: row.email,
      subscriberKey: row.subscriberKey || '',
      firstName: row.firstName || '',
      lastName: row.lastName || '',
      phone: row.phone || ''
    });
    setIsModalOpen(true);
  };

  const openNew = () => {
    setEditingId(null);
    setFormData({ email: '', subscriberKey: '', firstName: '', lastName: '', phone: '' });
    setIsModalOpen(true);
  };

  return (
    <div className="space-y-6">
      {error && (
        <div className="rounded-xl border border-danger/30 bg-danger/10 px-4 py-2 text-sm text-danger">{error}</div>
      )}
      <PageHeader
        eyebrow="Subscriber database"
        title="Subscribers"
        description="Search, filter, edit, and maintain people records used by targeting and compliance."
        action={
          <>
          <Button onClick={openNew} icon={<Plus size={16} />}>Add Subscriber</Button>
          <Button variant="danger" onClick={handleBulkDelete} disabled={selected.length === 0}>Delete Selected</Button>
          </>
        }
      />

      {/* Filters */}
      <Card>
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
          <div className="relative flex-1">
            <MagnifyingGlass size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-content-muted" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search by email, name, or subscriber key..."
              className="w-full rounded-lg border border-border-default bg-surface-secondary py-2 pl-9 pr-4 text-sm text-content-primary placeholder-content-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/30 transition-all"
            />
          </div>
          <div className="flex gap-2">
            {['', 'ACTIVE', 'UNSUBSCRIBED', 'BOUNCED'].map((s) => (
              <button
                key={s}
                onClick={() => setStatusFilter(s)}
                className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-colors ${
                  statusFilter === s
                    ? 'bg-brand-100 text-brand-700 dark:bg-brand-900/30 dark:text-brand-400'
                    : 'bg-surface-secondary text-content-secondary hover:text-content-primary'
                }`}
              >
                {s || 'All'}
              </button>
            ))}
          </div>
        </div>
      </Card>

      {/* Table */}
      <Card className="!p-0 overflow-hidden">
        <Table
          columns={columns}
          data={rows}
          rowKey={(row: Subscriber) => row.id}
          emptyMessage="No subscribers found"
          loading={loading}
          selectable
          selectedRowKeys={selected}
          onSelectionChange={setSelected}
        />
        <div className="p-4 border-t border-border-default flex justify-between items-center text-sm text-content-secondary">
          <span>Total: {totalElements}</span>
          <div className="flex gap-2">
            <Button variant="secondary" size="sm" disabled={isFirstPage} onClick={() => setPage(p => p - 1)}>Prev</Button>
            <Button variant="secondary" size="sm" disabled={isLastPage} onClick={() => setPage(p => p + 1)}>Next</Button>
          </div>
        </div>
      </Card>

      <Modal open={isModalOpen} onClose={() => setIsModalOpen(false)} title={editingId ? 'Edit Subscriber' : 'Add Subscriber'}>
        <div className="space-y-4 pt-4">
          <Input label="Email *" value={formData.email} onChange={e => setFormData({...formData, email: e.target.value})} />
          <Input label="Subscriber Key (Optional)" value={formData.subscriberKey} onChange={e => setFormData({...formData, subscriberKey: e.target.value})} />
          <div className="grid grid-cols-2 gap-4">
            <Input label="First Name" value={formData.firstName} onChange={e => setFormData({...formData, firstName: e.target.value})} />
            <Input label="Last Name" value={formData.lastName} onChange={e => setFormData({...formData, lastName: e.target.value})} />
          </div>
          <Input label="Phone" value={formData.phone} onChange={e => setFormData({...formData, phone: e.target.value})} />
          <div className="flex justify-end gap-2 pt-4">
            <Button variant="secondary" onClick={() => setIsModalOpen(false)}>Cancel</Button>
            <Button onClick={handleSave} disabled={!formData.email}>Save</Button>
          </div>
        </div>
      </Modal>
      <Modal
        open={Boolean(deleteRequest)}
        onClose={() => setDeleteRequest(null)}
        title={deleteRequest?.count === 1 ? 'Delete subscriber?' : 'Delete selected subscribers?'}
        description="This action removes the selected audience record references from the workspace."
        size="sm"
        footer={(
          <>
            <Button variant="secondary" onClick={() => setDeleteRequest(null)}>Cancel</Button>
            <Button variant="danger" onClick={confirmDelete}>Delete</Button>
          </>
        )}
      >
        <p className="text-sm leading-6 text-content-secondary">
          {deleteRequest?.count === 1
            ? 'Delete this subscriber record?'
            : `Delete ${deleteRequest?.count ?? 0} selected subscriber records?`}
        </p>
      </Modal>
    </div>
  );
}

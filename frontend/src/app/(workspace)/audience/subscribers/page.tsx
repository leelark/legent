'use client';

import { useState } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { Table } from '@/components/ui/Table';
import { Modal } from '@/components/ui/Modal';
import { useApi } from '@/hooks/useApi';
import { useDebounce } from '@/hooks/useDebounce';
import { post, put, del } from '@/lib/api-client';
import { MagnifyingGlass, Plus, Trash, PencilSimple } from '@phosphor-icons/react';

const statusBadgeMap: Record<string, 'success' | 'warning' | 'danger' | 'default'> = {
  ACTIVE: 'success',
  UNSUBSCRIBED: 'warning',
  BOUNCED: 'danger',
  HELD: 'default',
  BLOCKED: 'danger',
};

export default function SubscribersPage() {
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

  const { data, loading, refetch } = useApi<any>(
    `/api/v1/subscribers?page=${page}&size=20${debouncedSearch ? `&query=${debouncedSearch}` : ''}${statusFilter ? `&status=${statusFilter}` : ''}`
  );
  const rows = data?.content ?? [];
  const totalElements = data?.totalElements ?? 0;
  const isFirstPage = data?.first ?? true;
  const isLastPage = data?.last ?? true;

  const columns = [
    {
      key: 'email', header: 'Email',
      render: (row: any) => (
        <div>
          <p className="font-medium text-content-primary">{row.email}</p>
          <p className="text-xs text-content-muted">{row.subscriberKey}</p>
        </div>
      ),
    },
    {
      key: 'name', header: 'Name',
      render: (row: any) => `${row.firstName || ''} ${row.lastName || ''}`.trim() || '—',
    },
    {
      key: 'status', header: 'Status',
      render: (row: any) => <Badge variant={statusBadgeMap[row.status] || 'default'}>{row.status}</Badge>,
    },
    {
      key: 'source', header: 'Source',
      render: (row: any) => row.source || '—',
    },
    {
      key: 'createdAt', header: 'Created',
      render: (row: any) => row.createdAt ? new Date(row.createdAt).toLocaleDateString() : '—',
    },
    {
      key: 'actions', header: '',
      render: (row: any) => (
        <div className="flex justify-end gap-2">
          <button onClick={() => openEdit(row)} className="text-content-muted hover:text-accent p-1"><PencilSimple size={16} /></button>
          <button onClick={() => handleDelete(row.id)} className="text-content-muted hover:text-danger p-1"><Trash size={16} /></button>
        </div>
      )
    }
  ];

  const handleSave = async () => {
    try {
      if (editingId) {
        await put(`/api/v1/subscribers/${editingId}`, formData);
      } else {
        await post('/api/v1/subscribers', formData);
      }
      setIsModalOpen(false);
      refetch();
    } catch (e: any) {
      setError('Failed to save subscriber');
    }
  };

  const handleDelete = async (id: string) => {
    if (confirm('Are you sure you want to delete this subscriber?')) {
      try {
        await del(`/api/v1/subscribers/${id}`);
        refetch();
      } catch (e: any) {
        setError('Failed to delete subscriber');
      }
    }
  };

  const handleBulkDelete = async () => {
    if (selected.length === 0) return;
    if (confirm(`Delete ${selected.length} subscribers?`)) {
      try {
        await Promise.all(selected.map(id => del(`/api/v1/subscribers/${id}`)));
        setSelected([]);
        refetch();
      } catch (e: any) {
        setError('Bulk delete failed');
      }
    }
  };

  const openEdit = (row: any) => {
    setEditingId(row.id);
    setFormData({
      email: row.email,
      subscriberKey: row.subscriberKey,
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
        <div className="bg-red-100 text-red-700 px-4 py-2 rounded-lg">{error}</div>
      )}
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-content-primary">Subscribers</h1>
          <p className="mt-1 text-sm text-content-secondary">Manage your subscriber database</p>
        </div>
        <div className="flex gap-2">
          <Button onClick={openNew} icon={<Plus size={16} />}>Add Subscriber</Button>
          <Button variant="danger" onClick={handleBulkDelete} disabled={selected.length === 0}>Delete Selected</Button>
        </div>
      </div>

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
          rowKey={(row: any) => row.id}
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
          <Input label="Subscriber Key *" value={formData.subscriberKey} onChange={e => setFormData({...formData, subscriberKey: e.target.value})} />
          <div className="grid grid-cols-2 gap-4">
            <Input label="First Name" value={formData.firstName} onChange={e => setFormData({...formData, firstName: e.target.value})} />
            <Input label="Last Name" value={formData.lastName} onChange={e => setFormData({...formData, lastName: e.target.value})} />
          </div>
          <Input label="Phone" value={formData.phone} onChange={e => setFormData({...formData, phone: e.target.value})} />
          <div className="flex justify-end gap-2 pt-4">
            <Button variant="secondary" onClick={() => setIsModalOpen(false)}>Cancel</Button>
            <Button onClick={handleSave} disabled={!formData.email || !formData.subscriberKey}>Save</Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}

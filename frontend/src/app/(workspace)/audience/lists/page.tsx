'use client';

import { useState } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Table } from '@/components/ui/Table';
import { Modal } from '@/components/ui/Modal';
import { Input } from '@/components/ui/Input';
import { useApi } from '@/hooks/useApi';
import { post, put, del } from '@/lib/api-client';
import { Plus, Trash, PencilSimple } from '@phosphor-icons/react';

const statusBadgeMap: Record<string, 'success' | 'default'> = {
  ACTIVE: 'success',
  ARCHIVED: 'default',
};

export default function ListsPage() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState({
    name: '',
    description: '',
    listType: 'PUBLICATION',
  });
  const [error, setError] = useState<string | null>(null);
  const { data, loading, refetch } = useApi<any>('/api/v1/lists?page=0&size=100');
  const rows = data?.content ?? [];

  const openCreate = () => {
    setEditingId(null);
    setForm({ name: '', description: '', listType: 'PUBLICATION' });
    setError(null);
    setIsModalOpen(true);
  };

  const openEdit = (row: any) => {
    setEditingId(row.id);
    setForm({ name: row.name || '', description: row.description || '', listType: row.listType || 'PUBLICATION' });
    setError(null);
    setIsModalOpen(true);
  };

  const save = async () => {
    try {
      if (editingId) {
        await put(`/api/v1/lists/${editingId}`, {
          name: form.name,
          description: form.description,
          status: 'ACTIVE',
        });
      } else {
        await post('/api/v1/lists', form);
      }
      setIsModalOpen(false);
      refetch();
    } catch {
      setError('Failed to save list');
    }
  };

  const remove = async (id: string) => {
    if (!confirm('Delete this list?')) return;
    try {
      await del(`/api/v1/lists/${id}`);
      refetch();
    } catch {
      setError('Failed to delete list');
    }
  };

  const columns = [
    { key: 'name', header: 'Name' },
    { key: 'listType', header: 'Type' },
    { key: 'memberCount', header: 'Members' },
    {
      key: 'status',
      header: 'Status',
      render: (row: any) => <Badge variant={statusBadgeMap[row.status] || 'default'}>{row.status}</Badge>,
    },
    {
      key: 'actions',
      header: '',
      render: (row: any) => (
        <div className="flex justify-end gap-2">
          <button onClick={() => openEdit(row)} className="text-content-muted hover:text-accent p-1"><PencilSimple size={16} /></button>
          <button onClick={() => remove(row.id)} className="text-content-muted hover:text-danger p-1"><Trash size={16} /></button>
        </div>
      )
    }
  ];

  return (
    <div className="space-y-6">
      {error && <div className="rounded-lg bg-red-100 px-4 py-2 text-sm text-red-700">{error}</div>}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-content-primary">Lists</h1>
          <p className="mt-1 text-sm text-content-secondary">Manage subscriber lists</p>
        </div>
        <Button icon={<Plus size={16} />} onClick={openCreate}>Create List</Button>
      </div>

      <Card className="!p-0 overflow-hidden">
        <Table
          columns={columns}
          data={rows}
          rowKey={(row: any) => row.id}
          emptyMessage="No lists yet"
          loading={loading}
        />
      </Card>

      <Modal open={isModalOpen} onClose={() => setIsModalOpen(false)} title={editingId ? 'Edit List' : 'Create List'}>
        <div className="space-y-4 pt-4">
          <Input label="Name *" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          <Input label="Description" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          {!editingId && (
            <div>
              <label className="mb-1 block text-sm font-medium text-content-primary">Type</label>
              <select
                value={form.listType}
                onChange={(e) => setForm({ ...form, listType: e.target.value })}
                className="w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary"
              >
                <option value="PUBLICATION">Publication</option>
                <option value="SUPPRESSION">Suppression</option>
                <option value="SEND">Send</option>
              </select>
            </div>
          )}
          <div className="flex justify-end gap-2 pt-4">
            <Button variant="secondary" onClick={() => setIsModalOpen(false)}>Cancel</Button>
            <Button onClick={save} disabled={!form.name}>Save</Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}

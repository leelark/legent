'use client';

import { useState } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Table } from '@/components/ui/Table';
import { Modal } from '@/components/ui/Modal';
import { Input } from '@/components/ui/Input';
import { PageHeader } from '@/components/ui/PageChrome';
import { useApi } from '@/hooks/useApi';
import { post, del } from '@/lib/api-client';
import { Plus, Trash } from '@phosphor-icons/react';

export default function DataExtensionsPage() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState<string | null>(null);
  const { data, loading, refetch } = useApi<any>('/data-extensions?page=0&size=100');
  const rows = data?.content ?? [];

  const create = async () => {
    try {
      await post('/data-extensions', { name, description });
      setName('');
      setDescription('');
      setIsModalOpen(false);
      refetch();
    } catch {
      setError('Failed to create data extension');
    }
  };

  const remove = async (id: string) => {
    if (!confirm('Delete this data extension?')) return;
    try {
      await del(`/data-extensions/${id}`);
      refetch();
    } catch {
      setError('Failed to delete data extension');
    }
  };

  const columns = [
    { key: 'name', header: 'Name' },
    { key: 'description', header: 'Description' },
    {
      key: 'sendable',
      header: 'Sendable',
      render: (row: any) => <Badge variant={row.isSendable ? 'success' : 'default'}>{row.isSendable ? 'Yes' : 'No'}</Badge>,
    },
    { key: 'recordCount', header: 'Records' },
    {
      key: 'actions',
      header: '',
      render: (row: any) => (
        <div className="flex justify-end">
          <button onClick={() => remove(row.id)} className="text-content-muted hover:text-danger p-1"><Trash size={16} /></button>
        </div>
      ),
    }
  ];

  return (
    <div className="space-y-6">
      {error && <div className="rounded-lg bg-red-100 px-4 py-2 text-sm text-red-700">{error}</div>}
      <PageHeader
        eyebrow="Custom data"
        title="Data Extensions"
        description="Maintain sendable custom tables and operational data used by segments and journeys."
        action={<Button icon={<Plus size={16} />} onClick={() => setIsModalOpen(true)}>Create Data Extension</Button>}
      />

      <Card className="!p-0 overflow-hidden">
        <Table
          columns={columns}
          data={rows}
          rowKey={(row: any) => row.id}
          emptyMessage="No data extensions yet"
          loading={loading}
        />
      </Card>

      <Modal open={isModalOpen} onClose={() => setIsModalOpen(false)} title="Create Data Extension">
        <div className="space-y-4 pt-4">
          <Input label="Name *" value={name} onChange={(e) => setName(e.target.value)} />
          <Input label="Description" value={description} onChange={(e) => setDescription(e.target.value)} />
          <div className="flex justify-end gap-2 pt-4">
            <Button variant="secondary" onClick={() => setIsModalOpen(false)}>Cancel</Button>
            <Button onClick={create} disabled={!name}>Save</Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}

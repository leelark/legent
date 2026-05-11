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

type FieldDraft = {
  fieldName: string;
  fieldType: string;
  required: boolean;
  primaryKey: boolean;
  maxLength?: number;
};

export default function DataExtensionsPage() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [sendable, setSendable] = useState(true);
  const [sendableField, setSendableField] = useState('email');
  const [primaryKeyField, setPrimaryKeyField] = useState('subscriberKey');
  const [fields, setFields] = useState<FieldDraft[]>([
    { fieldName: 'subscriberKey', fieldType: 'TEXT', required: true, primaryKey: true, maxLength: 128 },
    { fieldName: 'email', fieldType: 'EMAIL', required: true, primaryKey: false, maxLength: 320 },
  ]);
  const [error, setError] = useState<string | null>(null);
  const { data, loading, refetch } = useApi<any>('/data-extensions?page=0&size=100');
  const rows = data?.content ?? [];

  const create = async () => {
    try {
      await post('/data-extensions', {
        name,
        description,
        sendable,
        sendableField: sendable ? sendableField : undefined,
        primaryKeyField: primaryKeyField || undefined,
        fields: fields.map((field, index) => ({ ...field, ordinal: index })),
      });
      setName('');
      setDescription('');
      setSendable(true);
      setSendableField('email');
      setPrimaryKeyField('subscriberKey');
      setFields([
        { fieldName: 'subscriberKey', fieldType: 'TEXT', required: true, primaryKey: true, maxLength: 128 },
        { fieldName: 'email', fieldType: 'EMAIL', required: true, primaryKey: false, maxLength: 320 },
      ]);
      setIsModalOpen(false);
      refetch();
    } catch {
      setError('Failed to create data extension');
    }
  };

  const patchField = (index: number, patch: Partial<FieldDraft>) => {
    setFields((current) => current.map((field, itemIndex) => itemIndex === index ? { ...field, ...patch } : field));
  };

  const addField = () => {
    setFields((current) => [...current, { fieldName: `field${current.length + 1}`, fieldType: 'TEXT', required: false, primaryKey: false, maxLength: 255 }]);
  };

  const removeField = (index: number) => {
    setFields((current) => current.filter((_, itemIndex) => itemIndex !== index));
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
      render: (row: any) => <Badge variant={row.sendable ? 'success' : 'default'}>{row.sendable ? 'Yes' : 'No'}</Badge>,
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
          <div className="grid gap-3 rounded-lg border border-border-default p-3 md:grid-cols-3">
            <label className="mt-7 flex items-center gap-2 text-sm text-content-primary">
              <input type="checkbox" checked={sendable} onChange={(event) => setSendable(event.target.checked)} />
              Sendable
            </label>
            <Input label="Sendable Field" value={sendableField} onChange={(event) => setSendableField(event.target.value)} />
            <Input label="Primary Key Field" value={primaryKeyField} onChange={(event) => setPrimaryKeyField(event.target.value)} />
          </div>
          <div className="space-y-3 rounded-lg border border-border-default p-3">
            <div className="flex items-center justify-between gap-3">
              <p className="text-sm font-semibold text-content-primary">Schema Fields</p>
              <Button size="sm" variant="secondary" onClick={addField}>Add Field</Button>
            </div>
            <div className="space-y-2">
              {fields.map((field, index) => (
                <div key={`${field.fieldName}-${index}`} className="grid gap-2 rounded-lg bg-surface-secondary p-2 md:grid-cols-[1fr_130px_90px_90px_auto]">
                  <Input
                    label="Field"
                    value={field.fieldName}
                    onChange={(event) => patchField(index, { fieldName: event.target.value })}
                  />
                  <div>
                    <label className="mb-1 block text-sm font-medium text-content-primary">Type</label>
                    <select
                      value={field.fieldType}
                      onChange={(event) => patchField(index, { fieldType: event.target.value })}
                      className="w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary"
                    >
                      {['TEXT', 'EMAIL', 'NUMBER', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME', 'PHONE', 'LOCALE'].map((type) => (
                        <option key={type} value={type}>{type}</option>
                      ))}
                    </select>
                  </div>
                  <label className="mt-7 flex items-center gap-2 text-sm text-content-primary">
                    <input
                      type="checkbox"
                      checked={field.required}
                      onChange={(event) => patchField(index, { required: event.target.checked })}
                    />
                    Required
                  </label>
                  <label className="mt-7 flex items-center gap-2 text-sm text-content-primary">
                    <input
                      type="checkbox"
                      checked={field.primaryKey}
                      onChange={(event) => patchField(index, { primaryKey: event.target.checked })}
                    />
                    Key
                  </label>
                  <button
                    type="button"
                    onClick={() => removeField(index)}
                    className="mt-7 text-content-muted hover:text-danger"
                    aria-label={`Remove field ${field.fieldName}`}
                  >
                    <Trash size={16} />
                  </button>
                </div>
              ))}
            </div>
          </div>
          <div className="flex justify-end gap-2 pt-4">
            <Button variant="secondary" onClick={() => setIsModalOpen(false)}>Cancel</Button>
            <Button onClick={create} disabled={!name || fields.length === 0}>Save</Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}

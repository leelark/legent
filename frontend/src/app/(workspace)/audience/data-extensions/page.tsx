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
import { del, get, post, put } from '@/lib/api-client';
import { ClockCounterClockwise, PencilSimple, Plus, ShieldCheck, Trash } from '@phosphor-icons/react';
import type {
  DataClassification,
  DataExtension,
  DataExtensionGovernance,
  GovernanceAuditResponse,
  PagedResponse,
} from '../types';

const SOURCE_TYPES = ['MANUAL', 'IMPORT', 'QUERY', 'API', 'AUTOMATION', 'INTEGRATION'] as const;
const DATA_CLASSIFICATIONS = ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED'] as const;

type GovernanceSourceType = typeof SOURCE_TYPES[number];

type FieldDraft = {
  fieldName: string;
  fieldType: string;
  dataClassification: DataClassification;
  required: boolean;
  primaryKey: boolean;
  maxLength?: number;
};

type GovernanceFormState = {
  sourceType: GovernanceSourceType;
  sourceSystem: string;
  sourceReference: string;
  dataClassification: DataClassification;
  governanceNotes: string;
};

const defaultFields = (): FieldDraft[] => [
  {
    fieldName: 'subscriberKey',
    fieldType: 'TEXT',
    dataClassification: 'INTERNAL',
    required: true,
    primaryKey: true,
    maxLength: 128,
  },
  {
    fieldName: 'email',
    fieldType: 'EMAIL',
    dataClassification: 'CONFIDENTIAL',
    required: true,
    primaryKey: false,
    maxLength: 320,
  },
];

const defaultGovernance = (): GovernanceFormState => ({
  sourceType: 'MANUAL',
  sourceSystem: '',
  sourceReference: '',
  dataClassification: 'INTERNAL',
  governanceNotes: '',
});

function cleanText(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}

function toGovernanceForm(governance?: DataExtensionGovernance): GovernanceFormState {
  return {
    sourceType: governance?.sourceType ?? 'MANUAL',
    sourceSystem: governance?.sourceSystem ?? '',
    sourceReference: governance?.sourceReference ?? '',
    dataClassification: governance?.dataClassification ?? 'INTERNAL',
    governanceNotes: governance?.governanceNotes ?? '',
  };
}

function governancePayload(form: GovernanceFormState): DataExtensionGovernance {
  return {
    sourceType: form.sourceType,
    sourceSystem: cleanText(form.sourceSystem),
    sourceReference: cleanText(form.sourceReference),
    dataClassification: form.dataClassification,
    governanceNotes: cleanText(form.governanceNotes),
  };
}

function classificationVariant(classification?: DataClassification) {
  if (classification === 'RESTRICTED') return 'danger';
  if (classification === 'CONFIDENTIAL') return 'warning';
  if (classification === 'PUBLIC') return 'info';
  return 'default';
}

function formatDate(value?: string) {
  if (!value) return 'Not recorded';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('en', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

function SelectField<T extends string>({
  id,
  label,
  value,
  options,
  onChange,
}: {
  id: string;
  label: string;
  value: T;
  options: readonly T[];
  onChange: (value: T) => void;
}) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-medium text-content-primary">
        {label}
      </label>
      <select
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value as T)}
        className="w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30 focus:ring-offset-1"
      >
        {options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    </div>
  );
}

function TextAreaField({
  id,
  label,
  value,
  onChange,
  rows = 3,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  rows?: number;
}) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-medium text-content-primary">
        {label}
      </label>
      <textarea
        id={id}
        value={value}
        rows={rows}
        onChange={(event) => onChange(event.target.value)}
        className="w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary placeholder-content-muted focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30 focus:ring-offset-1"
      />
    </div>
  );
}

export default function DataExtensionsPage() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [sendable, setSendable] = useState(true);
  const [sendableField, setSendableField] = useState('email');
  const [primaryKeyField, setPrimaryKeyField] = useState('subscriberKey');
  const [fields, setFields] = useState<FieldDraft[]>(defaultFields());
  const [createGovernance, setCreateGovernance] = useState<GovernanceFormState>(defaultGovernance());
  const [selectedExtension, setSelectedExtension] = useState<DataExtension | null>(null);
  const [governanceForm, setGovernanceForm] = useState<GovernanceFormState>(defaultGovernance());
  const [governanceAudit, setGovernanceAudit] = useState<GovernanceAuditResponse[]>([]);
  const [governanceAuditLoading, setGovernanceAuditLoading] = useState(false);
  const [governanceAuditError, setGovernanceAuditError] = useState<string | null>(null);
  const [governanceSaving, setGovernanceSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { data, loading, refetch } = useApi<PagedResponse<DataExtension>>('/data-extensions?page=0&size=100');
  const rows = data?.content ?? data?.data ?? [];

  const resetCreateForm = () => {
    setName('');
    setDescription('');
    setSendable(true);
    setSendableField('email');
    setPrimaryKeyField('subscriberKey');
    setFields(defaultFields());
    setCreateGovernance(defaultGovernance());
  };

  const create = async () => {
    try {
      await post('/data-extensions', {
        name,
        description,
        sendable,
        sendableField: sendable ? sendableField : undefined,
        primaryKeyField: primaryKeyField || undefined,
        governance: governancePayload(createGovernance),
        fields: fields.map((field, index) => ({ ...field, ordinal: index })),
      });
      resetCreateForm();
      setIsModalOpen(false);
      refetch();
    } catch {
      setError('Failed to create data extension');
    }
  };

  const patchField = (index: number, patch: Partial<FieldDraft>) => {
    setFields((current) => current.map((field, itemIndex) => itemIndex === index ? { ...field, ...patch } : field));
  };

  const patchCreateGovernance = (patch: Partial<GovernanceFormState>) => {
    setCreateGovernance((current) => ({ ...current, ...patch }));
  };

  const patchGovernanceForm = (patch: Partial<GovernanceFormState>) => {
    setGovernanceForm((current) => ({ ...current, ...patch }));
  };

  const addField = () => {
    setFields((current) => [
      ...current,
      {
        fieldName: `field${current.length + 1}`,
        fieldType: 'TEXT',
        dataClassification: 'INTERNAL',
        required: false,
        primaryKey: false,
        maxLength: 255,
      },
    ]);
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

  const loadGovernanceAudit = async (id: string) => {
    setGovernanceAuditLoading(true);
    setGovernanceAuditError(null);
    try {
      const audit = await get<GovernanceAuditResponse[]>(`/data-extensions/${id}/governance-audit`);
      setGovernanceAudit(audit);
    } catch {
      setGovernanceAudit([]);
      setGovernanceAuditError('Failed to load governance audit');
    } finally {
      setGovernanceAuditLoading(false);
    }
  };

  const openGovernance = (row: DataExtension) => {
    setSelectedExtension(row);
    setGovernanceForm(toGovernanceForm(row.governance));
    setGovernanceAudit([]);
    void loadGovernanceAudit(row.id);
  };

  const closeGovernance = () => {
    setSelectedExtension(null);
    setGovernanceAudit([]);
    setGovernanceAuditError(null);
    setGovernanceSaving(false);
  };

  const saveGovernance = async () => {
    if (!selectedExtension) return;
    setGovernanceSaving(true);
    setError(null);
    try {
      const payload = governancePayload(governanceForm);
      const updated = await put<DataExtension>(`/data-extensions/${selectedExtension.id}/governance`, payload);
      setSelectedExtension((current) => current ? { ...current, ...updated, governance: updated.governance ?? payload } : current);
      await loadGovernanceAudit(selectedExtension.id);
      refetch();
    } catch {
      setError('Failed to update data extension governance');
    } finally {
      setGovernanceSaving(false);
    }
  };

  const columns = [
    { key: 'name', header: 'Name' },
    { key: 'description', header: 'Description' },
    {
      key: 'governance',
      header: 'Governance',
      render: (row: DataExtension) => (
        <div className="flex flex-col gap-1">
          <Badge variant={classificationVariant(row.governance?.dataClassification)}>
            {row.governance?.dataClassification ?? 'UNCLASSIFIED'}
          </Badge>
          <span className="text-xs text-content-secondary">
            {row.governance?.sourceType ?? 'Source pending'}
            {row.governance?.sourceSystem ? ` · ${row.governance.sourceSystem}` : ''}
          </span>
        </div>
      ),
    },
    {
      key: 'sendable',
      header: 'Sendable',
      render: (row: DataExtension) => <Badge variant={row.sendable ? 'success' : 'default'}>{row.sendable ? 'Yes' : 'No'}</Badge>,
    },
    { key: 'recordCount', header: 'Records' },
    {
      key: 'actions',
      header: '',
      render: (row: DataExtension) => (
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={() => openGovernance(row)}
            className="rounded-lg p-1 text-content-muted transition-colors hover:bg-surface-secondary hover:text-content-primary"
            aria-label={`Manage governance for ${row.name ?? row.id}`}
          >
            <ShieldCheck size={16} />
          </button>
          <button
            type="button"
            onClick={() => remove(row.id)}
            className="rounded-lg p-1 text-content-muted transition-colors hover:bg-surface-secondary hover:text-danger"
            aria-label={`Delete ${row.name ?? row.id}`}
          >
            <Trash size={16} />
          </button>
        </div>
      ),
    },
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
          rowKey={(row: DataExtension) => row.id}
          emptyMessage="No data extensions yet"
          loading={loading}
        />
      </Card>

      <Modal open={isModalOpen} onClose={() => setIsModalOpen(false)} title="Create Data Extension" size="xl">
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
            <div className="flex items-center gap-2 text-sm font-semibold text-content-primary">
              <ShieldCheck size={16} />
              Governance
            </div>
            <div className="grid gap-3 md:grid-cols-2">
              <SelectField
                id="create-governance-source-type"
                label="Governance source type"
                value={createGovernance.sourceType}
                options={SOURCE_TYPES}
                onChange={(sourceType) => patchCreateGovernance({ sourceType })}
              />
              <SelectField
                id="create-governance-data-classification"
                label="Data classification"
                value={createGovernance.dataClassification}
                options={DATA_CLASSIFICATIONS}
                onChange={(dataClassification) => patchCreateGovernance({ dataClassification })}
              />
              <Input
                label="Source system"
                value={createGovernance.sourceSystem}
                onChange={(event) => patchCreateGovernance({ sourceSystem: event.target.value })}
              />
              <Input
                label="Source reference"
                value={createGovernance.sourceReference}
                onChange={(event) => patchCreateGovernance({ sourceReference: event.target.value })}
              />
            </div>
            <TextAreaField
              id="create-governance-notes"
              label="Governance notes"
              value={createGovernance.governanceNotes}
              onChange={(governanceNotes) => patchCreateGovernance({ governanceNotes })}
            />
          </div>
          <div className="space-y-3 rounded-lg border border-border-default p-3">
            <div className="flex items-center justify-between gap-3">
              <p className="text-sm font-semibold text-content-primary">Schema Fields</p>
              <Button size="sm" variant="secondary" onClick={addField}>Add Field</Button>
            </div>
            <div className="space-y-2">
              {fields.map((field, index) => (
                <div key={`${field.fieldName}-${index}`} className="grid gap-2 rounded-lg bg-surface-secondary p-2 md:grid-cols-[1fr_130px_150px_90px_90px_auto]">
                  <Input
                    label="Field"
                    value={field.fieldName}
                    onChange={(event) => patchField(index, { fieldName: event.target.value })}
                  />
                  <SelectField
                    id={`field-type-${index}`}
                    label="Type"
                    value={field.fieldType}
                    options={['TEXT', 'EMAIL', 'NUMBER', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME', 'PHONE', 'LOCALE']}
                    onChange={(fieldType) => patchField(index, { fieldType })}
                  />
                  <SelectField
                    id={`field-classification-${index}`}
                    label="Classification"
                    value={field.dataClassification}
                    options={DATA_CLASSIFICATIONS}
                    onChange={(dataClassification) => patchField(index, { dataClassification })}
                  />
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

      <Modal
        open={Boolean(selectedExtension)}
        onClose={closeGovernance}
        title={selectedExtension?.name ? `Governance: ${selectedExtension.name}` : 'Governance'}
        size="xl"
      >
        {selectedExtension && (
          <div className="grid gap-5 pt-2 lg:grid-cols-[minmax(0,1fr)_320px]">
            <div className="space-y-4">
              <div className="grid gap-3 md:grid-cols-2">
                <SelectField
                  id="edit-governance-source-type"
                  label="Governance source type"
                  value={governanceForm.sourceType}
                  options={SOURCE_TYPES}
                  onChange={(sourceType) => patchGovernanceForm({ sourceType })}
                />
                <SelectField
                  id="edit-governance-data-classification"
                  label="Data classification"
                  value={governanceForm.dataClassification}
                  options={DATA_CLASSIFICATIONS}
                  onChange={(dataClassification) => patchGovernanceForm({ dataClassification })}
                />
                <Input
                  label="Source system"
                  value={governanceForm.sourceSystem}
                  onChange={(event) => patchGovernanceForm({ sourceSystem: event.target.value })}
                />
                <Input
                  label="Source reference"
                  value={governanceForm.sourceReference}
                  onChange={(event) => patchGovernanceForm({ sourceReference: event.target.value })}
                />
              </div>
              <TextAreaField
                id="edit-governance-notes"
                label="Governance notes"
                value={governanceForm.governanceNotes}
                onChange={(governanceNotes) => patchGovernanceForm({ governanceNotes })}
                rows={5}
              />
              <div className="flex justify-end gap-2">
                <Button variant="secondary" onClick={closeGovernance}>Close</Button>
                <Button icon={<PencilSimple size={16} />} onClick={saveGovernance} disabled={governanceSaving}>
                  {governanceSaving ? 'Saving' : 'Save Governance'}
                </Button>
              </div>
            </div>

            <div className="space-y-4">
              <div className="rounded-lg border border-border-default p-3">
                <div className="flex items-center justify-between gap-3">
                  <p className="text-sm font-semibold text-content-primary">Current State</p>
                  <Badge variant={classificationVariant(selectedExtension.governance?.dataClassification)}>
                    {selectedExtension.governance?.dataClassification ?? 'UNCLASSIFIED'}
                  </Badge>
                </div>
                <dl className="mt-3 space-y-2 text-sm">
                  <div className="flex justify-between gap-3">
                    <dt className="text-content-secondary">Source</dt>
                    <dd className="text-right text-content-primary">{selectedExtension.governance?.sourceType ?? 'Not recorded'}</dd>
                  </div>
                  <div className="flex justify-between gap-3">
                    <dt className="text-content-secondary">System</dt>
                    <dd className="text-right text-content-primary">{selectedExtension.governance?.sourceSystem ?? 'Not recorded'}</dd>
                  </div>
                  <div className="flex justify-between gap-3">
                    <dt className="text-content-secondary">Reference</dt>
                    <dd className="max-w-[180px] truncate text-right text-content-primary">{selectedExtension.governance?.sourceReference ?? 'Not recorded'}</dd>
                  </div>
                  <div className="flex justify-between gap-3">
                    <dt className="text-content-secondary">Reviewed</dt>
                    <dd className="text-right text-content-primary">{formatDate(selectedExtension.governance?.reviewedAt)}</dd>
                  </div>
                </dl>
              </div>

              <div className="rounded-lg border border-border-default p-3">
                <div className="flex items-center gap-2 text-sm font-semibold text-content-primary">
                  <ClockCounterClockwise size={16} />
                  Recent Audit
                </div>
                {governanceAuditLoading && <p className="mt-3 text-sm text-content-secondary">Loading audit entries</p>}
                {governanceAuditError && <p className="mt-3 text-sm text-danger">{governanceAuditError}</p>}
                {!governanceAuditLoading && !governanceAuditError && governanceAudit.length === 0 && (
                  <p className="mt-3 text-sm text-content-secondary">No governance audit entries yet</p>
                )}
                <div className="mt-3 space-y-3">
                  {governanceAudit.map((entry) => (
                    <div key={entry.id} className="rounded-lg bg-surface-secondary p-3">
                      <div className="flex items-center justify-between gap-3">
                        <p className="text-sm font-medium text-content-primary">{entry.action ?? 'Governance event'}</p>
                        <span className="text-xs text-content-muted">{formatDate(entry.createdAt)}</span>
                      </div>
                      {entry.summary && <p className="mt-1 text-sm text-content-secondary">{entry.summary}</p>}
                      {entry.createdBy && <p className="mt-2 text-xs text-content-muted">By {entry.createdBy}</p>}
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}

'use client';

import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { clsx } from 'clsx';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { SEGMENT_BUILDER_MODE_FEATURES, isModeFeatureVisible } from '@/lib/ui-mode-contract';
import { useUIStore } from '@/stores/uiStore';
import { Trash, Plus, BracketsCurly, ShieldCheck, LockKey, Warning, Eye, ClipboardText } from '@phosphor-icons/react';

interface Condition {
  id: string;
  field: string;
  op: string;
  value: string;
}

interface RuleGroup {
  id: string;
  operator: 'AND' | 'OR';
  conditions: Condition[];
}

type SegmentRuleValue = string | number | boolean | null;

export interface SegmentRuleCondition {
  field?: string;
  op?: string;
  value?: SegmentRuleValue;
}

export interface SegmentRules {
  operator?: string;
  conditions?: SegmentRuleCondition[];
  groups?: SegmentRules[];
}

type DataClassification = 'PUBLIC' | 'INTERNAL' | 'CONFIDENTIAL' | 'RESTRICTED';
type GovernanceTone = 'success' | 'warning' | 'danger' | 'info';

interface SegmentField {
  value: string;
  label: string;
  family: string;
  classification: DataClassification;
  governanceNote: string;
}

interface GovernanceWarning {
  id: string;
  title: string;
  detail: string;
  tone: GovernanceTone;
}

const FIELDS = [
  {
    value: 'email',
    label: 'Email',
    family: 'Identity',
    classification: 'CONFIDENTIAL',
    governanceNote: 'Direct contact identifier',
  },
  {
    value: 'first_name',
    label: 'First Name',
    family: 'Profile',
    classification: 'CONFIDENTIAL',
    governanceNote: 'Personal profile attribute',
  },
  {
    value: 'last_name',
    label: 'Last Name',
    family: 'Profile',
    classification: 'CONFIDENTIAL',
    governanceNote: 'Personal profile attribute',
  },
  {
    value: 'status',
    label: 'Status',
    family: 'Subscription',
    classification: 'INTERNAL',
    governanceNote: 'Consent-adjacent state',
  },
  {
    value: 'source',
    label: 'Source',
    family: 'Acquisition',
    classification: 'INTERNAL',
    governanceNote: 'Acquisition metadata',
  },
  {
    value: 'locale',
    label: 'Locale',
    family: 'Preference',
    classification: 'INTERNAL',
    governanceNote: 'Preference metadata',
  },
  {
    value: 'created_at',
    label: 'Created At',
    family: 'Lifecycle',
    classification: 'INTERNAL',
    governanceNote: 'Lifecycle timestamp',
  },
  {
    value: 'list_membership',
    label: 'List Membership',
    family: 'Membership',
    classification: 'INTERNAL',
    governanceNote: 'Indexed list relationship',
  },
] as const satisfies readonly SegmentField[];

const FIELD_BY_VALUE = Object.fromEntries(
  FIELDS.map((field) => [field.value, field])
) as Record<string, SegmentField>;

const CLASSIFICATION_WEIGHT: Record<DataClassification, number> = {
  PUBLIC: 1,
  INTERNAL: 2,
  CONFIDENTIAL: 3,
  RESTRICTED: 4,
};

const GOVERNANCE_LOCKS = [
  {
    id: 'tenant-workspace',
    title: 'Tenant/workspace scope',
    detail: 'Required for preview and evaluation',
  },
  {
    id: 'suppression-consent',
    title: 'Consent and suppression precedence',
    detail: 'Applied outside builder rules',
  },
  {
    id: 'bounded-plan',
    title: 'Bounded execution plan',
    detail: 'Compiled before count or recompute',
  },
] as const;

const DRAFT_ONLY_FAMILIES = [
  'Relationship traversal',
  'Event rollups',
  'Recursive segment nesting',
] as const;

const SCALAR_OPERATORS = [
  { value: 'EQUALS', label: 'equals' },
  { value: 'NOT_EQUALS', label: 'not equals' },
  { value: 'CONTAINS', label: 'contains' },
  { value: 'STARTS_WITH', label: 'starts with' },
  { value: 'ENDS_WITH', label: 'ends with' },
  { value: 'IS_NULL', label: 'is empty' },
  { value: 'IS_NOT_NULL', label: 'is not empty' },
];

const LIST_OPERATORS = [
  { value: 'IN_LIST', label: 'in list' },
  { value: 'NOT_IN_LIST', label: 'not in list' },
];

let _idCounter = 0;
function newId() { return `rule-${++_idCounter}`; }

function newCondition(): Condition {
  return { id: newId(), field: 'email', op: 'EQUALS', value: '' };
}

function newGroup(): RuleGroup {
  return { id: newId(), operator: 'AND', conditions: [newCondition()] };
}

function operatorsForField(field: string) {
  return field === 'list_membership' ? LIST_OPERATORS : SCALAR_OPERATORS;
}

function normalizeCondition(condition: Condition): Condition {
  const normalizedField = FIELDS.some((field) => field.value === condition.field)
    ? condition.field
    : 'email';
  const allowedOperators = operatorsForField(normalizedField);
  const normalizedOperator = allowedOperators.some((operator) => operator.value === condition.op)
    ? condition.op
    : allowedOperators[0].value;

  return {
    ...condition,
    field: normalizedField,
    op: normalizedOperator,
  };
}

interface Props {
  initialRules?: SegmentRules | null;
  onChange?: (rules: SegmentRules) => void;
}

function toBuilderGroups(rules: SegmentRules | null | undefined): RuleGroup[] {
  if (!rules || typeof rules !== 'object') {
    return [newGroup()];
  }

  const mappedGroups: SegmentRules[] = Array.isArray(rules.groups)
    ? rules.groups
    : Array.isArray(rules.conditions)
      ? [{ operator: rules.operator || 'AND', conditions: rules.conditions }]
      : [];

  if (mappedGroups.length === 0) return [newGroup()];

  return mappedGroups.map((group) => ({
    id: newId(),
    operator: group?.operator === 'OR' ? 'OR' : 'AND',
    conditions: Array.isArray(group?.conditions) && group.conditions.length > 0
      ? group.conditions.map((condition) => normalizeCondition({
          id: newId(),
          field: condition?.field || 'email',
          op: condition?.op || 'EQUALS',
          value: condition?.value == null ? '' : String(condition.value),
        }))
      : [newCondition()],
  }));
}

function toApiRules(groups: RuleGroup[]): SegmentRules {
  return {
    operator: 'AND',
    conditions: [],
    groups: groups.map((group) => ({
      operator: group.operator,
      conditions: group.conditions.map((condition) => ({
        field: condition.field,
        op: condition.op,
        value: condition.value,
      })),
      groups: [],
    })),
  };
}

function highestClassification(fields: SegmentField[]): DataClassification {
  return fields.reduce<DataClassification>((highest, field) => {
    return CLASSIFICATION_WEIGHT[field.classification] > CLASSIFICATION_WEIGHT[highest]
      ? field.classification
      : highest;
  }, 'PUBLIC');
}

function uniqueFields(conditions: Condition[]) {
  const seen = new Set<string>();
  return conditions.reduce<SegmentField[]>((fields, condition) => {
    const field = FIELD_BY_VALUE[condition.field];
    if (!field || seen.has(field.value)) {
      return fields;
    }
    seen.add(field.value);
    fields.push(field);
    return fields;
  }, []);
}

function classificationVariant(classification: DataClassification) {
  if (classification === 'RESTRICTED') return 'danger';
  if (classification === 'CONFIDENTIAL') return 'warning';
  if (classification === 'INTERNAL') return 'info';
  return 'success';
}

function buildGovernanceWarnings(conditions: Condition[], fields: SegmentField[]): GovernanceWarning[] {
  const warnings: GovernanceWarning[] = [];
  const personalFields = fields.filter((field) =>
    field.classification === 'CONFIDENTIAL' || field.classification === 'RESTRICTED'
  );
  const wildcardFields = conditions
    .filter((condition) => condition.op === 'CONTAINS' || condition.op === 'ENDS_WITH')
    .map((condition) => FIELD_BY_VALUE[condition.field]?.label)
    .filter(Boolean);
  const blankValues = conditions.filter((condition) =>
    condition.op !== 'IS_NULL' && condition.op !== 'IS_NOT_NULL' && condition.value.trim().length === 0
  );

  if (personalFields.length > 0) {
    warnings.push({
      id: 'pii-review',
      title: 'PII review',
      detail: personalFields.map((field) => field.label).join(', '),
      tone: 'warning',
    });
  }

  if (wildcardFields.length > 0) {
    warnings.push({
      id: 'wildcard-review',
      title: 'Index review',
      detail: Array.from(new Set(wildcardFields)).join(', '),
      tone: 'warning',
    });
  }

  if (blankValues.length > 0) {
    warnings.push({
      id: 'blank-value',
      title: 'Incomplete value',
      detail: `${blankValues.length} condition${blankValues.length === 1 ? '' : 's'}`,
      tone: 'danger',
    });
  }

  return warnings;
}

function GovernanceStat({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0 rounded-lg border border-border-default bg-surface-primary px-3 py-2">
      <div className="text-[11px] font-medium uppercase text-content-muted">{label}</div>
      <div className="mt-1 truncate text-sm font-semibold text-content-primary">{children}</div>
    </div>
  );
}

export function SegmentRuleBuilder({ initialRules, onChange }: Props) {
  const initialGroups = useMemo(() => toBuilderGroups(initialRules), [initialRules]);
  const [groups, setGroups] = useState<RuleGroup[]>(initialGroups);
  const uiMode = useUIStore((state) => state.uiMode);
  const showAdvancedGovernance = isModeFeatureVisible(
    SEGMENT_BUILDER_MODE_FEATURES.governanceDetail,
    uiMode
  );
  const conditions = useMemo(() => groups.flatMap((group) => group.conditions), [groups]);
  const governanceFields = useMemo(() => uniqueFields(conditions), [conditions]);
  const highestDataClass = useMemo(() => highestClassification(governanceFields), [governanceFields]);
  const governanceWarnings = useMemo(
    () => buildGovernanceWarnings(conditions, governanceFields),
    [conditions, governanceFields]
  );

  useEffect(() => {
    setGroups(initialGroups);
  }, [initialGroups]);

  const updateGroups = (updated: RuleGroup[]) => {
    setGroups(updated);
    onChange?.(toApiRules(updated));
  };

  const addCondition = (groupId: string) => {
    updateGroups(groups.map(g =>
      g.id === groupId ? { ...g, conditions: [...g.conditions, newCondition()] } : g
    ));
  };

  const removeCondition = (groupId: string, condId: string) => {
    updateGroups(groups.map(g =>
      g.id === groupId ? { ...g, conditions: g.conditions.filter(c => c.id !== condId) } : g
    ));
  };

  const updateCondition = (groupId: string, condId: string, field: keyof Condition, value: string) => {
    updateGroups(groups.map(g =>
      g.id === groupId
        ? { ...g, conditions: g.conditions.map(c => c.id === condId ? normalizeCondition({ ...c, [field]: value }) : c) }
        : g
    ));
  };

  const toggleOperator = (groupId: string) => {
    updateGroups(groups.map(g =>
      g.id === groupId ? { ...g, operator: g.operator === 'AND' ? 'OR' : 'AND' } : g
    ));
  };

  return (
    <div className="space-y-4" data-testid="segment-rule-builder">
      <div
        className="rounded-lg border border-border-default bg-surface-secondary/60 p-4"
        data-testid="segment-governance-panel"
      >
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="flex min-w-0 items-start gap-2">
            <ShieldCheck size={18} className="mt-0.5 flex-shrink-0 text-brand-600" aria-hidden="true" />
            <div className="min-w-0">
              <div className="text-sm font-semibold text-content-primary">Governance</div>
              <div className="text-xs text-content-muted">Policy state and review signals</div>
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            <Badge variant={uiMode === 'ADVANCED' ? 'brand' : 'default'}>{uiMode}</Badge>
            <Badge variant={classificationVariant(highestDataClass)}>{highestDataClass}</Badge>
            <Badge variant={governanceWarnings.length > 0 ? 'warning' : 'success'}>
              {governanceWarnings.length > 0 ? `${governanceWarnings.length} review` : 'Clear'}
            </Badge>
          </div>
        </div>

        <div className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
          <GovernanceStat label="Conditions">{conditions.length}</GovernanceStat>
          <GovernanceStat label="Fields">{governanceFields.length}</GovernanceStat>
          <GovernanceStat label="Data class">
            <span data-testid="segment-governance-classification">{highestDataClass}</span>
          </GovernanceStat>
          <GovernanceStat label="Audit state">Draft</GovernanceStat>
        </div>

        {governanceWarnings.length > 0 ? (
          <div className="mt-3 grid gap-2" data-testid="segment-governance-warnings">
            {governanceWarnings.map((warning) => (
              <div
                key={warning.id}
                className={clsx(
                  'flex min-w-0 items-start gap-2 rounded-lg border px-3 py-2 text-xs',
                  warning.tone === 'danger'
                    ? 'border-red-200 bg-red-50 text-red-700 dark:border-red-800 dark:bg-red-900/20 dark:text-red-400'
                    : 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-800 dark:bg-amber-900/20 dark:text-amber-400'
                )}
                data-testid="segment-governance-warning"
              >
                <Warning size={14} className="mt-0.5 flex-shrink-0" aria-hidden="true" />
                <div className="min-w-0">
                  <span className="font-semibold">{warning.title}</span>
                  <span className="ml-1 break-words">{warning.detail}</span>
                </div>
              </div>
            ))}
          </div>
        ) : null}

        {showAdvancedGovernance ? (
          <div
            className="mt-4 grid gap-3 lg:grid-cols-2"
            data-testid="segment-advanced-governance"
            data-mode-feature={SEGMENT_BUILDER_MODE_FEATURES.governanceDetail.id}
            data-mode-visibility={SEGMENT_BUILDER_MODE_FEATURES.governanceDetail.visibility}
          >
            <div className="rounded-lg border border-border-default bg-surface-primary p-3">
              <div className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase text-content-secondary">
                <LockKey size={14} aria-hidden="true" />
                Locks
              </div>
              <div className="space-y-2">
                {GOVERNANCE_LOCKS.map((lock) => (
                  <div key={lock.id} className="flex min-w-0 items-start gap-2 text-xs" data-testid="segment-governance-lock">
                    <Badge variant="success">Required</Badge>
                    <div className="min-w-0">
                      <div className="font-medium text-content-primary">{lock.title}</div>
                      <div className="break-words text-content-muted">{lock.detail}</div>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="rounded-lg border border-border-default bg-surface-primary p-3">
              <div className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase text-content-secondary">
                <ClipboardText size={14} aria-hidden="true" />
                Field Audit
              </div>
              <div className="grid gap-2">
                {governanceFields.map((field) => (
                  <div
                    key={field.value}
                    className="min-w-0 rounded-lg border border-border-default bg-surface-secondary px-3 py-2 text-xs text-content-secondary"
                    data-testid="segment-field-classification"
                  >
                    <div className="flex min-w-0 flex-wrap items-center gap-2">
                      <span className="font-medium text-content-primary">{field.label}</span>
                      <Badge variant={classificationVariant(field.classification)}>{field.classification}</Badge>
                      <span className="text-content-muted">{field.family}</span>
                    </div>
                    <div className="mt-1 break-words text-content-muted">{field.governanceNote}</div>
                  </div>
                ))}
              </div>
              <div
                className="mt-3 flex flex-wrap gap-2"
                data-testid="segment-draft-only-families"
                data-mode-feature={SEGMENT_BUILDER_MODE_FEATURES.unsupportedFamilies.id}
                data-mode-visibility={SEGMENT_BUILDER_MODE_FEATURES.unsupportedFamilies.visibility}
              >
                {DRAFT_ONLY_FAMILIES.map((family) => (
                  <span
                    key={family}
                    className="inline-flex items-center gap-1 rounded-full border border-amber-200 bg-amber-50 px-2 py-1 text-xs text-amber-700 dark:border-amber-800 dark:bg-amber-900/20 dark:text-amber-400"
                  >
                    <Eye size={12} aria-hidden="true" />
                    {family}: Draft only
                  </span>
                ))}
              </div>
            </div>
          </div>
        ) : null}
      </div>

      {groups.map((group, gi) => (
        <div key={group.id} className="rounded-lg border border-border-default bg-surface-secondary/50 p-4 space-y-3">
          <div className="flex items-center justify-between">
            <button
              type="button"
              onClick={() => toggleOperator(group.id)}
              className={clsx(
                'rounded-lg px-3 py-1 text-xs font-bold uppercase transition-colors',
                group.operator === 'AND'
                  ? 'bg-brand-100 text-brand-700 dark:bg-brand-900/30 dark:text-brand-400'
                  : 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400'
              )}
            >
              {group.operator}
            </button>
            <Badge variant="default">Group {gi + 1}</Badge>
          </div>

          {group.conditions.map((cond) => (
            <div
              key={cond.id}
              className="grid gap-2 animate-fade-in sm:grid-cols-[minmax(8.5rem,1fr)_minmax(8.5rem,1fr)_minmax(10rem,1.4fr)_auto]"
              data-testid="segment-rule-condition"
            >
              <select
                aria-label="Rule field"
                value={cond.field}
                onChange={(e) => updateCondition(group.id, cond.id, 'field', e.target.value)}
                className="w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary"
              >
                {FIELDS.map(f => <option key={f.value} value={f.value}>{f.label}</option>)}
              </select>

              <select
                aria-label="Rule operator"
                value={cond.op}
                onChange={(e) => updateCondition(group.id, cond.id, 'op', e.target.value)}
                className="w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary"
              >
                {operatorsForField(cond.field).map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
              </select>

              <div className="min-w-0">
                {cond.op !== 'IS_NULL' && cond.op !== 'IS_NOT_NULL' ? (
                  <input
                    aria-label="Rule value"
                    type="text"
                    value={cond.value}
                    onChange={(e) => updateCondition(group.id, cond.id, 'value', e.target.value)}
                    placeholder="Value"
                    className="w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary placeholder-content-muted focus:border-accent focus:outline-none"
                  />
                ) : (
                  <div className="rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-muted">
                    No value required
                  </div>
                )}
              </div>

              <button
                type="button"
                aria-label="Remove condition"
                onClick={() => removeCondition(group.id, cond.id)}
                className="rounded-lg p-2 text-content-muted hover:text-danger hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors sm:justify-self-end"
                disabled={group.conditions.length === 1}
              >
                <Trash size={14} />
              </button>
            </div>
          ))}

          <Button variant="ghost" size="sm" onClick={() => addCondition(group.id)} icon={<Plus size={14} />}>
            Add Condition
          </Button>
        </div>
      ))}

      <Button variant="secondary" size="sm" onClick={() => updateGroups([...groups, newGroup()])} icon={<BracketsCurly size={14} />}>
        Add Group
      </Button>
    </div>
  );
}

'use client';

import { useEffect, useMemo, useState } from 'react';
import { clsx } from 'clsx';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Trash, Plus, BracketsCurly } from '@phosphor-icons/react';

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

const FIELDS = [
  { value: 'email', label: 'Email' },
  { value: 'first_name', label: 'First Name' },
  { value: 'last_name', label: 'Last Name' },
  { value: 'status', label: 'Status' },
  { value: 'source', label: 'Source' },
  { value: 'locale', label: 'Locale' },
  { value: 'created_at', label: 'Created At' },
  { value: 'list_membership', label: 'List Membership' },
];

const OPERATORS = [
  { value: 'EQUALS', label: 'equals' },
  { value: 'NOT_EQUALS', label: 'not equals' },
  { value: 'CONTAINS', label: 'contains' },
  { value: 'STARTS_WITH', label: 'starts with' },
  { value: 'ENDS_WITH', label: 'ends with' },
  { value: 'IS_NULL', label: 'is empty' },
  { value: 'IS_NOT_NULL', label: 'is not empty' },
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

interface Props {
  initialRules?: any;
  onChange?: (rules: any) => void;
}

function toBuilderGroups(rules: any): RuleGroup[] {
  if (!rules || typeof rules !== 'object') {
    return [newGroup()];
  }

  const mappedGroups = Array.isArray(rules.groups)
    ? rules.groups
    : Array.isArray(rules.conditions)
      ? [{ operator: rules.operator || 'AND', conditions: rules.conditions }]
      : [];

  if (mappedGroups.length === 0) return [newGroup()];

  return mappedGroups.map((group: any) => ({
    id: newId(),
    operator: group?.operator === 'OR' ? 'OR' : 'AND',
    conditions: Array.isArray(group?.conditions) && group.conditions.length > 0
      ? group.conditions.map((condition: any) => ({
          id: newId(),
          field: condition?.field || 'email',
          op: condition?.op || 'EQUALS',
          value: condition?.value == null ? '' : String(condition.value),
        }))
      : [newCondition()],
  }));
}

function toApiRules(groups: RuleGroup[]) {
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

export function SegmentRuleBuilder({ initialRules, onChange }: Props) {
  const initialGroups = useMemo(() => toBuilderGroups(initialRules), [initialRules]);
  const [groups, setGroups] = useState<RuleGroup[]>(initialGroups);

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

  const updateCondition = (groupId: string, condId: string, field: string, value: any) => {
    updateGroups(groups.map(g =>
      g.id === groupId
        ? { ...g, conditions: g.conditions.map(c => c.id === condId ? { ...c, [field]: value } : c) }
        : g
    ));
  };

  const toggleOperator = (groupId: string) => {
    updateGroups(groups.map(g =>
      g.id === groupId ? { ...g, operator: g.operator === 'AND' ? 'OR' : 'AND' } : g
    ));
  };

  return (
    <div className="space-y-4">
      {groups.map((group, gi) => (
        <div key={group.id} className="rounded-xl border border-border-default bg-surface-secondary/50 p-4 space-y-3">
          <div className="flex items-center justify-between">
            <button
              onClick={() => toggleOperator(group.id)}
              className={clsx(
                'rounded-lg px-3 py-1 text-xs font-bold uppercase tracking-wider transition-colors',
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
            <div key={cond.id} className="flex items-center gap-2 animate-fade-in">
              <select
                value={cond.field}
                onChange={(e) => updateCondition(group.id, cond.id, 'field', e.target.value)}
                className="rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary"
              >
                {FIELDS.map(f => <option key={f.value} value={f.value}>{f.label}</option>)}
              </select>

              <select
                value={cond.op}
                onChange={(e) => updateCondition(group.id, cond.id, 'op', e.target.value)}
                className="rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary"
              >
                {OPERATORS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
              </select>

              {cond.op !== 'IS_NULL' && cond.op !== 'IS_NOT_NULL' && (
                <input
                  type="text"
                  value={cond.value}
                  onChange={(e) => updateCondition(group.id, cond.id, 'value', e.target.value)}
                  placeholder="Value..."
                  className="flex-1 rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm text-content-primary placeholder-content-muted focus:border-accent focus:outline-none"
                />
              )}

              <button
                onClick={() => removeCondition(group.id, cond.id)}
                className="rounded-lg p-2 text-content-muted hover:text-danger hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
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

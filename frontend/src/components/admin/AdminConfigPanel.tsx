"use client";

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  applyAdminSetting,
  getAdminSettings,
  getSettingHistory,
  getSettingImpact,
  resetAdminSetting,
  validateAdminSetting,
  type AdminConfig,
} from '@/lib/admin-api';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';

const CATEGORIES = [
  { label: 'Delivery', value: 'DELIVERY', module: 'delivery' },
  { label: 'Campaign', value: 'CAMPAIGN', module: 'campaign' },
  { label: 'Automation', value: 'AUTOMATION', module: 'automation' },
  { label: 'Audience', value: 'AUDIENCE', module: 'audience' },
  { label: 'Template', value: 'TEMPLATE', module: 'template' },
  { label: 'Analytics', value: 'ANALYTICS', module: 'analytics' },
  { label: 'Security', value: 'SECURITY', module: 'security' },
  { label: 'System', value: 'SYSTEM', module: 'system' },
];

type DraftSetting = {
  key: string;
  value: string;
  module: string;
  category: string;
  type: string;
  scope: string;
};

const DEFAULT_DRAFT: DraftSetting = {
  key: '',
  value: '',
  module: 'system',
  category: 'SYSTEM',
  type: 'STRING',
  scope: 'WORKSPACE',
};

export const AdminConfigPanel: React.FC = () => {
  const [selectedCategory, setSelectedCategory] = useState('SYSTEM');
  const [configs, setConfigs] = useState<AdminConfig[]>([]);
  const [history, setHistory] = useState<Array<Record<string, unknown>>>([]);
  const [draft, setDraft] = useState<DraftSetting>(DEFAULT_DRAFT);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [validationErrors, setValidationErrors] = useState<string[]>([]);
  const [impactNotice, setImpactNotice] = useState<string[]>([]);
  const [historyKey, setHistoryKey] = useState<string>('');

  const selectedMeta = useMemo(
    () => CATEGORIES.find((item) => item.value === selectedCategory) ?? CATEGORIES[CATEGORIES.length - 1],
    [selectedCategory]
  );

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getAdminSettings({
        category: selectedCategory,
        module: selectedMeta.module,
      });
      setConfigs(Array.isArray(data) ? data : []);
    } catch (err: any) {
      setError(err?.normalized?.message || err?.response?.data?.error?.message || 'Failed to load settings');
      setConfigs([]);
    } finally {
      setLoading(false);
    }
  }, [selectedCategory, selectedMeta.module]);

  useEffect(() => {
    load();
  }, [load]);

  const handleValidate = async () => {
    setValidationErrors([]);
    setImpactNotice([]);
    try {
      const validation = await validateAdminSetting(draft);
      if (!validation.valid) {
        setValidationErrors(validation.errors || ['Validation failed']);
        return;
      }
      const impact = await getSettingImpact({
        key: draft.key,
        module: draft.module,
        scope: draft.scope,
      });
      setImpactNotice([
        ...(impact.impactedModules || []).map((module) => `Impacts: ${module}`),
        ...(impact.notices || []),
      ]);
    } catch (err: any) {
      setValidationErrors([err?.normalized?.message || 'Validation request failed']);
    }
  };

  const handleSave = async () => {
    if (!draft.key.trim() || draft.value === '') {
      setValidationErrors(['Key and value required']);
      return;
    }
    setSaving(true);
    try {
      const validation = await validateAdminSetting(draft);
      if (!validation.valid) {
        setValidationErrors(validation.errors || ['Validation failed']);
        return;
      }
      await applyAdminSetting(draft);
      setDraft({
        ...DEFAULT_DRAFT,
        module: selectedMeta.module,
        category: selectedMeta.value,
      });
      setValidationErrors([]);
      await load();
    } catch (err: any) {
      setError(err?.normalized?.message || 'Failed to apply setting');
    } finally {
      setSaving(false);
    }
  };

  const handleReset = async (key: string, scope?: string) => {
    try {
      await resetAdminSetting({ key, scope });
      await load();
    } catch (err: any) {
      setError(err?.normalized?.message || 'Failed to reset setting');
    }
  };

  const loadHistory = async () => {
    try {
      const rows = await getSettingHistory(historyKey.trim() || undefined);
      setHistory(Array.isArray(rows) ? rows : []);
    } catch (err: any) {
      setError(err?.normalized?.message || 'Failed to load setting history');
      setHistory([]);
    }
  };

  return (
    <Card className="p-5 space-y-5">
      <div className="flex flex-wrap gap-2">
        {CATEGORIES.map((category) => (
          <Button
            key={category.value}
            size="sm"
            variant={selectedCategory === category.value ? 'primary' : 'secondary'}
            onClick={() => {
              setSelectedCategory(category.value);
              setDraft({
                ...draft,
                module: category.module,
                category: category.value,
              });
            }}
          >
            {category.label}
          </Button>
        ))}
      </div>

      {error && <p className="text-sm text-danger">{error}</p>}

      <div className="grid grid-cols-1 md:grid-cols-6 gap-2">
        <input
          value={draft.key}
          onChange={(e) => setDraft((prev) => ({ ...prev, key: e.target.value }))}
          placeholder="setting.key"
          className="border rounded px-2 py-1 text-sm md:col-span-2"
        />
        <input
          value={draft.value}
          onChange={(e) => setDraft((prev) => ({ ...prev, value: e.target.value }))}
          placeholder="value"
          className="border rounded px-2 py-1 text-sm md:col-span-2"
        />
        <select
          value={draft.type}
          onChange={(e) => setDraft((prev) => ({ ...prev, type: e.target.value }))}
          className="border rounded px-2 py-1 text-sm"
        >
          <option value="STRING">STRING</option>
          <option value="INTEGER">INTEGER</option>
          <option value="BOOLEAN">BOOLEAN</option>
          <option value="JSON">JSON</option>
          <option value="DECIMAL">DECIMAL</option>
        </select>
        <select
          value={draft.scope}
          onChange={(e) => setDraft((prev) => ({ ...prev, scope: e.target.value }))}
          className="border rounded px-2 py-1 text-sm"
        >
          <option value="WORKSPACE">WORKSPACE</option>
          <option value="TENANT">TENANT</option>
          <option value="ENVIRONMENT">ENVIRONMENT</option>
          <option value="GLOBAL">GLOBAL</option>
        </select>
      </div>

      <div className="flex flex-wrap gap-2">
        <Button onClick={handleValidate} variant="secondary">Validate + Impact</Button>
        <Button onClick={handleSave} loading={saving}>Apply Setting</Button>
      </div>

      {validationErrors.length > 0 && (
        <div className="rounded border border-danger/30 bg-danger/10 p-3 text-xs text-danger">
          {validationErrors.map((item, idx) => (
            <p key={`${item}-${idx}`}>{item}</p>
          ))}
        </div>
      )}

      {impactNotice.length > 0 && (
        <div className="rounded border border-brand-300 bg-brand-50 p-3 text-xs text-brand-700">
          {impactNotice.map((item, idx) => (
            <p key={`${item}-${idx}`}>{item}</p>
          ))}
        </div>
      )}

      {loading ? (
        <p className="text-sm text-content-muted">Loading settings...</p>
      ) : (
        <table className="w-full text-sm">
          <thead className="text-left">
            <tr>
              <th className="pb-2">Key</th>
              <th className="pb-2">Value</th>
              <th className="pb-2">Type</th>
              <th className="pb-2">Scope</th>
              <th className="pb-2">Updated</th>
              <th className="pb-2 text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {configs.map((config) => (
              <tr key={`${config.key}-${config.scope}-${config.workspaceId || 'global'}`} className="border-t border-border-default">
                <td className="py-2 font-medium">{config.key}</td>
                <td className="py-2">{config.value}</td>
                <td className="py-2">{config.configType || 'STRING'}</td>
                <td className="py-2">{config.scope || 'TENANT'}</td>
                <td className="py-2 text-xs text-content-muted">{config.updatedAt ? new Date(config.updatedAt).toLocaleString() : '-'}</td>
                <td className="py-2 text-right">
                  <Button size="sm" variant="outline" onClick={() => handleReset(config.key, config.scope)}>
                    Reset
                  </Button>
                </td>
              </tr>
            ))}
            {configs.length === 0 && (
              <tr>
                <td className="py-4 text-content-muted" colSpan={6}>
                  No settings found for {selectedMeta.label}.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      )}

      <div className="border-t border-border-default pt-4 space-y-2">
        <h4 className="font-semibold text-sm">Version History</h4>
        <div className="flex gap-2">
          <input
            value={historyKey}
            onChange={(e) => setHistoryKey(e.target.value)}
            placeholder="Optional setting key"
            className="border rounded px-2 py-1 text-sm"
          />
          <Button size="sm" variant="secondary" onClick={loadHistory}>Load History</Button>
        </div>
        <div className="max-h-48 overflow-auto rounded bg-surface-secondary p-2 text-xs">
          {history.length > 0 ? (
            <pre>{JSON.stringify(history, null, 2)}</pre>
          ) : (
            <p className="text-content-muted">No history loaded.</p>
          )}
        </div>
      </div>
    </Card>
  );
};

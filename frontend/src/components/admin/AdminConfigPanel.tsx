"use client";
import React, { useEffect, useState } from 'react';
import { getAdminConfigs, saveAdminConfig, type AdminConfig } from '@/lib/admin-api';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';

export const AdminConfigPanel: React.FC = () => {
  const [configs, setConfigs] = useState<AdminConfig[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [newConfig, setNewConfig] = useState<AdminConfig>({ key: '', value: '', description: '', category: '', configType: 'STRING', editable: true });
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    load();
  }, []);

  const load = async () => {
    setLoading(true);
    try {
      const data = await getAdminConfigs();
      setConfigs(Array.isArray(data) ? data : []);
      setError(null);
    } catch (e: any) {
      setError(e?.response?.data?.error?.message || 'Failed to load configs');
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    if (!newConfig.key.trim() || !newConfig.value.trim()) return;
    setSaving(true);
    try {
      await saveAdminConfig(newConfig);
      setNewConfig({ key: '', value: '', description: '', category: '', configType: 'STRING', editable: true });
      await load();
    } catch (e: any) {
      setError(e?.response?.data?.error?.message || 'Failed to save config');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card className="p-4">
      <h3 className="font-bold mb-2">Admin Configs</h3>
      {error && <p className="text-sm text-danger mb-2">{error}</p>}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-2 mb-4">
        <input value={newConfig.key} onChange={e => setNewConfig({ ...newConfig, key: e.target.value })} placeholder="Key" className="border rounded px-2 py-1 text-sm" />
        <input value={newConfig.value} onChange={e => setNewConfig({ ...newConfig, value: e.target.value })} placeholder="Value" className="border rounded px-2 py-1 text-sm" />
        <input value={newConfig.category || ''} onChange={e => setNewConfig({ ...newConfig, category: e.target.value })} placeholder="Category" className="border rounded px-2 py-1 text-sm" />
        <input value={newConfig.description || ''} onChange={e => setNewConfig({ ...newConfig, description: e.target.value })} placeholder="Description" className="border rounded px-2 py-1 text-sm md:col-span-2" />
        <select value={newConfig.configType || 'STRING'} onChange={e => setNewConfig({ ...newConfig, configType: e.target.value })} className="border rounded px-2 py-1 text-sm">
          <option value="STRING">STRING</option>
          <option value="NUMBER">NUMBER</option>
          <option value="BOOLEAN">BOOLEAN</option>
          <option value="JSON">JSON</option>
        </select>
      </div>
      <div className="mb-4">
        <Button onClick={handleSave} disabled={saving || !newConfig.key || !newConfig.value}>{saving ? 'Saving...' : 'Save Config'}</Button>
      </div>
      {loading ? (
        <p className="text-sm text-content-muted">Loading configs...</p>
      ) : (
        <table className="w-full text-sm">
          <thead className="text-left">
            <tr>
              <th className="pb-2">Key</th>
              <th className="pb-2">Value</th>
              <th className="pb-2">Category</th>
              <th className="pb-2">Type</th>
              <th className="pb-2">Description</th>
            </tr>
          </thead>
          <tbody>
            {configs.map((c) => (
              <tr key={c.id || c.key} className="border-t border-border-default">
                <td className="py-2 font-medium">{c.key}</td>
                <td className="py-2">{c.value}</td>
                <td className="py-2">{c.category || '-'}</td>
                <td className="py-2">{c.configType || 'STRING'}</td>
                <td className="py-2">{c.description || '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Card>
  );
};

"use client";
import React, { useEffect, useState } from 'react';
import { getAdminConfigs, saveAdminConfig, type AdminConfig } from '@/lib/admin-api';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';

export const AdminConfigPanel: React.FC = () => {
  const [configs, setConfigs] = useState<AdminConfig[]>([]);
  const [key, setKey] = useState('');
  const [value, setValue] = useState('');
  useEffect(() => { getAdminConfigs().then(setConfigs); }, []);
  const handleSave = async () => {
    await saveAdminConfig({ key, value });
    setKey(''); setValue('');
    getAdminConfigs().then(setConfigs);
  };
  return (
    <Card className="p-4">
      <h3 className="font-bold mb-2">Admin Configs</h3>
      <div className="flex gap-2 mb-4">
        <input value={key} onChange={e => setKey(e.target.value)} placeholder="Key" className="border rounded px-2 py-1" />
        <input value={value} onChange={e => setValue(e.target.value)} placeholder="Value" className="border rounded px-2 py-1" />
        <Button onClick={handleSave} disabled={!key || !value}>Save</Button>
      </div>
      <table className="w-full text-sm">
        <thead><tr><th>Key</th><th>Value</th></tr></thead>
        <tbody>
          {configs.map((c) => <tr key={c.key}><td>{c.key}</td><td>{c.value}</td></tr>)}
        </tbody>
      </table>
    </Card>
  );
};

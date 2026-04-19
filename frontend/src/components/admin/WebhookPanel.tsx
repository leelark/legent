import React, { useEffect, useState } from 'react';
import { getWebhooks, saveWebhook } from '@/lib/admin-api';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';

export const WebhookPanel: React.FC = () => {
  const [webhooks, setWebhooks] = useState<any[]>([]);
  const [wh, setWh] = useState<any>({ name: '', url: '', eventType: '' });
  useEffect(() => { getWebhooks().then(setWebhooks); }, []);
  const handleSave = async () => {
    await saveWebhook(wh);
    setWh({ name: '', url: '', eventType: '' });
    getWebhooks().then(setWebhooks);
  };
  return (
    <Card className="p-4">
      <h3 className="font-bold mb-2">Webhook Integrations</h3>
      <div className="flex gap-2 mb-4">
        <input value={wh.name} onChange={e => setWh({ ...wh, name: e.target.value })} placeholder="Name" className="border rounded px-2 py-1" />
        <input value={wh.url} onChange={e => setWh({ ...wh, url: e.target.value })} placeholder="URL" className="border rounded px-2 py-1" />
        <input value={wh.eventType} onChange={e => setWh({ ...wh, eventType: e.target.value })} placeholder="Event Type" className="border rounded px-2 py-1" />
        <Button onClick={handleSave} disabled={!wh.name || !wh.url || !wh.eventType}>Save</Button>
      </div>
      <table className="w-full text-sm">
        <thead><tr><th>Name</th><th>URL</th><th>Event Type</th></tr></thead>
        <tbody>
          {webhooks.map((w) => <tr key={w.id}><td>{w.name}</td><td>{w.url}</td><td>{w.eventType}</td></tr>)}
        </tbody>
      </table>
    </Card>
  );
};

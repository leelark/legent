'use client';
import React, { useCallback, useEffect, useState } from 'react';
import { getWebhooks, saveWebhook, type WebhookIntegration } from '@/lib/admin-api';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useToast } from '@/components/ui/Toast';
import { AdminEmptyState, AdminPanel, AdminSkeletonRows, AdminTableShell, StatusPill } from '@/components/admin/AdminChrome';

function isValidWebhookUrl(value: string) {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' || (url.protocol === 'http:' && url.hostname === 'localhost');
  } catch {
    return false;
  }
}

export const WebhookPanel: React.FC = () => {
  const [webhooks, setWebhooks] = useState<WebhookIntegration[]>([]);
  const [wh, setWh] = useState<WebhookIntegration>({ name: '', url: '', eventType: '', isActive: true });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { addToast } = useToast();

  const urlInvalid = wh.url.length > 0 && !isValidWebhookUrl(wh.url);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setWebhooks(await getWebhooks());
    } catch (err: any) {
      setError(err?.normalized?.message || 'Failed to load webhooks.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleSave = async () => {
    if (!wh.name.trim() || !wh.url.trim() || !wh.eventType.trim() || urlInvalid) {
      setError('Name, secure URL, and at least one event are required.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await saveWebhook({
        ...wh,
        name: wh.name.trim(),
        url: wh.url.trim(),
        eventType: wh.eventType.trim(),
      });
      setWh({ name: '', url: '', eventType: '', isActive: true });
      await load();
      addToast({ type: 'success', title: 'Webhook saved', message: 'Integration endpoint is ready to receive subscribed events.' });
    } catch (err: any) {
      const message = err?.normalized?.message || err?.message || 'Failed to save webhook.';
      setError(message);
      addToast({ type: 'error', title: 'Webhook save failed', message });
    } finally {
      setSaving(false);
    }
  };
  return (
    <AdminPanel
      title="Webhook Integrations"
      subtitle="Configure outbound runtime events with URL validation, event chips, and active state."
      action={<Button size="sm" variant="secondary" onClick={load} loading={loading}>Refresh</Button>}
    >
      <div className="space-y-5">
        {error ? <div className="rounded-xl border border-danger/30 bg-danger/10 px-4 py-3 text-sm text-danger">{error}</div> : null}

        <div className="rounded-xl border border-border-default bg-surface-secondary p-4">
          <div className="grid gap-3 xl:grid-cols-[1fr_1.4fr_1.2fr_auto]">
            <Input value={wh.name} onChange={(e) => setWh({ ...wh, name: e.target.value })} label="Name" placeholder="Delivery events" />
            <Input value={wh.url} onChange={(e) => setWh({ ...wh, url: e.target.value })} label="Endpoint URL" placeholder="https://example.com/webhooks/legent" error={urlInvalid ? 'Use HTTPS, or localhost for local testing' : undefined} />
            <Input value={wh.eventType} onChange={(e) => setWh({ ...wh, eventType: e.target.value })} label="Events" placeholder="campaign.sent, delivery.failed" hint="Comma-separated event names" />
            <div className="flex items-end">
              <Button className="w-full" onClick={handleSave} loading={saving} disabled={!wh.name || !wh.url || !wh.eventType || urlInvalid}>Save</Button>
            </div>
          </div>
        </div>

        {loading ? (
          <AdminSkeletonRows rows={4} />
        ) : webhooks.length === 0 ? (
          <AdminEmptyState title="No webhooks configured" description="Create a webhook to deliver campaign, delivery, automation, or audit events to another system." />
        ) : (
          <AdminTableShell>
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead className="bg-surface-secondary text-xs uppercase tracking-wide text-content-muted">
                <tr>
                  <th className="px-4 py-3">Name</th>
                  <th className="px-4 py-3">Endpoint</th>
                  <th className="px-4 py-3">Events</th>
                  <th className="px-4 py-3">State</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border-default">
                {webhooks.map((w) => (
                  <tr key={w.id || `${w.name}-${w.url}`} className="transition-colors hover:bg-surface-secondary/70">
                    <td className="px-4 py-3 font-semibold text-content-primary">{w.name}</td>
                    <td className="max-w-xs truncate px-4 py-3 text-content-secondary">{w.url}</td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-1.5">
                        {w.eventType.split(',').map((event) => event.trim()).filter(Boolean).map((event) => (
                          <span key={event} className="rounded-full border border-border-default bg-surface-secondary px-2 py-0.5 text-xs text-content-secondary">{event}</span>
                        ))}
                      </div>
                    </td>
                    <td className="px-4 py-3"><StatusPill status={w.isActive === false ? 'INACTIVE' : 'ACTIVE'} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </AdminTableShell>
        )}
      </div>
    </AdminPanel>
  );
};

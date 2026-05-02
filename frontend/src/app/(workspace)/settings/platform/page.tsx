'use client';

import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/Card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Link, Webhook, Key, CheckCircle, XCircle, Search, Save, Loader2 } from 'lucide-react';
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { get, post } from '@/lib/api-client';

interface WebhookConfig {
  id: string;
  url: string;
  events: string;
  isActive: boolean;
}

interface PlatformWebhookConfig {
  id: string;
  endpointUrl: string;
  eventsSubscribed: string;
  isActive: boolean;
}

interface TenantConfig {
  tenantId: string;
  themeColor: string;
  displayName: string;
  logoUrl?: string;
}

export default function PlatformSettings() {
  const [webhooks, setWebhooks] = useState<WebhookConfig[]>([]);
  const [config, setConfig] = useState<TenantConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const parseEvents = (eventsSubscribed: string) => {
      try {
        const parsed = JSON.parse(eventsSubscribed);
        return Array.isArray(parsed) ? parsed.join(', ') : String(parsed);
      } catch {
        return eventsSubscribed || '';
      }
    };

    const fetchData = async () => {
      setLoading(true);
      try {
        const [webhookRes, configRes] = await Promise.all([
          get<PlatformWebhookConfig[]>('/platform/webhooks'),
          get<TenantConfig>('/platform/config')
        ]);
        setWebhooks((webhookRes || []).map((hook) => ({
          id: hook.id,
          url: hook.endpointUrl,
          events: parseEvents(hook.eventsSubscribed),
          isActive: hook.isActive,
        })));
        setConfig(configRes || { tenantId: '', themeColor: '#4F46E5', displayName: 'Legent Studio' });
      } catch (err) {
        console.error('Failed to load platform settings', err);
        setError('Failed to load settings');
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  const handleSaveConfig = async () => {
    if (!config) return;
    setSaving(true);
    try {
      await post('/platform/config', config);
    } catch (err) {
      console.error('Failed to save config', err);
      alert('Failed to save configuration');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <Loader2 className="w-8 h-8 animate-spin text-brand-500" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-3xl font-bold tracking-tight">Platform Integrations</h2>
          <p className="text-muted-foreground mt-1">Manage API Keys, Webhooks, and Global Branding Settings</p>
        </div>
        <Button onClick={handleSaveConfig} loading={saving} icon={<Save className="w-4 h-4" />}>
          Save All Changes
        </Button>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
                <div>
                    <CardTitle className="text-lg">Global Webhooks</CardTitle>
                    <CardDescription>Real-time HTTP push notifications</CardDescription>
                </div>
                <Button variant="outline" size="sm"><Webhook className="w-4 h-4 mr-2"/> Add Webhook</Button>
            </div>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Endpoint</TableHead>
                  <TableHead>Events</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {webhooks.length > 0 ? webhooks.map((hook) => (
                  <TableRow key={hook.id}>
                    <TableCell className="font-medium text-xs text-muted-foreground break-all max-w-[200px]">
                      {hook.url}
                    </TableCell>
                    <TableCell className="text-xs">
                      <span className="bg-slate-100 text-slate-800 px-2 py-1 rounded">{hook.events}</span>
                    </TableCell>
                    <TableCell>
                      {hook.isActive ? <CheckCircle className="w-4 h-4 text-green-500" /> : <XCircle className="w-4 h-4 text-red-500" />}
                    </TableCell>
                  </TableRow>
                )) : (
                  <TableRow>
                    <TableCell colSpan={3} className="text-center py-4 text-sm text-content-muted">No webhooks configured</TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
             <div className="flex items-center justify-between">
                <div>
                    <CardTitle className="text-lg">API Keys</CardTitle>
                    <CardDescription>Secure tokens for external REST integration</CardDescription>
                </div>
                <Button variant="outline" size="sm"><Key className="w-4 h-4 mr-2"/> Generate Key</Button>
            </div>
          </CardHeader>
          <CardContent>
             <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Description</TableHead>
                  <TableHead>Created</TableHead>
                  <TableHead>Last Used</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow>
                  <TableCell className="font-medium">Zapier Sync Integration</TableCell>
                  <TableCell className="text-slate-500 text-sm">Oct 12, 2026</TableCell>
                  <TableCell className="text-slate-500 text-sm">2 mins ago</TableCell>
                </TableRow>
                 <TableRow>
                  <TableCell className="font-medium">Internal Dashboard App</TableCell>
                  <TableCell className="text-slate-500 text-sm">Aug 01, 2026</TableCell>
                  <TableCell className="text-slate-500 text-sm">Yesterday</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>

       <Card>
          <CardHeader>
            <CardTitle>Branding & Visuals</CardTitle>
            <CardDescription>
              Customize the look and feel of the Legent Studio for your tenant&apos;s users.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
              <div className="grid w-full max-w-sm items-center gap-1.5">
                <label htmlFor="displayName" className="text-sm font-medium leading-none">Display Name</label>
                <Input 
                  type="text" 
                  id="displayName" 
                  value={config?.displayName || ''} 
                  onChange={(e) => setConfig(prev => prev ? { ...prev, displayName: e.target.value } : null)} 
                />
              </div>
              <div className="grid w-full max-w-sm items-center gap-1.5">
                <label htmlFor="theme" className="text-sm font-medium leading-none">Primary Theme Color (Hex)</label>
                <div className="flex items-center space-x-2">
                    <div className="w-8 h-8 rounded border shadow-inner" style={{ backgroundColor: config?.themeColor || '#4F46E5' }}></div>
                    <Input 
                      type="text" 
                      id="theme" 
                      value={config?.themeColor || ''} 
                      onChange={(e) => setConfig(prev => prev ? { ...prev, themeColor: e.target.value } : null)} 
                    />
                </div>
              </div>
          </CardContent>
        </Card>
    </div>
  );
}

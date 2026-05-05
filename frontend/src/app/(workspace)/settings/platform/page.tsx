'use client';

import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/Card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Webhook, CheckCircle, XCircle, Save, Loader2, Plus, RefreshCcw, Trash2, ShieldCheck, ShieldAlert } from 'lucide-react';
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { get, post } from '@/lib/api-client';
import { createProvider, deleteProvider, listProviderHealth, listProviders, testProvider, updateProvider, type Provider, type ProviderCreateRequest } from '@/lib/providers-api';

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
  timezone: string;
  logoUrl?: string;
}

const PROVIDER_TYPES = [
  "SMTP",
  "AWS_SES",
  "SENDGRID",
  "MAILGUN",
  "BREVO",
  "POSTMARK",
  "SPARKPOST",
  "POSTAL",
  "HARAKA",
  "POSTFIX",
  "MAILHOG",
  "MAILPIT",
  "DOCKER_MAIL_SERVER",
  "CUSTOM_SMTP",
  "MOCK"
];

export default function PlatformSettings() {
  const [webhooks, setWebhooks] = useState<WebhookConfig[]>([]);
  const [config, setConfig] = useState<TenantConfig | null>(null);
  const [providers, setProviders] = useState<Provider[]>([]);
  const [providerHealth, setProviderHealth] = useState<Record<string, string>>({});
  const [creatingProvider, setCreatingProvider] = useState(false);
  const [providerActionId, setProviderActionId] = useState<string | null>(null);
  const [newProvider, setNewProvider] = useState<ProviderCreateRequest>({
    name: '',
    type: 'SMTP',
    host: '',
    port: 587,
    username: '',
    password: '',
    isActive: true,
    priority: 1,
    maxSendRate: 100,
    healthCheckEnabled: true,
    healthCheckIntervalSeconds: 60
  });
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
        const [webhookRes, configRes, providerRes, healthRes] = await Promise.all([
          get<PlatformWebhookConfig[]>('/platform/webhooks'),
          get<TenantConfig>('/platform/config'),
          listProviders(true),
          listProviderHealth()
        ]);
        setWebhooks((webhookRes || []).map((hook) => ({
          id: hook.id,
          url: hook.endpointUrl,
          events: parseEvents(hook.eventsSubscribed),
          isActive: hook.isActive,
        })));
        setConfig(configRes || { tenantId: '', themeColor: '#4F46E5', timezone: 'UTC' });
        setProviders(providerRes || []);
        setProviderHealth(Object.fromEntries((healthRes || []).map((row) => [row.id, row.healthStatus || 'UNKNOWN'])));
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

  const refreshProviders = async () => {
    const [providerRes, healthRes] = await Promise.all([listProviders(true), listProviderHealth()]);
    setProviders(providerRes || []);
    setProviderHealth(Object.fromEntries((healthRes || []).map((row) => [row.id, row.healthStatus || 'UNKNOWN'])));
  };

  const handleCreateProvider = async () => {
    if (!newProvider.name?.trim()) {
      return;
    }
    setCreatingProvider(true);
    try {
      await createProvider(newProvider);
      setNewProvider({
        ...newProvider,
        name: '',
        host: '',
        username: '',
        password: ''
      });
      await refreshProviders();
    } finally {
      setCreatingProvider(false);
    }
  };

  const handleToggleProvider = async (provider: Provider) => {
    setProviderActionId(provider.id);
    try {
      await updateProvider(provider.id, {
        name: provider.name,
        type: provider.type,
        host: provider.host,
        port: provider.port,
        username: provider.username,
        isActive: !provider.isActive,
        priority: provider.priority,
        maxSendRate: provider.maxSendRate,
        healthCheckEnabled: provider.healthCheckEnabled,
        healthCheckUrl: provider.healthCheckUrl,
        healthCheckIntervalSeconds: provider.healthCheckIntervalSeconds
      });
      await refreshProviders();
    } finally {
      setProviderActionId(null);
    }
  };

  const handleTestProvider = async (id: string) => {
    setProviderActionId(id);
    try {
      await testProvider(id);
      await refreshProviders();
    } finally {
      setProviderActionId(null);
    }
  };

  const handleDeleteProvider = async (id: string) => {
    setProviderActionId(id);
    try {
      await deleteProvider(id);
      await refreshProviders();
    } finally {
      setProviderActionId(null);
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
                    <CardTitle className="text-lg">Tenant Runtime Config</CardTitle>
                    <CardDescription>Theme and locale-level platform settings</CardDescription>
                </div>
            </div>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <Input
                label="Timezone"
                value={config?.timezone || 'UTC'}
                onChange={(e) => setConfig(prev => prev ? { ...prev, timezone: e.target.value } : null)}
              />
              <Input
                label="Logo URL"
                value={config?.logoUrl || ''}
                onChange={(e) => setConfig(prev => prev ? { ...prev, logoUrl: e.target.value } : null)}
              />
            </div>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Branding & Visuals</CardTitle>
          <CardDescription>
            Customize tenant color identity.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
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

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle>Email Providers</CardTitle>
              <CardDescription>Configure, test, prioritize, activate, and fail over delivery providers</CardDescription>
            </div>
            <Button variant="outline" size="sm" onClick={refreshProviders} icon={<RefreshCcw className="w-4 h-4" />}>
              Refresh
            </Button>
          </div>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="grid gap-3 md:grid-cols-4">
            <Input
              label="Provider Name"
              value={newProvider.name || ''}
              onChange={(e) => setNewProvider((prev) => ({ ...prev, name: e.target.value }))}
              placeholder="Primary SES"
            />
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-content-primary">Type</label>
              <select
                value={newProvider.type || 'SMTP'}
                onChange={(e) => setNewProvider((prev) => ({ ...prev, type: e.target.value }))}
                className="w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm"
              >
                {PROVIDER_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
              </select>
            </div>
            <Input
              label="Host"
              value={newProvider.host || ''}
              onChange={(e) => setNewProvider((prev) => ({ ...prev, host: e.target.value }))}
              placeholder="smtp.sendgrid.net"
            />
            <Input
              label="Port"
              type="number"
              value={newProvider.port || 587}
              onChange={(e) => setNewProvider((prev) => ({ ...prev, port: Number(e.target.value || 587) }))}
            />
            <Input
              label="Username"
              value={newProvider.username || ''}
              onChange={(e) => setNewProvider((prev) => ({ ...prev, username: e.target.value }))}
            />
            <Input
              label="Password"
              type="password"
              value={newProvider.password || ''}
              onChange={(e) => setNewProvider((prev) => ({ ...prev, password: e.target.value }))}
            />
            <Input
              label="Priority"
              type="number"
              value={newProvider.priority || 1}
              onChange={(e) => setNewProvider((prev) => ({ ...prev, priority: Number(e.target.value || 1) }))}
            />
            <Input
              label="Max Send/sec"
              type="number"
              value={newProvider.maxSendRate || 100}
              onChange={(e) => setNewProvider((prev) => ({ ...prev, maxSendRate: Number(e.target.value || 0) }))}
            />
          </div>
          <Button onClick={handleCreateProvider} loading={creatingProvider} icon={<Plus className="w-4 h-4" />}>
            Add Provider
          </Button>

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Host</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Health</TableHead>
                <TableHead>Priority</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {providers.length > 0 ? providers.map((provider) => {
                const health = providerHealth[provider.id] || provider.healthStatus || 'UNKNOWN';
                const healthy = health === 'HEALTHY';
                return (
                  <TableRow key={provider.id}>
                    <TableCell className="font-medium">{provider.name}</TableCell>
                    <TableCell>{provider.type}</TableCell>
                    <TableCell>{provider.host || '-'}</TableCell>
                    <TableCell>
                      <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs ${provider.isActive ? 'bg-green-100 text-green-800' : 'bg-slate-100 text-slate-700'}`}>
                        {provider.isActive ? 'ACTIVE' : 'DISABLED'}
                      </span>
                    </TableCell>
                    <TableCell>
                      <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs ${healthy ? 'bg-green-100 text-green-800' : 'bg-amber-100 text-amber-800'}`}>
                        {healthy ? <ShieldCheck className="w-3 h-3" /> : <ShieldAlert className="w-3 h-3" />}
                        {health}
                      </span>
                    </TableCell>
                    <TableCell>{provider.priority ?? 1}</TableCell>
                    <TableCell className="text-right space-x-2">
                      <Button size="sm" variant="outline" onClick={() => handleTestProvider(provider.id)} disabled={providerActionId === provider.id}>
                        Test
                      </Button>
                      <Button size="sm" variant="outline" onClick={() => handleToggleProvider(provider)} disabled={providerActionId === provider.id}>
                        {provider.isActive ? 'Disable' : 'Enable'}
                      </Button>
                      <Button size="sm" variant="outline" onClick={() => handleDeleteProvider(provider.id)} disabled={providerActionId === provider.id} icon={<Trash2 className="w-3 h-3" />}>
                        Delete
                      </Button>
                    </TableCell>
                  </TableRow>
                );
              }) : (
                <TableRow>
                  <TableCell colSpan={7} className="text-center py-4 text-sm text-content-muted">
                    No providers configured
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}

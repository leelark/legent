'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Copy, KeyRound, Loader2, Plus, RefreshCcw, Save, ShieldCheck } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { AdminEmptyState, AdminPanel, StatusPill } from '@/components/admin/AdminChrome';
import { useToast } from '@/components/ui/Toast';
import { getStoredTenantId } from '@/lib/auth';
import {
  federationApi,
  type FederationProvider,
  type FederationProtocol,
  type FederationProviderPayload,
  type ScimToken,
} from '@/lib/federation-api';

type ProviderDraft = {
  providerKey: string;
  displayName: string;
  protocol: FederationProtocol;
  status: 'ACTIVE' | 'PAUSED';
  issuer: string;
  clientId: string;
  clientSecretRef: string;
  authorizationEndpoint: string;
  tokenEndpoint: string;
  userInfoEndpoint: string;
  jwksUrl: string;
  redirectUri: string;
  scopes: string;
  entityId: string;
  ssoUrl: string;
  audience: string;
  signingCertificate: string;
  jitProvisioningEnabled: boolean;
  scimEnabled: boolean;
  defaultWorkspaceId: string;
  defaultRoleKeys: string;
  attributeMapping: string;
  metadata: string;
};

const emptyDraft: ProviderDraft = {
  providerKey: '',
  displayName: '',
  protocol: 'OIDC',
  status: 'ACTIVE',
  issuer: '',
  clientId: '',
  clientSecretRef: '',
  authorizationEndpoint: '',
  tokenEndpoint: '',
  userInfoEndpoint: '',
  jwksUrl: '',
  redirectUri: '',
  scopes: 'openid,email,profile',
  entityId: '',
  ssoUrl: '',
  audience: '',
  signingCertificate: '',
  jitProvisioningEnabled: true,
  scimEnabled: true,
  defaultWorkspaceId: 'workspace-default',
  defaultRoleKeys: 'USER',
  attributeMapping: '{\n  "email": "email",\n  "firstName": "given_name",\n  "lastName": "family_name",\n  "externalId": "sub"\n}',
  metadata: '{}',
};

export function FederationConfigPanel() {
  const { addToast } = useToast();
  const [providers, setProviders] = useState<FederationProvider[]>([]);
  const [tokens, setTokens] = useState<ScimToken[]>([]);
  const [draft, setDraft] = useState<ProviderDraft>(emptyDraft);
  const [selectedProviderId, setSelectedProviderId] = useState('');
  const [tokenLabel, setTokenLabel] = useState('Primary IdP SCIM token');
  const [newToken, setNewToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [creatingToken, setCreatingToken] = useState(false);

  const tenantId = typeof window === 'undefined' ? '{tenantId}' : getStoredTenantId() || '{tenantId}';
  const apiBase = useMemo(() => {
    const configured = process.env.NEXT_PUBLIC_API_BASE_URL || process.env.NEXT_PUBLIC_API_URL;
    const fallback = typeof window === 'undefined' ? '' : window.location.origin;
    return (configured || fallback).replace(/\/$/, '');
  }, []);

  const activeKey = draft.providerKey.trim() || '{providerKey}';
  const callbackUrls = useMemo(() => ({
    oidc: `${apiBase}/api/v1/sso/${tenantId}/${activeKey}/oidc/callback`,
    samlAcs: `${apiBase}/api/v1/sso/${tenantId}/${activeKey}/saml/acs`,
    samlMetadata: `${apiBase}/api/v1/sso/${tenantId}/${activeKey}/metadata`,
    scim: `${apiBase}/api/v1/scim/v2`,
    login: `${apiBase}/api/v1/sso/${tenantId}/${activeKey}/login`,
  }), [activeKey, apiBase, tenantId]);

  const selectedProvider = useMemo(
    () => providers.find((provider) => readString(provider, 'id') === selectedProviderId),
    [providers, selectedProviderId]
  );

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [providerRows, tokenRows] = await Promise.all([
        federationApi.listProviders(),
        federationApi.listScimTokens(),
      ]);
      setProviders(providerRows || []);
      setTokens(tokenRows || []);
      if (providerRows?.[0]?.id) {
        setSelectedProviderId((current) => current || providerRows[0].id);
      }
    } catch (error: any) {
      addToast({ type: 'error', title: 'Federation load failed', message: error?.normalized?.message || error?.message });
    } finally {
      setLoading(false);
    }
  }, [addToast]);

  useEffect(() => {
    load();
  }, [load]);

  const editProvider = (provider: FederationProvider) => {
    const protocol = readString(provider, 'protocol').toUpperCase() === 'SAML' ? 'SAML' : 'OIDC';
    setDraft({
      providerKey: readString(provider, 'providerKey', 'provider_key'),
      displayName: readString(provider, 'displayName', 'display_name'),
      protocol,
      status: readString(provider, 'status') === 'PAUSED' ? 'PAUSED' : 'ACTIVE',
      issuer: readString(provider, 'issuer'),
      clientId: readString(provider, 'clientId', 'client_id'),
      clientSecretRef: readString(provider, 'clientSecretRef', 'client_secret_ref'),
      authorizationEndpoint: readString(provider, 'authorizationEndpoint', 'authorization_endpoint'),
      tokenEndpoint: readString(provider, 'tokenEndpoint', 'token_endpoint'),
      userInfoEndpoint: readString(provider, 'userInfoEndpoint', 'userinfo_endpoint'),
      jwksUrl: readString(provider, 'jwksUrl', 'jwks_url'),
      redirectUri: readString(provider, 'redirectUri', 'redirect_uri'),
      scopes: readList(provider, 'scopes').join(',') || 'openid,email,profile',
      entityId: readString(provider, 'entityId', 'entity_id'),
      ssoUrl: readString(provider, 'ssoUrl', 'sso_url'),
      audience: readString(provider, 'audience'),
      signingCertificate: '',
      jitProvisioningEnabled: readBoolean(provider, true, 'jitProvisioningEnabled', 'jit_provisioning_enabled'),
      scimEnabled: readBoolean(provider, false, 'scimEnabled', 'scim_enabled'),
      defaultWorkspaceId: readString(provider, 'defaultWorkspaceId', 'default_workspace_id') || 'workspace-default',
      defaultRoleKeys: readList(provider, 'defaultRoleKeys', 'default_role_keys').join(',') || 'USER',
      attributeMapping: stringifyMaybe(provider.attributeMapping ?? provider.attribute_mapping, emptyDraft.attributeMapping),
      metadata: stringifyMaybe(provider.metadata, '{}'),
    });
    setSelectedProviderId(readString(provider, 'id'));
    setNewToken(null);
  };

  const saveProvider = async () => {
    let payload: FederationProviderPayload;
    try {
      payload = toPayload(draft);
    } catch (error: any) {
      addToast({ type: 'error', title: 'Provider config invalid', message: error.message });
      return;
    }
    setSaving(true);
    try {
      const saved = await federationApi.upsertProvider(payload);
      setProviders((current) => [saved, ...current.filter((provider) => readString(provider, 'id') !== readString(saved, 'id'))]);
      setSelectedProviderId(readString(saved, 'id'));
      addToast({ type: 'success', title: 'Federation provider saved', message: payload.providerKey });
      await load();
    } catch (error: any) {
      addToast({ type: 'error', title: 'Provider save failed', message: error?.normalized?.message || error?.message });
    } finally {
      setSaving(false);
    }
  };

  const createToken = async () => {
    const providerId = selectedProviderId || selectedProvider?.id;
    if (!providerId || !tokenLabel.trim()) {
      addToast({ type: 'error', title: 'Select provider and token label' });
      return;
    }
    setCreatingToken(true);
    try {
      const token = await federationApi.createScimToken({
        providerId,
        label: tokenLabel.trim(),
        scopes: ['scim:users', 'scim:groups'],
      });
      setNewToken(token.token || null);
      await load();
      addToast({ type: 'success', title: 'SCIM token created', message: 'Token is visible once in this panel.' });
    } catch (error: any) {
      addToast({ type: 'error', title: 'SCIM token failed', message: error?.normalized?.message || error?.message });
    } finally {
      setCreatingToken(false);
    }
  };

  const copy = async (value: string, label: string) => {
    try {
      await navigator.clipboard.writeText(value);
      addToast({ type: 'success', title: `${label} copied` });
    } catch {
      addToast({ type: 'warning', title: 'Copy unavailable', message: 'Unable to copy value to clipboard.' });
    }
  };

  return (
    <AdminPanel
      title="Federation"
      subtitle="OIDC, SAML, JIT provisioning, and SCIM configuration for enterprise identity."
      action={<Button size="sm" variant="secondary" onClick={load} loading={loading} icon={<RefreshCcw className="h-4 w-4" />}>Refresh</Button>}
    >
      {loading ? (
        <div className="flex min-h-[220px] items-center justify-center text-content-muted">
          <Loader2 className="mr-2 h-5 w-5 animate-spin" />
          Loading federation control plane
        </div>
      ) : (
        <div className="grid gap-5 xl:grid-cols-[minmax(0,1.1fr)_420px]">
          <section className="space-y-5">
            <div className="flex flex-wrap items-center gap-2">
              <Button size="sm" variant={draft.protocol === 'OIDC' ? 'primary' : 'secondary'} onClick={() => setDraft({ ...draft, protocol: 'OIDC', redirectUri: callbackUrls.oidc })}>
                OIDC
              </Button>
              <Button size="sm" variant={draft.protocol === 'SAML' ? 'primary' : 'secondary'} onClick={() => setDraft({ ...draft, protocol: 'SAML', redirectUri: callbackUrls.samlAcs })}>
                SAML
              </Button>
              <Button size="sm" variant="secondary" onClick={() => setDraft(emptyDraft)} icon={<Plus className="h-4 w-4" />}>
                New provider
              </Button>
            </div>

            <div className="grid gap-3 md:grid-cols-2">
              <Input label="Provider key" value={draft.providerKey} onChange={(event) => setDraft({ ...draft, providerKey: event.target.value })} placeholder="okta-prod" />
              <Input label="Display name" value={draft.displayName} onChange={(event) => setDraft({ ...draft, displayName: event.target.value })} placeholder="Okta Production" />
              <Input label="Default workspace" value={draft.defaultWorkspaceId} onChange={(event) => setDraft({ ...draft, defaultWorkspaceId: event.target.value })} placeholder="workspace-default" />
              <Input label="Default roles" value={draft.defaultRoleKeys} onChange={(event) => setDraft({ ...draft, defaultRoleKeys: event.target.value })} placeholder="USER,ORG_ADMIN" />
            </div>

            {draft.protocol === 'OIDC' ? (
              <div className="grid gap-3 md:grid-cols-2">
                <Input label="Issuer" value={draft.issuer} onChange={(event) => setDraft({ ...draft, issuer: event.target.value })} placeholder="https://idp.example.com" />
                <Input label="Client ID" value={draft.clientId} onChange={(event) => setDraft({ ...draft, clientId: event.target.value })} />
                <Input label="Client secret ref" value={draft.clientSecretRef} onChange={(event) => setDraft({ ...draft, clientSecretRef: event.target.value })} placeholder="env:OIDC_CLIENT_SECRET" />
                <Input label="Scopes" value={draft.scopes} onChange={(event) => setDraft({ ...draft, scopes: event.target.value })} placeholder="openid,email,profile" />
                <Input label="Authorization endpoint" value={draft.authorizationEndpoint} onChange={(event) => setDraft({ ...draft, authorizationEndpoint: event.target.value })} className="md:col-span-2" />
                <Input label="Token endpoint" value={draft.tokenEndpoint} onChange={(event) => setDraft({ ...draft, tokenEndpoint: event.target.value })} className="md:col-span-2" />
                <Input label="JWKS URL" value={draft.jwksUrl} onChange={(event) => setDraft({ ...draft, jwksUrl: event.target.value })} className="md:col-span-2" />
                <Input label="Redirect URI" value={draft.redirectUri} onChange={(event) => setDraft({ ...draft, redirectUri: event.target.value })} className="md:col-span-2" />
              </div>
            ) : (
              <div className="grid gap-3 md:grid-cols-2">
                <Input label="IdP issuer" value={draft.issuer} onChange={(event) => setDraft({ ...draft, issuer: event.target.value })} placeholder="https://idp.example.com/entity" />
                <Input label="SP entity ID" value={draft.entityId} onChange={(event) => setDraft({ ...draft, entityId: event.target.value })} placeholder="legent-email-studio" />
                <Input label="SAML SSO URL" value={draft.ssoUrl} onChange={(event) => setDraft({ ...draft, ssoUrl: event.target.value })} className="md:col-span-2" />
                <Input label="Assertion consumer service URL" value={draft.redirectUri} onChange={(event) => setDraft({ ...draft, redirectUri: event.target.value })} className="md:col-span-2" />
                <Input label="Audience" value={draft.audience} onChange={(event) => setDraft({ ...draft, audience: event.target.value })} className="md:col-span-2" />
                <label className="space-y-1.5 text-sm font-medium text-content-primary md:col-span-2">
                  Signing certificate
                  <textarea
                    value={draft.signingCertificate}
                    onChange={(event) => setDraft({ ...draft, signingCertificate: event.target.value })}
                    className="min-h-[112px] w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 font-mono text-xs text-content-primary outline-none focus:border-accent focus:ring-2 focus:ring-accent/30"
                    placeholder="Paste IdP X.509 certificate when creating or rotating"
                  />
                </label>
              </div>
            )}

            <div className="grid gap-3 md:grid-cols-2">
              <label className="space-y-1.5 text-sm font-medium text-content-primary">
                Attribute mapping JSON
                <textarea
                  value={draft.attributeMapping}
                  onChange={(event) => setDraft({ ...draft, attributeMapping: event.target.value })}
                  className="min-h-[144px] w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 font-mono text-xs text-content-primary outline-none focus:border-accent focus:ring-2 focus:ring-accent/30"
                />
              </label>
              <label className="space-y-1.5 text-sm font-medium text-content-primary">
                Metadata JSON
                <textarea
                  value={draft.metadata}
                  onChange={(event) => setDraft({ ...draft, metadata: event.target.value })}
                  className="min-h-[144px] w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 font-mono text-xs text-content-primary outline-none focus:border-accent focus:ring-2 focus:ring-accent/30"
                />
              </label>
            </div>

            <div className="flex flex-wrap items-center gap-4 rounded-lg border border-border-default bg-surface-secondary px-4 py-3 text-sm">
              <label className="flex items-center gap-2">
                <input type="checkbox" checked={draft.jitProvisioningEnabled} onChange={(event) => setDraft({ ...draft, jitProvisioningEnabled: event.target.checked })} />
                JIT provisioning
              </label>
              <label className="flex items-center gap-2">
                <input type="checkbox" checked={draft.scimEnabled} onChange={(event) => setDraft({ ...draft, scimEnabled: event.target.checked })} />
                SCIM enabled
              </label>
              <label className="flex items-center gap-2">
                Status
                <select
                  value={draft.status}
                  onChange={(event) => setDraft({ ...draft, status: event.target.value as ProviderDraft['status'] })}
                  className="rounded-lg border border-border-default bg-surface-primary px-2 py-1 text-sm"
                >
                  <option value="ACTIVE">ACTIVE</option>
                  <option value="PAUSED">PAUSED</option>
                </select>
              </label>
              <Button onClick={saveProvider} loading={saving} icon={<Save className="h-4 w-4" />}>
                Save provider
              </Button>
            </div>
          </section>

          <aside className="space-y-5">
            <section className="rounded-lg border border-border-default bg-surface-primary p-4">
              <div className="mb-3 flex items-center justify-between gap-3">
                <div>
                  <p className="font-semibold text-content-primary">Configured providers</p>
                  <p className="text-xs text-content-secondary">Runtime IdP entries for login and provisioning.</p>
                </div>
                <ShieldCheck className="h-5 w-5 text-emerald-500" />
              </div>
              <div className="space-y-3">
                {providers.map((provider) => {
                  const id = readString(provider, 'id');
                  return (
                    <button
                      key={id}
                      type="button"
                      onClick={() => editProvider(provider)}
                      className="w-full rounded-lg border border-border-default bg-surface-elevated p-3 text-left transition hover:border-brand-300 hover:bg-brand-50/40 dark:hover:bg-brand-950/20"
                    >
                      <div className="flex items-center justify-between gap-3">
                        <p className="truncate text-sm font-semibold">{readString(provider, 'displayName', 'display_name') || readString(provider, 'providerKey', 'provider_key')}</p>
                        <StatusPill status={readString(provider, 'status') || 'ACTIVE'} />
                      </div>
                      <div className="mt-2 flex flex-wrap gap-2 text-xs text-content-secondary">
                        <span>{readString(provider, 'protocol')}</span>
                        <span>{readBoolean(provider, false, 'scimEnabled', 'scim_enabled') ? 'SCIM' : 'SCIM off'}</span>
                        <span>{readBoolean(provider, false, 'hasSigningCertificate') ? 'cert stored' : readBoolean(provider, false, 'hasClientSecretRef') ? 'secret ref' : 'no secret ref'}</span>
                      </div>
                    </button>
                  );
                })}
                {!providers.length ? (
                  <AdminEmptyState title="No federation provider" description="Create OIDC or SAML config to enable enterprise login." />
                ) : null}
              </div>
            </section>

            <section className="rounded-lg border border-border-default bg-surface-primary p-4">
              <p className="font-semibold text-content-primary">IdP URLs</p>
              <div className="mt-3 space-y-2">
                <CopyRow label="OIDC callback" value={callbackUrls.oidc} onCopy={copy} />
                <CopyRow label="SAML ACS" value={callbackUrls.samlAcs} onCopy={copy} />
                <CopyRow label="SAML metadata" value={callbackUrls.samlMetadata} onCopy={copy} />
                <CopyRow label="SCIM base" value={callbackUrls.scim} onCopy={copy} />
                <CopyRow label="Login URL" value={callbackUrls.login} onCopy={copy} />
              </div>
            </section>

            <section className="rounded-lg border border-border-default bg-surface-primary p-4">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="font-semibold text-content-primary">SCIM tokens</p>
                  <p className="text-xs text-content-secondary">Bearer tokens are hashed server-side and shown once.</p>
                </div>
                <KeyRound className="h-5 w-5 text-brand-500" />
              </div>
              <div className="mt-3 space-y-3">
                <select
                  value={selectedProviderId}
                  onChange={(event) => setSelectedProviderId(event.target.value)}
                  className="w-full rounded-lg border border-border-default bg-surface-primary px-3 py-2 text-sm"
                >
                  <option value="">Select provider</option>
                  {providers.map((provider) => (
                    <option key={readString(provider, 'id')} value={readString(provider, 'id')}>
                      {readString(provider, 'displayName', 'display_name') || readString(provider, 'providerKey', 'provider_key')}
                    </option>
                  ))}
                </select>
                <Input label="Token label" value={tokenLabel} onChange={(event) => setTokenLabel(event.target.value)} />
                <Button className="w-full" variant="secondary" loading={creatingToken} onClick={createToken} icon={<KeyRound className="h-4 w-4" />}>
                  Create SCIM token
                </Button>
                {newToken ? (
                  <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-xs text-emerald-900 dark:border-emerald-800 dark:bg-emerald-950/30 dark:text-emerald-100">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-semibold">New token</span>
                      <Button size="sm" variant="secondary" onClick={() => copy(newToken, 'SCIM token')} icon={<Copy className="h-4 w-4" />}>Copy</Button>
                    </div>
                    <p className="mt-2 break-all font-mono">{newToken}</p>
                  </div>
                ) : null}
                <div className="space-y-2">
                  {tokens.map((token) => (
                    <div key={token.id} className="rounded-lg border border-border-default bg-surface-elevated p-3 text-xs">
                      <div className="flex items-center justify-between gap-3">
                        <span className="font-semibold text-content-primary">{token.label}</span>
                        <StatusPill status={token.status || 'ACTIVE'} />
                      </div>
                      <p className="mt-1 text-content-secondary">Last used {formatTime(readString(token, 'lastUsedAt', 'last_used_at'))}</p>
                    </div>
                  ))}
                  {!tokens.length ? <p className="text-xs text-content-muted">No SCIM tokens created.</p> : null}
                </div>
              </div>
            </section>
          </aside>
        </div>
      )}
    </AdminPanel>
  );
}

function CopyRow({ label, value, onCopy }: { label: string; value: string; onCopy: (value: string, label: string) => void }) {
  return (
    <div className="rounded-lg border border-border-default bg-surface-elevated p-2">
      <div className="mb-1 flex items-center justify-between gap-2">
        <span className="text-xs font-semibold text-content-secondary">{label}</span>
        <button type="button" onClick={() => onCopy(value, label)} className="rounded-md p-1 text-content-muted hover:bg-surface-secondary hover:text-content-primary">
          <Copy className="h-3.5 w-3.5" />
        </button>
      </div>
      <p className="break-all font-mono text-[11px] leading-4 text-content-primary">{value}</p>
    </div>
  );
}

function toPayload(draft: ProviderDraft): FederationProviderPayload {
  if (!draft.providerKey.trim() || !draft.displayName.trim()) {
    throw new Error('Provider key and display name are required.');
  }
  const common = {
    providerKey: draft.providerKey.trim(),
    displayName: draft.displayName.trim(),
    protocol: draft.protocol,
    status: draft.status,
    jitProvisioningEnabled: draft.jitProvisioningEnabled,
    scimEnabled: draft.scimEnabled,
    defaultWorkspaceId: optional(draft.defaultWorkspaceId),
    defaultRoleKeys: csv(draft.defaultRoleKeys),
    attributeMapping: jsonObject(draft.attributeMapping, 'Attribute mapping'),
    metadata: jsonObject(draft.metadata, 'Metadata'),
  };

  if (draft.protocol === 'OIDC') {
    return {
      ...common,
      issuer: required(draft.issuer, 'Issuer'),
      clientId: required(draft.clientId, 'Client ID'),
      clientSecretRef: optional(draft.clientSecretRef),
      authorizationEndpoint: required(draft.authorizationEndpoint, 'Authorization endpoint'),
      tokenEndpoint: required(draft.tokenEndpoint, 'Token endpoint'),
      userInfoEndpoint: optional(draft.userInfoEndpoint),
      jwksUrl: required(draft.jwksUrl, 'JWKS URL'),
      redirectUri: required(draft.redirectUri, 'Redirect URI'),
      scopes: csv(draft.scopes),
    };
  }

  return {
    ...common,
    issuer: optional(draft.issuer),
    entityId: optional(draft.entityId),
    ssoUrl: required(draft.ssoUrl, 'SAML SSO URL'),
    redirectUri: required(draft.redirectUri, 'SAML ACS URL'),
    audience: optional(draft.audience),
    signingCertificate: optional(draft.signingCertificate),
  };
}

function readString(row: Record<string, any>, ...keys: string[]) {
  for (const key of keys) {
    const value = row?.[key];
    if (value !== undefined && value !== null) {
      return String(value);
    }
  }
  return '';
}

function readBoolean(row: Record<string, any>, fallback: boolean, ...keys: string[]) {
  for (const key of keys) {
    const value = row?.[key];
    if (typeof value === 'boolean') {
      return value;
    }
    if (value !== undefined && value !== null) {
      return String(value).toLowerCase() === 'true';
    }
  }
  return fallback;
}

function readList(row: Record<string, any>, ...keys: string[]) {
  for (const key of keys) {
    const value = row?.[key];
    if (Array.isArray(value)) {
      return value.map(String).filter(Boolean);
    }
    if (typeof value === 'string' && value.trim()) {
      try {
        const parsed = JSON.parse(value);
        if (Array.isArray(parsed)) {
          return parsed.map(String).filter(Boolean);
        }
      } catch {
        return csv(value);
      }
    }
  }
  return [];
}

function stringifyMaybe(value: unknown, fallback: string) {
  if (!value) {
    return fallback;
  }
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }
  return JSON.stringify(value, null, 2);
}

function jsonObject(value: string, label: string) {
  try {
    const parsed = JSON.parse(value || '{}');
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      throw new Error();
    }
    return parsed as Record<string, unknown>;
  } catch {
    throw new Error(`${label} must be a JSON object.`);
  }
}

function csv(value: string) {
  return value.split(',').map((item) => item.trim()).filter(Boolean);
}

function required(value: string, label: string) {
  const normalized = value.trim();
  if (!normalized) {
    throw new Error(`${label} is required.`);
  }
  return normalized;
}

function optional(value: string) {
  const normalized = value.trim();
  return normalized || undefined;
}

function formatTime(value: string) {
  if (!value) {
    return 'never';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

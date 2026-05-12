import { get, post } from './api-client';

export type FederationProtocol = 'OIDC' | 'SAML';
export type FederationProviderStatus = 'ACTIVE' | 'PAUSED';

export type FederationProvider = {
  id: string;
  tenantId?: string;
  provider_key?: string;
  providerKey?: string;
  display_name?: string;
  displayName?: string;
  protocol: FederationProtocol;
  status?: FederationProviderStatus | string;
  issuer?: string | null;
  client_id?: string | null;
  clientId?: string | null;
  client_secret_ref?: string | null;
  clientSecretRef?: string | null;
  authorization_endpoint?: string | null;
  authorizationEndpoint?: string | null;
  token_endpoint?: string | null;
  tokenEndpoint?: string | null;
  userinfo_endpoint?: string | null;
  userInfoEndpoint?: string | null;
  jwks_url?: string | null;
  jwksUrl?: string | null;
  redirect_uri?: string | null;
  redirectUri?: string | null;
  scopes?: string[] | string;
  entity_id?: string | null;
  entityId?: string | null;
  sso_url?: string | null;
  ssoUrl?: string | null;
  audience?: string | null;
  jit_provisioning_enabled?: boolean;
  jitProvisioningEnabled?: boolean;
  scim_enabled?: boolean;
  scimEnabled?: boolean;
  default_workspace_id?: string | null;
  defaultWorkspaceId?: string | null;
  default_role_keys?: string[] | string;
  defaultRoleKeys?: string[];
  attribute_mapping?: Record<string, unknown> | string | null;
  attributeMapping?: Record<string, unknown>;
  metadata?: Record<string, unknown> | string | null;
  hasSigningCertificate?: boolean;
  hasClientSecretRef?: boolean;
  created_at?: string;
  createdAt?: string;
  updated_at?: string;
  updatedAt?: string;
};

export type FederationProviderPayload = {
  providerKey: string;
  displayName: string;
  protocol: FederationProtocol;
  status?: FederationProviderStatus;
  issuer?: string;
  clientId?: string;
  clientSecretRef?: string;
  authorizationEndpoint?: string;
  tokenEndpoint?: string;
  userInfoEndpoint?: string;
  jwksUrl?: string;
  redirectUri?: string;
  scopes?: string[];
  entityId?: string;
  ssoUrl?: string;
  audience?: string;
  signingCertificate?: string;
  jitProvisioningEnabled?: boolean;
  scimEnabled?: boolean;
  defaultWorkspaceId?: string;
  defaultRoleKeys?: string[];
  attributeMapping?: Record<string, unknown>;
  metadata?: Record<string, unknown>;
};

export type ScimToken = {
  id: string;
  provider_id?: string;
  providerId?: string;
  label: string;
  scopes?: string[] | string;
  status?: string;
  expires_at?: string | null;
  expiresAt?: string | null;
  last_used_at?: string | null;
  lastUsedAt?: string | null;
  created_at?: string;
  createdAt?: string;
  token?: string;
};

export const federationApi = {
  listProviders: () => get<FederationProvider[]>('/federation/providers'),
  upsertProvider: (payload: FederationProviderPayload) => post<FederationProvider>('/federation/providers', payload),
  getProvider: (providerKey: string) => get<FederationProvider>(`/federation/providers/${encodeURIComponent(providerKey)}`),
  listScimTokens: () => get<ScimToken[]>('/federation/scim-tokens'),
  createScimToken: (payload: { providerId: string; label: string; scopes?: string[]; expiresAt?: string }) =>
    post<ScimToken>('/federation/scim-tokens', payload),
};

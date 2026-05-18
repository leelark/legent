import { get, post } from '@/lib/api-client';

export type CorePlatformRecord = Record<string, unknown>;
export type CoreMetadata = Record<string, unknown>;
export type IsoDate = string;
export type IsoDateTime = string;

export type OrganizationRequest = {
  name: string;
  slug: string;
  status?: string;
  metadata?: CoreMetadata;
};

export type BusinessUnitRequest = {
  organizationId: string;
  parentId?: string;
  code?: string;
  name: string;
  description?: string;
  status?: string;
  metadata?: CoreMetadata;
};

export type WorkspaceRequest = {
  organizationId: string;
  businessUnitId?: string;
  name: string;
  slug: string;
  status?: string;
  defaultEnvironment?: string;
  metadata?: CoreMetadata;
};

export type TeamRequest = {
  workspaceId: string;
  name: string;
  code?: string;
  status?: string;
  metadata?: CoreMetadata;
};

export type DepartmentRequest = {
  workspaceId: string;
  name: string;
  code?: string;
  metadata?: CoreMetadata;
};

export type MembershipRequest = {
  userId: string;
  organizationId: string;
  businessUnitId?: string;
  workspaceId?: string;
  teamId?: string;
  departmentId?: string;
  status?: string;
  principalType?: string;
  metadata?: CoreMetadata;
};

export type RoleDefinitionRequest = {
  tenantId?: string;
  roleKey: string;
  displayName: string;
  description?: string;
  system?: boolean;
  permissions?: string[];
  metadata?: CoreMetadata;
};

export type PermissionGroupRequest = {
  tenantId?: string;
  groupKey: string;
  displayName: string;
  permissions?: string[];
  metadata?: CoreMetadata;
};

export type AccessGrantRequest = {
  granteeUserId: string;
  workspaceId?: string;
  permissions?: string[];
  reason?: string;
  expiresAt?: IsoDateTime;
};

export type AccessGrantDecisionRequest = {
  status: string;
  decisionNote?: string;
};

export type RoleBindingRequest = {
  principalType: string;
  principalId: string;
  roleDefinitionId?: string;
  permissionGroupId?: string;
  workspaceId?: string;
  teamId?: string;
  resourceType?: string;
  resourceId?: string;
  effectiveFrom?: IsoDateTime;
  effectiveUntil?: IsoDateTime;
  metadata?: CoreMetadata;
};

export type IdentityProviderRequest = {
  providerKey: string;
  displayName: string;
  protocol: string;
  status?: string;
  issuer?: string;
  entityId?: string;
  ssoUrl?: string;
  jwksUrl?: string;
  metadataUrl?: string;
  attributeMapping?: CoreMetadata;
  certificateFingerprint?: string;
  signingCertificate?: string;
  scimEnabled?: boolean;
  jitProvisioningEnabled?: boolean;
  defaultRoleKeys?: string[];
  metadata?: CoreMetadata;
};

export type ScimTokenRequest = {
  identityProviderId: string;
  label: string;
  scopes?: string[];
  expiresAt?: IsoDateTime;
};

export type InvitationRequest = {
  email: string;
  organizationId?: string;
  workspaceId?: string;
  roleKeys?: string[];
  expiresAt?: IsoDateTime;
  metadata?: CoreMetadata;
};

export type InvitationAcceptRequest = {
  token: string;
};

export type QuotaPolicyRequest = {
  workspaceId?: string;
  metricKey: string;
  softLimit?: number;
  hardLimit?: number;
  overageRate?: number | string;
  enabled?: boolean;
  metadata?: CoreMetadata;
};

export type UsageIncrementRequest = {
  workspaceId?: string;
  metricKey: string;
  delta?: number;
  periodStart?: IsoDate;
  periodEnd?: IsoDate;
};

export type SubscriptionRequest = {
  planKey: string;
  billingCycle?: string;
  startsAt?: IsoDateTime;
  endsAt?: IsoDateTime;
  autoRenew?: boolean;
  metadata?: CoreMetadata;
};

export type EnvironmentRequest = {
  workspaceId: string;
  environmentKey: string;
  displayName: string;
  status?: string;
  metadata?: CoreMetadata;
};

export type EnvironmentLockRequest = {
  environmentId: string;
  lockType: string;
  reason?: string;
  expiresAt?: IsoDateTime;
};

export type PromotionRequest = {
  workspaceId: string;
  fromEnvironmentId: string;
  toEnvironmentId: string;
  summary?: string;
  changeset?: CoreMetadata;
};

export type PromotionDecisionRequest = {
  status: string;
  decisionNote?: string;
};

export type FeatureControlRequest = {
  workspaceId?: string;
  featureKey: string;
  enabled?: boolean;
  source?: string;
  dependencyKeys?: string[];
  metadata?: CoreMetadata;
};

export const coreApi = {
  listOrganizations: () => get<CorePlatformRecord[]>('/core/organizations'),
  createOrganization: (payload: OrganizationRequest) => post<CorePlatformRecord>('/core/organizations', payload),
  listBusinessUnits: () => get<CorePlatformRecord[]>('/core/business-units'),
  listBusinessUnitTree: () => get<CorePlatformRecord[]>('/core/business-units/tree'),
  createBusinessUnit: (payload: BusinessUnitRequest) => post<CorePlatformRecord>('/core/business-units', payload),
  listWorkspaces: () => get<CorePlatformRecord[]>('/core/workspaces'),
  createWorkspace: (payload: WorkspaceRequest) => post<CorePlatformRecord>('/core/workspaces', payload),
  listTeams: () => get<CorePlatformRecord[]>('/core/teams'),
  createTeam: (payload: TeamRequest) => post<CorePlatformRecord>('/core/teams', payload),
  listDepartments: () => get<CorePlatformRecord[]>('/core/departments'),
  createDepartment: (payload: DepartmentRequest) => post<CorePlatformRecord>('/core/departments', payload),
  listMemberships: () => get<CorePlatformRecord[]>('/core/memberships'),
  createMembership: (payload: MembershipRequest) => post<CorePlatformRecord>('/core/memberships', payload),
  listRoles: () => get<CorePlatformRecord[]>('/core/roles'),
  createRole: (payload: RoleDefinitionRequest) => post<CorePlatformRecord>('/core/roles', payload),
  listPermissionGroups: () => get<CorePlatformRecord[]>('/core/permission-groups'),
  createPermissionGroup: (payload: PermissionGroupRequest) => post<CorePlatformRecord>('/core/permission-groups', payload),
  listRoleBindings: () => get<CorePlatformRecord[]>('/core/role-bindings'),
  createRoleBinding: (payload: RoleBindingRequest) => post<CorePlatformRecord>('/core/role-bindings', payload),
  listAccessGrants: () => get<CorePlatformRecord[]>('/core/access-grants'),
  createAccessGrant: (payload: AccessGrantRequest) => post<CorePlatformRecord>('/core/access-grants', payload),
  decideAccessGrant: (id: string, payload: AccessGrantDecisionRequest) =>
    post<CorePlatformRecord>(`/core/access-grants/${id}/decision`, payload),
  previewAccessPolicy: (principalId: string) =>
    get<CorePlatformRecord>(`/core/access-policy/preview?principalId=${encodeURIComponent(principalId)}`),
  listIdentityProviders: () => get<CorePlatformRecord[]>('/core/identity-providers'),
  upsertIdentityProvider: (payload: IdentityProviderRequest) =>
    post<CorePlatformRecord>('/core/identity-providers', payload),
  listScimTokens: () => get<CorePlatformRecord[]>('/core/scim-tokens'),
  createScimToken: (payload: ScimTokenRequest) => post<CorePlatformRecord>('/core/scim-tokens', payload),
  listInvitations: () => get<CorePlatformRecord[]>('/core/invitations'),
  createInvitation: (payload: InvitationRequest) => post<CorePlatformRecord>('/core/invitations', payload),
  acceptInvitation: (payload: InvitationAcceptRequest) => post<CorePlatformRecord>('/core/invitations/accept', payload),
  listQuotas: () => get<CorePlatformRecord[]>('/core/quotas'),
  upsertQuota: (payload: QuotaPolicyRequest) => post<CorePlatformRecord>('/core/quotas', payload),
  listUsage: () => get<CorePlatformRecord[]>('/core/usage'),
  incrementUsage: (payload: UsageIncrementRequest) => post<CorePlatformRecord>('/core/usage/increment', payload),
  listSubscriptions: () => get<CorePlatformRecord[]>('/core/subscriptions'),
  createSubscription: (payload: SubscriptionRequest) => post<CorePlatformRecord>('/core/subscriptions', payload),
  listEnvironments: () => get<CorePlatformRecord[]>('/core/environments'),
  createEnvironment: (payload: EnvironmentRequest) => post<CorePlatformRecord>('/core/environments', payload),
  lockEnvironment: (payload: EnvironmentLockRequest) => post<CorePlatformRecord>('/core/environments/locks', payload),
  listPromotions: () => get<CorePlatformRecord[]>('/core/promotions'),
  createPromotion: (payload: PromotionRequest) => post<CorePlatformRecord>('/core/promotions', payload),
  decidePromotion: (id: string, payload: PromotionDecisionRequest) =>
    post<CorePlatformRecord>(`/core/promotions/${id}/decision`, payload),
  listFeatureControls: () => get<CorePlatformRecord[]>('/core/feature-controls'),
  upsertFeatureControl: (payload: FeatureControlRequest) => post<CorePlatformRecord>('/core/feature-controls', payload),
  listAuditEvents: (workspaceId?: string, action?: string) =>
    get<CorePlatformRecord[]>(
      `/core/audit${
        workspaceId || action
          ? `?${new URLSearchParams({ ...(workspaceId ? { workspaceId } : {}), ...(action ? { action } : {}) }).toString()}`
          : ''
      }`
    ),
};

import { get, post } from '@/lib/api-client';
import {
  TENANT_STORAGE_KEY,
  WORKSPACE_STORAGE_KEY,
  ENVIRONMENT_STORAGE_KEY,
} from '@/lib/auth';

export interface AccountContext {
  tenantId: string;
  workspaceId?: string | null;
  environmentId?: string | null;
  default?: boolean;
}

export interface BootstrapContextOptions {
  preferredTenantId?: string | null;
  preferredWorkspaceId?: string | null;
  preferredEnvironmentId?: string | null;
}

export interface ActiveContext {
  tenantId: string;
  workspaceId: string;
  environmentId?: string | null;
}

const normalize = (value: string | null | undefined): string | null => {
  if (!value) {
    return null;
  }
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
};

const persistContext = (context: ActiveContext) => {
  localStorage.setItem(TENANT_STORAGE_KEY, context.tenantId);
  localStorage.setItem(WORKSPACE_STORAGE_KEY, context.workspaceId);
  if (context.environmentId) {
    localStorage.setItem(ENVIRONMENT_STORAGE_KEY, context.environmentId);
  } else {
    localStorage.removeItem(ENVIRONMENT_STORAGE_KEY);
  }
};

export async function ensureActiveContext(
  options: BootstrapContextOptions = {}
): Promise<ActiveContext | null> {
  if (typeof window === 'undefined') {
    return null;
  }

  const tenantId = normalize(options.preferredTenantId) ?? normalize(localStorage.getItem(TENANT_STORAGE_KEY));
  const workspaceId = normalize(options.preferredWorkspaceId) ?? normalize(localStorage.getItem(WORKSPACE_STORAGE_KEY));
  const environmentId = normalize(options.preferredEnvironmentId) ?? normalize(localStorage.getItem(ENVIRONMENT_STORAGE_KEY));

  if (tenantId && workspaceId) {
    const active = { tenantId, workspaceId, environmentId };
    persistContext(active);
    return active;
  }

  const contexts = await get<AccountContext[]>('/auth/contexts');
  if (!Array.isArray(contexts) || contexts.length === 0) {
    return null;
  }

  const matched = contexts.find((ctx) => {
    if (!tenantId || ctx.tenantId !== tenantId) {
      return false;
    }
    if (workspaceId) {
      return normalize(ctx.workspaceId) === workspaceId;
    }
    return true;
  });
  const target = matched ?? contexts.find((ctx) => Boolean(ctx.default)) ?? contexts[0];
  if (!target?.tenantId || !normalize(target.workspaceId)) {
    return null;
  }

  const targetTenantId = target.tenantId;
  const targetWorkspaceId = normalize(target.workspaceId);
  const targetEnvironmentId = normalize(target.environmentId) ?? environmentId;
  if (!targetWorkspaceId) {
    return null;
  }

  await post('/auth/context/switch', {
    tenantId: targetTenantId,
    workspaceId: targetWorkspaceId,
    environmentId: targetEnvironmentId,
  });

  const active = {
    tenantId: targetTenantId,
    workspaceId: targetWorkspaceId,
    environmentId: targetEnvironmentId,
  };
  persistContext(active);
  return active;
}

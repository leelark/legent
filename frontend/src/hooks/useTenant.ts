'use client';

import { useTenantStore } from '@/stores/tenantStore';

/**
 * Hook to access current tenant context.
 */
export function useTenant() {
  const { currentTenant, tenants, setCurrentTenant, setTenants } = useTenantStore();

  return {
    tenant: currentTenant,
    tenantId: currentTenant?.id ?? null,
    tenantName: currentTenant?.name ?? 'No Tenant',
    tenants,
    switchTenant: setCurrentTenant,
    setTenants,
  };
}

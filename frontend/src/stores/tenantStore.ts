import { create } from 'zustand';
import { TENANT_STORAGE_KEY } from '@/lib/auth';

interface Tenant {
  id: string;
  name: string;
  slug: string;
  status: string;
  plan: string;
}

interface TenantState {
  currentTenant: Tenant | null;
  tenants: Tenant[];

  setCurrentTenant: (tenant: Tenant) => void;
  setTenants: (tenants: Tenant[]) => void;
}

export const useTenantStore = create<TenantState>((set) => ({
  currentTenant: null,
  tenants: [],

  setCurrentTenant: (tenant) => {
    if (typeof window !== 'undefined') {
      localStorage.setItem(TENANT_STORAGE_KEY, tenant.id);
    }
    set({ currentTenant: tenant });
  },
  setTenants: (tenants) => set({ tenants }),
}));

---
name: legent-api-route-ownership
description: Work on Legent API routes, gateway map, Nginx proxying, Kubernetes ingress, frontend API client ownership, and route drift validation.
---

# Legent API Route Ownership

1. Identify the owning service for each route.
2. Keep `config/gateway/route-map.json`, `config/nginx/nginx.conf`, Kubernetes ingress, and frontend API usage synchronized.
3. Public routes must be intentionally public and documented.
4. Tenant-scoped routes must preserve tenant/workspace/environment context.
5. Avoid ambiguous duplicate route ownership.

Validation:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-route-map.ps1
kubectl kustomize infrastructure/kubernetes/overlays/production
```

Required output:
- route ownership change,
- files updated,
- public/auth/tenant impact,
- validators run.

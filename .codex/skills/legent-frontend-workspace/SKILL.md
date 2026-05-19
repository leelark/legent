---
name: legent-frontend-workspace
description: Work on Legent frontend UI safely. Use for Next.js App Router routes, workspace shell, public pages, API client, auth/context stores, operational SaaS UX, or Playwright validation.
---

# Legent Frontend Workspace

1. Treat `frontend/src/app/(workspace)` as implementation ownership and `frontend/src/app/app` as thin compatibility wrappers.
2. Reuse app shell, API client, auth store, tenant store, and existing UI components before adding abstractions.
3. Keep tokens out of browser storage; only non-secret session metadata may persist.
4. Keep operational UI dense, quiet, scannable, and workflow-focused.
5. Keep public HTML sinks behind `sanitize-html.ts`; use `postPublic` for credential-free public calls.
6. For auth/context changes, run targeted Playwright specs.

## UI Safety

- Verify route groups, middleware redirects, auth hydration, tenant/workspace selection, and API client behavior before changing navigation.
- Keep `/app` compatibility routes thin unless intentionally removing the compatibility surface.
- Check desktop and mobile overflow, text wrapping, dense tables, empty states, loading states, permission states, and error states.
- Use existing design tokens, layout primitives, icons, and stores before adding new components.
- Do not put access or refresh tokens in localStorage, sessionStorage, Zustand persistence, query strings, or logs.
- Public pages must not assume authenticated workspace context.

Relevant gates:

```powershell
cd frontend
npm run lint
npm run build:ci
npm run test:e2e:smoke
```

## Required Output

- Routes/components touched.
- Auth and tenant/workspace impact.
- Browser or Playwright validation.
- Screenshots only when useful.
- Memory/doc updates for route or workflow changes.

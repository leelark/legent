# CSRF and Cookie Threat Model

Date: 2026-05-11

## Scope

Legent uses bearer JWTs and HTTP-only auth cookies (`legent_token`, `legent_refresh_token`, `legent_tenant_id`). Cookie auth improves browser ergonomics, but it creates CSRF exposure on unsafe methods if a browser can send authenticated cross-site requests.

## Controls

- Auth cookies are `HttpOnly`, `Secure` by default, and `SameSite=Strict` by default.
- CORS origins must be explicitly configured through `legent.security.cors.allowed-origins`; startup fails when the list is absent.
- `UnsafeMethodOriginGuardFilter` blocks unsafe browser requests (`POST`, `PUT`, `PATCH`, `DELETE`, and other non-safe methods) when `Origin` or `Referer` is outside the configured allow-list.
- Unsafe requests authenticated by Legent auth cookies must include an allowed `Origin` or `Referer`; missing browser origin headers fail closed for cookie-authenticated writes.
- Unsafe requests without Legent auth cookies may omit browser origin headers so service-to-service calls, CLI calls, health probes, and webhooks keep working through bearer/internal-token validation.
- `X-Tenant-Id`, `X-Workspace-Id`, `X-Request-Id`, and `X-Correlation-Id` remain explicit request headers and are not trusted when they conflict with authenticated tenant context.

## Residual Risk

- SameSite protection depends on browser behavior. The server-side Origin/Referer guard is the primary backstop.
- If production CORS is configured with broad wildcards, cross-site write protection becomes weaker. Production secret/config review must reject wildcard origins unless the pattern is tied to a controlled Legent domain.
- Webhooks and non-browser clients commonly omit Origin and Referer. They must rely on bearer/internal token validation and outbound URL controls.

## Tests

- `shared/legent-security/src/test/java/com/legent/security/UnsafeMethodOriginGuardFilterTest.java`
- `shared/legent-security/src/test/java/com/legent/security/TenantFilterTest.java`
- `services/identity-service/src/test/java/com/legent/identity/controller/AuthControllerTest.java`

## Release Gate

Run:

```powershell
.\scripts\ops\release-gate.ps1 -RunSyntheticSmoke -SmokeBaseUrl http://localhost:8080
```

The synthetic smoke confirms health, protected API auth rejection, cross-site unsafe write rejection, and basic tenant-scoped endpoint denial without credentials.

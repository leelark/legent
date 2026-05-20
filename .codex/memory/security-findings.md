# Security Findings

Fresh baseline date: 2026-05-20.

Current findings from 2026-05-20 audit:

| ID | Owner | Status | Source | Impact | Next Action |
|---|---|---|---|---|---|
| feature-flag-id-tenant-scope | FOUNDATION_SERVICE_OWNER | FIXED | `services/foundation-service/src/main/java/com/legent/foundation/service/FeatureFlagService.java`, `FeatureFlagController.java` | Feature flag get/update/delete by ID previously used raw IDs and could cross tenant boundaries if an ID was known. | Monitor for any explicit global feature flag admin workflow; current protected by-ID operations are tenant-scoped. |
| delivery-provider-workspace-isolation | DELIVERY_SERVICE_OWNER | FIXED | `services/delivery-service/src/main/java/com/legent/delivery/controller/ProviderController.java`, `SmtpProviderRepository.java`, `ProviderSelectionStrategy.java`, `ProviderCapacityService.java`, `V16__smtp_provider_workspace_scope.sql` | Provider CRUD, routing, selection, health checks/status, capacity profiles, and failover drills now require tenant+workspace provider ownership; V16 adds composite ownership constraints for provider-linked tables. | Monitor legacy rows backfilled to `workspace-default` and require target migration review before production promotion. |
| internal-routes-public-edge | API_ARCHITECT | FIXED | `config/nginx/nginx.conf`, `infrastructure/kubernetes/ingress/ingress.yml`, `scripts/ops/validate-route-map.ps1` | Service-to-service endpoints rely on a shared internal token; public edge now returns 404 for known internal routes, including the deliverability suppression child route prefix, and validation enforces the deny rules. | Keep route validation in release gates and extend exact or prefix deny rules for any new `/internal` endpoint. |
| egress-evidence-validator-hardening | RELEASE_MANAGER | FIXED | `scripts/ops/validate-production-egress-evidence.ps1`, `scripts/ops/test-release-evidence-validators.ps1`, `scripts/ops/validate-production-egress-policy-render.ps1`, `scripts/ops/write-production-egress-policy.ps1` | Production egress evidence templates, `example-*` values, documentation CIDRs, unsupported FQDN evidence, broad/non-canonical CIDRs, future dates, unsupported schema versions, stale generated policies, missing generated policies, and uncovered app-label selectors now fail validation instead of satisfying release gates. | Keep production release blocked until real target-environment evidence exists; strict egress validation now proves generated policy render inclusion when reviewed evidence is supplied. |
| sso-tenant-cookie-httponly | IDENTITY_SERVICE_OWNER | FIXED | `services/identity-service/src/main/java/com/legent/identity/controller/SsoController.java`, `services/identity-service/src/test/java/com/legent/identity/controller/SsoControllerTest.java` | SSO tenant cookie now matches normal auth cookie posture: `HttpOnly`, `Secure`, `SameSite`, path, and max-age are covered for OIDC and SAML ACS callbacks; failed OIDC callback emits no cookies. | Preserve HTTP-only tenant cookie parity for any new SSO callback path. |

Standing security invariants:
- Never store secrets, `.env` values, raw tokens, private keys, credentials, or customer data in memory.
- Preserve HTTP-only cookie auth and refresh path scoping.
- Preserve unsafe-method origin/referer guard unless replaced with a stronger CSRF strategy.
- Tenant/workspace context must fail closed except documented public endpoints.
- Do not widen Kafka deserialization trust.
- Do not introduce production `ddl-auto=update`.
- Do not weaken HTML sanitization, outbound URL guard, signed tracking URLs, suppression checks, unsubscribe, warmup, rate controls, or inbox safety.

Security changes require focused tests or documented residual risk.

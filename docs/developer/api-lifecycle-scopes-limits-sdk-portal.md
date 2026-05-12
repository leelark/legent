# API Lifecycle, Scopes, Limits, SDKs, Developer Portal

## Lifecycle

- `experimental`: internal or beta-only; breaking changes allowed with release note.
- `v1`: stable contract; additive changes only.
- `deprecated`: supported for at least one minor release with migration guide.
- `sunset`: blocked from new integrations; removal date published.

## Enterprise Scopes

- `tenant:read`, `tenant:*`: organization, business unit, workspace, role, quota, and identity-provider administration.
- `audit:read`, `audit:*`: immutable evidence, retention matrix, consent ledger, privacy requests, compliance exports.
- `delivery:read`, `delivery:*`: provider capacity, adaptive throttling, failover drills, replay, DLQ operations.
- `tracking:read`, `tracking:*`: raw events, exports, taxonomy, rollups, BI reporting.
- `campaign:*`, `template:*`, `audience:*`, `automation:*`: studio-specific authoring and execution.

## Limits

- API clients must send tenant and workspace context unless endpoint is explicitly public.
- Bulk reads default to 100 rows and hard-cap at 10,000 rows unless export job is used.
- Write APIs require idempotency keys for retryable client workflows.
- Provider send APIs must respect adaptive throttle decisions before queue admission.
- Compliance evidence, consent ledger, and audit exports are append-only from public API perspective.

## SDK Contract

- SDKs should wrap auth, tenant/workspace headers, idempotency keys, retry with jitter, pagination, and typed error bodies.
- SDKs must expose beta namespaces separately from stable namespaces.
- SDK telemetry should include request id and correlation id, never secrets or recipient PII.

## Developer Portal

- Publish OpenAPI specs per service and one gateway-composed spec.
- Include scope matrix, examples, SDK install snippets, rate limits, webhook signature verification, SCIM setup, SAML/OIDC setup, and changelog.
- Include sandbox keys, test tenants, fake provider fixtures, and BI dataset dictionary.
- Show deprecation notices and migration guides alongside endpoint docs.

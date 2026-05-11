# Security And Compliance

## Authentication

The frontend API client sends HTTP-only cookies with `withCredentials: true`. Workspace APIs require session hydration and active tenant/workspace context.

## Authorization And Context

- `X-Tenant-Id`: tenant boundary.
- `X-Workspace-Id`: workspace boundary.
- `X-Environment-Id`: optional runtime environment boundary.
- `X-Request-Id`: traceability.

Shared security modules provide tenant context filters, interceptors, async context propagation, JWT token provider, authentication filter, RBAC evaluator, and exception handling.

## Secrets

Secrets should never be committed. Use `.env` for local, Docker environment variables for Compose, and Kubernetes Secrets for cloud. Critical values include database password, JWT secret, tracking signing key, SMTP/provider credentials, MinIO/S3 credentials, and CORS origin lists.

## Compliance Notes

- Audience consent, preferences, suppressions, double opt-in tokens, unsubscribe, bounce, complaint, and DMARC flows exist in domain model/service logic.
- Audit and configuration version history are present in foundation/admin/platform areas.
- Production should add secret rotation, centralized audit export, SIEM integration, SAST/DAST, dependency scanning, and backup restore drills.

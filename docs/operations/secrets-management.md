# External Secret Management

Date: 2026-05-11

## Production Pattern

Production no longer relies on static Kubernetes `Secret` manifests. The production Kustomize overlay deletes the base placeholder secret and creates an `ExternalSecret` named `legent-secrets`.

Manifest:

- `infrastructure/kubernetes/overlays/production/external-secrets.yml`

Required cluster object:

- `ClusterSecretStore/legent-production-secrets`

## Required Secret Keys

- `DB_USER`
- `DB_PASSWORD`
- `REDIS_PASSWORD`
- `LEGENT_SECURITY_JWT_SECRET`
- `CLICKHOUSE_PASSWORD`
- `MINIO_ACCESS_KEY`
- `MINIO_SECRET_KEY`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `CORS_ALLOWED_ORIGINS`
- `LEGENT_INTERNAL_API_TOKEN`
- `ALERTMANAGER_PLATFORM_WEBHOOK_URL`
- `ALERTMANAGER_PLATFORM_PAGER_URL`

## Guardrails

- Shared runtime guard rejects placeholder secrets in `prod`.
- Internal service token validation fails startup when the token is absent or placeholder-like.
- JWT signing key must be configured and at least 64 characters.
- CORS allow-list must be configured explicitly.

## Rotation

1. Write the new value into the external secret manager.
2. Wait for External Secrets Operator refresh or force reconcile.
3. Restart affected deployments when the value is consumed only at startup.
4. Run synthetic smoke.
5. Retire the old value after the overlap window.

# Legent: Email Studio

Enterprise lifecycle email operating system with a Next.js frontend, Spring Boot microservices, PostgreSQL, Kafka, Redis, ClickHouse, OpenSearch, object storage, Docker Compose, and Kubernetes manifests.

## Prerequisites

- Java 21
- Maven 3.9+ or the included `mvnw.cmd`
- Node.js 20.9 through 24.x and npm 10+
- Docker Desktop with Compose
- `kubectl` when validating Kubernetes overlays

## Local Setup

1. Create a local environment file.

```powershell
Copy-Item .env.example .env
notepad .env
```

Use non-placeholder local values. `LEGENT_SECURITY_JWT_SECRET` must be at least 64 characters. `LEGENT_TRACKING_SIGNING_KEY`, `LEGENT_DELIVERY_CREDENTIAL_KEY`, and `LEGENT_INTERNAL_API_TOKEN` must be at least 32 characters.

Upload limits are configurable through `LEGENT_AUDIENCE_IMPORT_MAX_FILE_SIZE_BYTES`, `LEGENT_AUDIENCE_IMPORT_ALLOWED_CONTENT_TYPES`, `LEGENT_ASSETS_MAX_SIZE_BYTES`, and `LEGENT_ASSETS_ALLOWED_CONTENT_TYPES`.

2. Validate local configuration.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-env.ps1
docker compose config --quiet
```

3. Start the local stack.

```powershell
docker compose up -d --build
powershell -ExecutionPolicy Bypass -File scripts\ops\validate-compose-health.ps1
```

Local ports:

| Service | Port |
| --- | --- |
| Gateway | `http://localhost:8080` |
| Frontend | `http://localhost:${FRONTEND_HOST_PORT:-3000}` |
| PostgreSQL | `5432` |
| Redis | `6379` |
| Kafka | `9092` internal, `29092` host |
| Kafka UI | `http://localhost:8091` |
| OpenSearch | `http://localhost:9200` |
| MinIO Console | `http://localhost:9001` |
| ClickHouse | `8123` HTTP, `9009` native |
| MailHog | `http://localhost:8025` |

## Development

Backend:

```powershell
.\mvnw.cmd -DskipTests compile -T 1C
.\mvnw.cmd test -T 1C
```

Frontend:

```powershell
cd frontend
npm install
npm run lint
npm run build:next
npm run test:e2e:smoke
npm run test:e2e:visual
```

Playwright uses `http://127.0.0.1:3010` by default to avoid stale dev servers on port `3000`. Override with `PLAYWRIGHT_PORT`, `PORT`, or `PLAYWRIGHT_BASE_URL`.

## Release Gate

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ops\release-gate.ps1
```

The gate validates env, gateway routes, backend tests, frontend lint/build, smoke E2E, Docker Compose config, and the production Kubernetes overlay. Use the `-Skip*` switches in the script only for targeted local debugging.

## Kubernetes

Base manifests are local/non-production friendly. Production and global overlays use external managed-service endpoints and external secrets.

```powershell
kubectl kustomize infrastructure\kubernetes\base | Out-Null
kubectl kustomize infrastructure\kubernetes\overlays\production | Out-Null
kubectl kustomize infrastructure\kubernetes\overlays\global\global-primary | Out-Null
kubectl kustomize infrastructure\kubernetes\overlays\global\global-standby | Out-Null
kubectl kustomize infrastructure\kubernetes\overlays\global\global-active-active | Out-Null
```

Gateway route ownership lives in `config/gateway/route-map.json` and is checked by `scripts/ops/validate-route-map.ps1`.

## Architecture

- `frontend/`: Next.js 16, React 19, Tailwind, Playwright
- `shared/`: common Java libraries for security, caching, Kafka, test support
- `services/`: Spring Boot 3.2 microservices
- `config/nginx/`: local gateway config
- `infrastructure/kubernetes/`: base, production, global, ingress, observability manifests
- `scripts/ops/`: production-readiness validation and smoke scripts

API conventions:

- Base path: `/api/v1`
- Tenant/workspace headers: `X-Tenant-Id`, `X-Workspace-Id`
- Response envelope: `{ success, data, error, meta }`
- Pagination: `{ page, size, totalElements, totalPages }`

## License

Proprietary. All rights reserved.

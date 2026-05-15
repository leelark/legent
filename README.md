# Legent Email Studio

Legent is an enterprise email marketing, lifecycle automation, deliverability, and analytics platform. It provides a Next.js workspace UI backed by Java/Spring microservices, Kafka event processing, PostgreSQL service databases, Redis, MinIO, OpenSearch, ClickHouse, Docker Compose, and Kubernetes deployment manifests.

The product is designed for teams that need audience management, email content creation, campaign approvals, high-volume send orchestration, provider-aware delivery, tracking, automation journeys, deliverability monitoring, and admin controls.

Important: no email platform can guarantee that all mail from a new domain or new address lands in the inbox. Legent should optimize authentication, warmup, throttling, suppression compliance, targeting, provider feedback, and reputation. The current delivery code intentionally limits new sender/provider warmup.

## Features

- Multi-tenant signup, login, HTTP-only cookie sessions, workspace/environment context, invitations, delegation, SSO, and SCIM.
- Tenant/workspace foundation, enterprise settings, entitlements, and admin governance.
- Subscriber management, data extensions, preferences, CSV imports, segmentation, and audience resolution.
- Email templates, snippets, brand kits, personalization, dynamic content, landing pages, validation, and test sends.
- Campaign drafting, approvals, preflight, scheduling, launch orchestration, batching, and feedback reconciliation.
- Delivery provider selection, message logs, inbox safety checks, warmup, rate control, retry, bounce, and failure events.
- Signed open/click tracking, conversion ingestion, outbox, ClickHouse analytics, rollups, and WebSocket analytics updates.
- Automation workflows, triggers, node execution, delays, and scheduled journeys.
- Deliverability suppressions, domain/DNS verification, feedback loops, and reputation metrics.
- Platform integrations, webhooks, retries, and import platform services.
- Docker Compose local stack and Kubernetes base/production overlays.

## Tech Stack

- Frontend: Next.js 16, React 19, TypeScript, Tailwind CSS, Zustand, Axios, Framer Motion, GSAP, Chart.js, Playwright.
- Backend: Java 21, Spring Boot 3.2, Spring Security, Spring Data JPA, Spring Kafka, Flyway, MapStruct, Lombok, Micrometer, Resilience4j.
- Data and infrastructure: PostgreSQL, Redis, Kafka, MinIO, OpenSearch, ClickHouse, MailHog, Nginx.
- Build and tests: Maven, npm, Testcontainers, Playwright, Docker Compose, Kubernetes kustomize.

## Folder Structure

```text
.
+-- config/                    # Nginx, gateway route map, database init, local config
+-- docs/                      # Audits, runbooks, contracts, production and load-test notes
+-- frontend/                  # Next.js application and Playwright tests
+-- infrastructure/            # Kubernetes manifests, overlays, observability, secrets templates
+-- scripts/                   # Ops, release, validation, build, and load-test scripts
+-- sdk/                       # SDK/client code
+-- services/                  # Spring Boot microservices
|   +-- audience-service
|   +-- automation-service
|   +-- campaign-service
|   +-- content-service
|   +-- deliverability-service
|   +-- delivery-service
|   +-- foundation-service
|   +-- identity-service
|   +-- platform-service
|   +-- tracking-service
+-- shared/                    # Common, security, Kafka, cache, and test-support modules
+-- docker-compose.yml
+-- Makefile
+-- pom.xml
```

## Local Setup

Prerequisites:

- Java 21
- Maven wrapper from this repository
- Node.js and npm compatible with the frontend lockfile
- Docker Desktop or Docker Engine with Compose
- PowerShell for Windows helper scripts

Create local environment:

```powershell
Copy-Item .env.example .env
```

Edit `.env` with local values. Do not commit `.env`.

Validate environment:

```powershell
.\scripts\ops\validate-env.ps1 -EnvFile .env
```

Install backend/frontend dependencies:

```powershell
.\mvnw.cmd -DskipTests install
cd frontend
npm ci
```

## Running Locally

Run full local stack:

```powershell
docker compose up -d --build
```

Main local URLs:

- Frontend: `http://localhost:3000`
- Gateway: `http://localhost:8080`
- MailHog: `http://localhost:8025`
- Kafka UI: `http://localhost:8095`
- MinIO console: `http://localhost:9001`
- OpenSearch: `http://localhost:9200`
- ClickHouse HTTP: `http://localhost:8123`

Frontend only:

```powershell
cd frontend
npm run dev
```

Backend helper commands:

```powershell
make backend-build
make backend-run
```

Stop local stack:

```powershell
docker compose down
```

## Environment Setup

Use `.env.example` as the contract for required local variables. Key categories include:

- Database credentials and per-service database names.
- Redis, Kafka, MinIO, OpenSearch, and ClickHouse settings.
- JWT secret and token lifetimes.
- Cookie security settings.
- CORS and frontend API base URLs.
- Tracking and delivery signing keys.
- Internal service token.
- Mail provider settings.

Production must use real secret management. The Kubernetes production overlay expects External Secrets and managed infrastructure instead of local Compose backing services.

## Testing

Backend tests:

```powershell
.\mvnw.cmd test
```

Single service tests:

```powershell
.\mvnw.cmd -pl services/campaign-service -am test
```

Frontend lint/build/E2E:

```powershell
cd frontend
npm run lint
npm run build
npm run test:e2e:smoke
```

Infrastructure validation:

```powershell
docker compose config
kubectl kustomize infrastructure/kubernetes/overlays/production
```

## Build Process

Backend package:

```powershell
.\mvnw.cmd -DskipTests package
```

Frontend production build:

```powershell
cd frontend
npm run build
```

Docker images:

```powershell
docker compose build
```

Cached/local helpers:

```powershell
make fast-build
make docker-build
```

## Deployment

Local deployment is handled by Docker Compose.

Production-style manifests live under:

```text
infrastructure/kubernetes/base
infrastructure/kubernetes/overlays/production
```

Render production manifests:

```powershell
kubectl kustomize infrastructure/kubernetes/overlays/production
```

Production overlay removes local-only backing services such as in-cluster PostgreSQL, Redis, Kafka, MinIO, OpenSearch, ClickHouse, and MailHog, and expects managed services plus External Secrets.

Before deployment:

- Validate environment and secret placeholders.
- Run backend tests and frontend build/lint.
- Render Kubernetes manifests.
- Confirm route ownership between `config/gateway/route-map.json` and `config/nginx/nginx.conf`.
- Confirm provider configuration, warmup policy, suppression compliance, and tracking URL configuration.

## Architecture Notes

The current million-send bottleneck is not frontend rendering. It is the backend event and delivery pipeline:

- Audience resolution currently emits a full resolved recipient payload.
- Kafka default partition key can hot-spot by tenant.
- Send execution renders and publishes per recipient.
- Delivery performs multiple per-message checks/writes.
- New sender warmup intentionally blocks unsafe high volume.

For high-volume production, redesign around chunked recipient snapshots, shard-aware Kafka keys, provider/domain backpressure, batched rate reservations, explicit DLQ handling, and tracking ingestion isolation.

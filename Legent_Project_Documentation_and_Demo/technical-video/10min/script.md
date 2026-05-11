# legent-technical-explanation-10min

Target duration: 600 seconds

## 1. Repository Structure

The repository is organized as a production monorepo with shared Java libraries, Spring Boot services, a Next.js frontend, Docker and Kubernetes infrastructure, scripts, tests, and existing project documentation.

## 2. Frontend Architecture

The frontend uses Next.js App Router, public marketing pages, authenticated app routes, a shared workspace layout, reusable UI components, API clients, hooks, and Zustand stores.

## 3. Backend Services

The backend is split into identity, foundation, audience, content, campaign, delivery, tracking, automation, deliverability, and platform services. Each service owns a domain boundary.

## 4. Shared Libraries

Shared libraries centralize common models, security, Kafka, cache, and test support so service code uses consistent response, tenant, event, and testing primitives.

## 5. API And Context

APIs use the /api/v1 base path. Protected requests carry tenant, workspace, environment, and request identifiers. Auth endpoints and public tracking endpoints have different context rules.

## 6. Data Layer

PostgreSQL stores transactional service data through Flyway migrations. ClickHouse supports analytics, Redis supports fast state, OpenSearch supports search, and MinIO handles object storage.

## 7. Kafka Flows

Kafka decouples signup, bootstrap, audience resolution, campaign send, delivery results, tracking ingestion, workflow execution, deliverability feedback, search, notification, and webhook events.

## 8. Local Runtime

Local development starts infrastructure with Docker Compose, builds Java with Maven wrapper, runs Spring services, starts the Next.js frontend, and validates with smoke and e2e tests.

## 9. Cloud Runtime

Cloud setup builds images, pushes to a registry, applies Kubernetes manifests, injects secrets and config maps, then monitors health, logs, rollouts, and service metrics.

## 10. Quality And Roadmap

The future roadmap should add OpenAPI generation, Helm and Terraform, richer observability, CI/CD, enterprise identity, visual regression testing, AI recommendations, and stronger module-level automation.

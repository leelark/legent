# Legent: Email Studio

> Enterprise Email Marketing Platform — A Salesforce Marketing Cloud Email Studio Replica

---

## Quick Start

### Prerequisites

- **Java 21** (Eclipse Temurin recommended)
- **Maven 3.9+** (or use the included `./mvnw` wrapper)
- **Node.js 20+** and npm
- **Docker** and Docker Compose

### 1. Start Infrastructure

```bash
docker compose up -d
```

This starts: PostgreSQL, Redis, Kafka, Zookeeper, OpenSearch, MinIO, ClickHouse, and Kafka UI.

| Service      | Port  | URL                          |
|:-------------|:------|:-----------------------------|
| PostgreSQL   | 5432  | `jdbc:postgresql://localhost:5432/legent_foundation` |
| Redis        | 6379  |                              |
| Kafka        | 9092  |                              |
| Kafka UI     | 8090  | http://localhost:8090        |
| OpenSearch   | 9200  | http://localhost:9200        |
| MinIO        | 9001  | http://localhost:9001        |
| ClickHouse   | 8123  | http://localhost:8123        |

### 2. Build Backend

```bash
mvn clean install -DskipTests
```

### 3. Run Foundation Service

```bash
mvn -pl services/foundation-service spring-boot:run
```

- Health: http://localhost:8081/api/v1/health
- Metrics: http://localhost:8081/actuator/prometheus

### 4. Start Frontend

```bash
cd frontend
npm install
npm run dev
```

- UI: http://localhost:3000

---

## Architecture

```
┌──────────┐   ┌──────────────┐   ┌──────────────────────────┐
│ Next.js  │──▶│ Nginx/Kong   │──▶│ Microservices (Spring)   │
│ Frontend │   │ API Gateway  │   │                          │
└──────────┘   └──────────────┘   │ ┌─ Foundation Service    │
                                  │ ├─ Audience Service      │
                                  │ ├─ Content Service       │
                                  │ ├─ Campaign Service      │
                                  │ ├─ Delivery Service      │
                                  │ ├─ Tracking Service      │
                                  │ ├─ Automation Service    │
                                  │ ├─ Deliverability Svc    │
                                  │ └─ Admin Service         │
                                  └──────────────────────────┘
                                    │         │          │
                              ┌─────┘    ┌────┘     ┌────┘
                              ▼          ▼          ▼
                         PostgreSQL    Kafka     Redis
                         ClickHouse  OpenSearch  MinIO
```

## Project Structure

```
legent-email-studio/
├── shared/                     # Shared Java libraries
│   ├── legent-common/          # Base entities, DTOs, exceptions, utils
│   ├── legent-security/        # Tenant context, JWT, RBAC
│   ├── legent-kafka/           # Kafka producer/consumer abstraction
│   ├── legent-cache/           # Redis caching layer
│   └── legent-test-support/    # TestContainers, mock helpers
├── services/                   # Microservices
│   └── foundation-service/     # Config, feature flags, tenants, health
├── frontend/                   # Next.js + Tailwind app
│   ├── src/app/                # App Router pages
│   ├── src/components/         # UI components + shell
│   ├── src/hooks/              # Custom React hooks
│   ├── src/stores/             # Zustand state stores
│   └── src/styles/             # Design tokens + globals
├── infrastructure/             # Docker + K8s manifests
├── config/                     # Nginx, Kafka topics, env configs
└── docs/                       # Architecture documentation
```

## API Conventions

- **Base path:** `/api/v1`
- **Tenant header:** `X-Tenant-Id` (required on all requests except health)
- **Response format:** `{ success, data, error, meta }`
- **Pagination:** `{ page, size, totalElements, totalPages }`

## Tech Stack

| Layer           | Technology                     |
|:----------------|:-------------------------------|
| Frontend        | React, Next.js 14, Tailwind v3 |
| Backend         | Java 21, Spring Boot 3.2       |
| Database        | PostgreSQL 16 + JSONB          |
| Analytics       | ClickHouse                     |
| Cache           | Redis 7                        |
| Message Broker  | Apache Kafka                   |
| Search          | OpenSearch                     |
| Object Storage  | MinIO                          |
| Gateway         | Nginx (default) / Kong         |
| Container       | Docker + Kubernetes            |

## Running Tests

```bash
# All tests
mvn clean test

# Single service
mvn -pl services/foundation-service test
```

---

## License

Proprietary — All rights reserved.

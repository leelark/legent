# Local Runbook

## Prerequisites

- Java 21. Use the Maven wrapper; it resolves Java 21 in this environment.
- Maven 3.9+ or included `mvnw.cmd`.
- Node.js 20+ and npm.
- Docker and Docker Compose.
- PowerShell on Windows.

## First-Time Setup

```powershell
copy .env.example .env
.\scripts\setup\first-time-setup.bat
```

Review `.env` and set secrets:

```powershell
notepad .env
```

## Start Infrastructure

```powershell
docker compose up -d
docker compose ps
```

Important local services:

```text
PostgreSQL    localhost:5432
Redis         localhost:6379
Kafka         localhost:29092 from host, kafka:9092 inside compose
Kafka UI      http://localhost:8090
OpenSearch    http://localhost:9200
MinIO Console http://localhost:9001
ClickHouse    http://localhost:8123
MailHog       http://localhost:8025
```

## Build Backend

```powershell
.\mvnw.cmd clean install -DskipTests -T 1C
```

Fast build options:

```powershell
make fast-build
.\scripts\fast-build\fast-build.ps1
.\scripts\fast-build\all-in-one.ps1
```

## Run Services Locally

Open separate terminals:

```powershell
.\mvnw.cmd -pl services/foundation-service spring-boot:run
.\mvnw.cmd -pl services/identity-service spring-boot:run
.\mvnw.cmd -pl services/audience-service spring-boot:run
.\mvnw.cmd -pl services/content-service spring-boot:run
.\mvnw.cmd -pl services/campaign-service spring-boot:run
.\mvnw.cmd -pl services/delivery-service spring-boot:run
.\mvnw.cmd -pl services/tracking-service spring-boot:run
.\mvnw.cmd -pl services/automation-service spring-boot:run
.\mvnw.cmd -pl services/deliverability-service spring-boot:run
.\mvnw.cmd -pl services/platform-service spring-boot:run
```

## Run Frontend

```powershell
cd frontend
npm install
npm run dev
```

Open `http://localhost:3000`.

## Smoke Test

```powershell
python simulate_e2e_flow.py
```

## Tests

```powershell
.\mvnw.cmd test
cd frontend
npm run build
npm run test:e2e -- --project=chromium
```

## Troubleshooting

```powershell
docker compose logs -f postgres
docker compose logs -f kafka
docker compose down
docker compose up -d
```

If a service cannot connect to PostgreSQL, verify the service database exists and Flyway migrations ran. If frontend redirects to login, verify `/api/v1/auth/session` and local tenant/workspace context.

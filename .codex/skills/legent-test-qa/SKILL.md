---
name: legent-test-qa
description: Design, add, or run Legent tests and QA validation for backend, frontend, Kafka, security, migration, performance, and release work.
---

# Legent Test QA

Use tests to protect tenant isolation, workspace context, campaign correctness, deliverability safety, and event reliability.

## Test Selection

- Unit tests: validation, mapping, policy, scoring, warmup/rate logic, sanitization, rendering helpers, frontend utilities/hooks/stores.
- Integration tests: controller/service/repository, Flyway, PostgreSQL queries, Kafka producer/consumer, tenant filters, auth/session/cookies.
- E2E tests: public pages, login/session, workspace shell, campaign launch, audience import, template editor, analytics/admin navigation.
- Failure tests: provider timeout/throttle, Kafka retry/DLQ, DB/Redis/MinIO/ClickHouse outage, duplicate events, stuck jobs.
- Security tests: allowed/denied auth, tenant mismatch, origin guard, SCIM scopes, signed tracking, outbound URL guard, secret placeholder rejection.

Commands:

```powershell
.\mvnw.cmd test
.\mvnw.cmd -pl services/campaign-service -am test
cd frontend
npm run lint
npm run build:ci
npm run test:e2e:smoke
```

Required output:
- test plan,
- commands run,
- coverage of failure/denied cases,
- skipped gates and reason,
- memory updates for defects or gaps.

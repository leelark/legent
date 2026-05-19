# Service Dependencies

Fresh baseline date: 2026-05-20.

Current dependency model:
- Frontend depends on gateway-exposed backend APIs and must preserve auth/session cookie behavior.
- Identity service owns authentication, sessions, onboarding, SSO/federation, and SCIM entry points.
- Foundation service owns tenant/workspace/environment/governance platform data.
- Audience, content, campaign, delivery, tracking, automation, deliverability, and platform services coordinate through APIs, Kafka, and shared contracts.
- Shared modules are cross-service dependencies and require broad impact review when changed.
- Infrastructure dependencies include PostgreSQL, Redis, Kafka/Zookeeper, MinIO, OpenSearch, ClickHouse, MailHog, and Nginx.

Do not bypass service boundaries or use another service database directly.

# Service Map

Last updated: 2026-05-13.

| Service | Port | Database | Main ownership |
| --- | --- | --- | --- |
| foundation-service | 8081 | `legent_foundation` | tenants, workspaces, admin/config/public CMS |
| audience-service | 8082 | `legent_audience` | subscribers, lists, segments, imports, suppressions |
| campaign-service | 8083 | `legent_campaign` | campaigns, jobs, batches, send handoff |
| delivery-service | 8084 | `legent_delivery` | providers, delivery execution, logs, feedback |
| tracking-service | 8085 | `legent_tracking` + ClickHouse | tracking ingestion and analytics |
| automation-service | 8086 | `legent_automation` | workflows and journey runtime |
| deliverability-service | 8087 | `legent_deliverability` | DNS, reputation, DMARC, feedback loops |
| platform-service | 8088 | `legent_platform` | webhooks, notifications, search, integrations |
| identity-service | 8089 | `legent_identity` | auth/session/users/federation/SCIM |
| content-service | 8090 | `legent_content` | templates, assets, rendering, landing pages |

Source: `docker-compose.yml`.

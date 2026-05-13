# Unresolved Risks

Last updated: 2026-05-13.

Priority scoring uses `(ProductionReadinessImpact * 5) + (SecurityRisk * 4) + (UserImpact * 3) + (PerformanceImpact * 2) + TechnicalDebtImpact`, each 0-5.

| Priority | Score | Type | Risk | Source | Next action |
| --- | ---: | --- | --- | --- | --- |
| P0 | 41 | PERFORMANCE | LIST/SEGMENT audience resolution still materializes included subscriber ID sets before paging subscribers. | `AudienceResolutionConsumer.java` | Stream/page list and segment memberships with cursor checkpoints or a job selection table. |
| P1 | 34 | SECURITY | Content/platform/data-extension schemas are tenant-only, risking cross-workspace visibility. | DB/API audit | Decide tenant-global vs workspace-scoped and migrate/filter accordingly. |
| P1 | 33 | RELEASE | Production network policy may block managed dependencies. | K8s production/base network policy and prod config | Add explicit egress for chosen managed endpoints/ports. |
| P2 | 29 | SECURITY | Raw HTML frontend surfaces need end-to-end sanitizer contract verification. | `frontend` landing/template preview paths | Audit `dangerouslySetInnerHTML`, add sanitizer tests. |
| P2 | 22 | REFACTOR | Large frontend/backend files increase regression risk. | line-count scan | Split when touched. |
| P2 | 20 | RELEASE | Docs/scripts drift possible around ports/flags. | `AGENTS.md`, scripts list | Validate scripts before use and update docs. |
| P2 | 20 | RELEASE | Route-map has unmapped active controller roots and an apparently unused mapped `/events` prefix. | route-map/controller scan | Sync route-map/Nginx/ingress after confirming exposure intent. |
| P2 | 19 | ENHANCEMENT | Product is private-beta oriented and lacks GA proof artifacts. | docs/audits 2026-05-12 | Produce smoke/load/restore/security/monitoring evidence before public GA claims. |

Resolved:

- 2026-05-13: non-test service and Kubernetes base JPA DDL defaults changed from `update` to `validate`; test `create-drop` left intact.
- 2026-05-13: Kafka trusted package wildcards and shared `com.legent.*` trust narrowed to `java.lang,java.util,com.legent.kafka.model`.
- 2026-05-13: Compose health validator frontend port drift and Redis optional-auth healthcheck fixed.
- 2026-05-13: shared `TenantFilter` now rejects workspace/environment header conflicts against JWT-populated context and preserves authenticated context when headers are omitted. Residual risk: workspace membership/authorization checks across services still need design.
- 2026-05-13: `AudienceResolutionConsumer` now publishes `send.audience.resolved` in bounded `SEND_BATCH_SIZE` chunks with deterministic chunk metadata. Residual risk: upstream resolution/suppression still materializes full in-memory lists.
- 2026-05-13: shared `EventPublisher` no longer silently uses tenant ID for configured high-volume topics; it derives or requires non-tenant routing keys while preserving tenant fallback for low-volume topics.
- 2026-05-13: `DeliveryEventConsumer` now rethrows unexpected send-request failures after logging so listener retry/DLQ handling can engage; other service consumers remain open risk.
- 2026-05-13: shared Kafka listener retry exhaustion now publishes to declared central `kafka.dead-letter`, and automation/tracking/deliverability/campaign/audience/platform listeners rethrow unexpected processing failures for retry/DLQ handling.
- 2026-05-13: tracking analytics WebSocket now requires authentication plus tenant/workspace context, refreshed access tokens preserve workspace/environment claims, and frontend sockets use API-base WebSocket origin.
- 2026-05-13: production overlay now pins `legent/*:1.0.2`, uses production hosts, sets `CLICKHOUSE_DB=legent_analytics`, and validates these at release gate.
- 2026-05-13: campaign send execution now has a bounded per-batch render cache for repeated render requests.

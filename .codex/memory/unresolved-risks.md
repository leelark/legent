# Unresolved Risks

Last updated: 2026-05-14.

Priority scoring uses `(ProductionReadinessImpact * 5) + (SecurityRisk * 4) + (UserImpact * 3) + (PerformanceImpact * 2) + TechnicalDebtImpact`, each 0-5.

| Priority | Score | Type | Risk | Source | Next action |
| --- | ---: | --- | --- | --- | --- |
| P1 | 34 | SECURITY | Content and platform schemas remain tenant-only, risking cross-workspace visibility unless product scope is intentionally tenant-global. | DB/API audit | Decide tenant-global vs workspace-scoped semantics and migrate/filter accordingly. |
| P1 | 33 | RELEASE | Production inherits broad base `allow-legent-egress` (`0.0.0.0/0` TCP 443 except private ranges). Release validation now fails closed until a reviewed production-specific egress model exists; safe policy cannot be completed without exact managed-service/provider CIDRs, ports, or confirmed CNI FQDN-policy support. | `infrastructure/kubernetes/base/network-policy.yml`, `scripts/ops/validate-production-overlay.ps1`, prod config | Obtain endpoint CIDRs/ports or approved FQDN egress policy model, then replace inherited broad egress. |
| P1 | 31 | SECURITY | Service-level workspace authorization remains incomplete outside identity/foundation context checks; many services trust scoped JWT/header context without proving the workspace membership for every domain operation. | DB/API audit; Loop 5 security review | Define shared membership/authorization contract or service-local authorization checks for workspace-scoped operations. |
| P1 | 30 | SECURITY | Platform foundation-settings bridge still calls foundation admin settings without a completed caller-token relay or dedicated service-credential authorization contract. Platform config RBAC and fail-closed bridge behavior reduce exposure but do not solve inter-service auth. | `FoundationSettingsBridgeService`, `PlatformConfigController`, `AdminSettingsController` | Design and test explicit service-to-service auth or caller authorization forwarding for foundation admin settings. |
| P1 | 29 | RELEASE/DATA | Audience V17 blocks ambiguous legacy data-extension workspace mapping, but production upgrade still requires operator-reviewed mapping metadata and target workspace verification outside the audience DB. | `V16__workspace_scope_data_extensions.sql`, `V17__guard_data_extension_workspace_mapping.sql`, `docs/operations/audience-data-extension-workspace-mapping.md` | Run staging preflight, populate reviewed mapping table, and verify target workspaces from the authoritative workspace source before production migration. |
| P2 | 24 | PERFORMANCE | High-volume campaign delivery still carries rendered HTML/body payload pressure in send events; current batching/idempotency fixes improve reliability but not full payload externalization. | campaign/delivery event flow audit | Move large render/content payloads to durable object/storage references or bounded templates with measured provider handoff. |
| P2 | 23 | PRODUCT/SECURITY | Landing-page forms render inert because sanitizer unwraps/removes `<form>` tags; no Legent-controlled submission endpoint, consent, rate-limit, and abuse model is implemented yet. | `frontend/src/lib/sanitize-html.ts`, `services/content-service/src/main/java/com/legent/content/service/EmailContentValidationService.java` | Either remove form-capable UX or implement a controlled landing submission API with validation, consent, throttling, and route coverage. |
| P2 | 22 | REFACTOR | Large frontend/backend files increase regression risk. | line-count scan | Split when touched. |
| P2 | 20 | RELEASE | Docs/scripts drift possible around ports/flags. | `AGENTS.md`, scripts list | Validate scripts before use and update docs. |
| P2 | 19 | ENHANCEMENT | Product is private-beta oriented and lacks executed GA proof artifacts. | docs/audits 2026-05-12; `docs/operations/ga-evidence-matrix.md` | Execute smoke/load/restore/security/monitoring evidence runs before public GA claims. |

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
- 2026-05-13: LIST/SEGMENT audience resolution now uses DB-backed keyset candidate paging instead of materializing full include/exclude subscriber ID sets; focused audience tests passed.
- 2026-05-13: raw HTML frontend sinks are routed through shared DOMPurify profiles, and content-service sanitization now uses jsoup policy tests for script/event/URL/form/srcset/SVG/CSS vectors.
- 2026-05-13: route-map, Nginx, ingress, and route validation now cover literal/controller-root drift checks for known exposed roots including `/api/v1/sso`, `/api/v1/scim/v2`, `/api/v1/federation`, `/api/v1/audience`, `/api/v1/preferences`, `/api/v1/performance-intelligence`, `/api/v1/global`, and `/api/v1/differentiation`; unused `/api/v1/events` route was removed.
- 2026-05-13: audience data extensions and records are now workspace-scoped in schema, repositories, service filters, and tests. Residual: content/platform workspace ownership still needs product decisions.
- 2026-05-14: audience data-extension legacy workspace mapping now fails closed in `V17__guard_data_extension_workspace_mapping.sql` unless operators provide reviewed `public.audience_data_extension_workspace_mapping_review` metadata; clean installs remain unaffected.
- 2026-05-14: delivery and tracking consumers now reject envelope/payload tenant/workspace mismatches and normalize payload scope before side effects; delivery/tracking idempotency now uses pending claim/complete/release semantics.
- 2026-05-14: platform webhook secrets no longer serialize in JSON responses; responses expose `secretConfigured` instead.
- 2026-05-14: platform config and content landing-page write/publish/archive/delete endpoints now have explicit RBAC checks. Residual: foundation bridge service auth and product workspace-scoping decisions remain open above.
- 2026-05-14: release gate now checks native command exit codes, load live mode rejects placeholder or under-specified campaign IDs, route validation checks broad controller mappings, and production overlay validation checks DDL/Flyway invariants.
- 2026-05-14: service-local Flyway defaults now use baseline false, validation true, and out-of-order false across all service `application.yml` files.
- 2026-05-13: campaign handoff failures now rethrow for retry/DLQ and failed publish payloads remain for retry; recipient counters remain delivery-feedback owned.
- 2026-05-13: foundation public-content admin update/publish and workspace-aware foundation APIs now reject cross-workspace mismatches.
- 2026-05-13: audience resolution idempotency now rolls back on listener/publish failure and waits for `send.audience.resolved` publish futures.
- 2026-05-13: campaign idempotency now marks events processed only after side effects finish; critical handoff publishes are awaited, unfinished batches can be requeued, and terminal batch failure reconciles job/campaign state.
- 2026-05-13: production ExternalSecret required runtime keys are rendered and validated; production overlay validation still fails closed on unresolved egress.
- 2026-05-13: public optional-auth handling, public landing fetches, SSO/SCIM tenant-free routing, identity environment context minting, and foundation dynamic workspace-scoped helpers were hardened.

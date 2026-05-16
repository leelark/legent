# Unresolved Risks

Last updated: 2026-05-16.

Priority scoring uses `(ProductionReadinessImpact * 5) + (SecurityRisk * 4) + (UserImpact * 3) + (PerformanceImpact * 2) + TechnicalDebtImpact`, each 0-5.

| Priority | Score | Type | Risk | Source | Next action |
| --- | ---: | --- | --- | --- | --- |
| P1 | 34 | SECURITY | Content and platform schemas remain tenant-only, risking cross-workspace visibility unless product scope is intentionally tenant-global. | DB/API audit | Decide tenant-global vs workspace-scoped semantics and migrate/filter accordingly. |
| P1 | 33 | RELEASE | Production inherits broad base `allow-legent-egress` (`0.0.0.0/0` TCP 443 except private ranges). Release validation now fails closed until a reviewed production-specific egress model exists; safe policy cannot be completed without exact managed-service/provider CIDRs, ports, or confirmed CNI FQDN-policy support. | `infrastructure/kubernetes/base/network-policy.yml`, `scripts/ops/validate-production-overlay.ps1`, prod config | Obtain endpoint CIDRs/ports or approved FQDN egress policy model, then replace inherited broad egress. |
| P1 | 33 | DELIVERABILITY | Campaign launch now enforces sender/sending-domain alignment, but still does not query authoritative verified DNS, domain warmup, provider health/capacity, or suppression readiness from deliverability/delivery services before launch. | Full audit 2026-05-16; `CampaignEngineService`, `CampaignLaunchOrchestrationService` | Add service-backed launch gate for verified sender domain, DNS auth, warmup state, provider capacity, suppression health, and operator override audit. |
| P1 | 32 | PERFORMANCE | Delivery warmup/rate checks can race under concurrent workers because reservation is not a single atomic token or lease operation. | Full audit 2026-05-16 delivery rate-control scan | Implement Redis or DB-backed atomic token reservation with idempotent reservation IDs and release/expire behavior. |
| P1 | 31 | SECURITY | Service-level workspace authorization remains incomplete outside identity/foundation context checks; many services trust scoped JWT/header context without proving the workspace membership for every domain operation. | DB/API audit; Loop 5 security review | Define shared membership/authorization contract or service-local authorization checks for workspace-scoped operations. |
| P1 | 29 | RELEASE/DATA | Audience V17 blocks ambiguous legacy data-extension workspace mapping, but production upgrade still requires operator-reviewed mapping metadata and target workspace verification outside the audience DB. | `V16__workspace_scope_data_extensions.sql`, `V17__guard_data_extension_workspace_mapping.sql`, `docs/operations/audience-data-extension-workspace-mapping.md` | Run staging preflight, populate reviewed mapping table, and verify target workspaces from the authoritative workspace source before production migration. |
| P2 | 25 | SECURITY | Webhook endpoint validation resolves hosts before dispatch/retry, but WebClient performs its own DNS lookup at connection time; DNS rebinding can bypass the validated address decision. | `shared/legent-common/src/main/java/com/legent/common/security/OutboundUrlGuard.java`, platform webhook services | Pin validated addresses through a reviewed HTTP client connector or force webhooks through an egress proxy/CNI policy that blocks private/reserved destinations at connect time. |
| P2 | 24 | PERFORMANCE | High-volume campaign delivery still carries rendered HTML/body payload pressure in send events. Oversized rendered payloads now fail before Kafka publish, but full payload externalization is still open. | campaign/delivery event flow audit | Move large render/content payloads to durable object/storage references with length/hash validation and measured provider handoff. |
| P2 | 22 | RELIABILITY | Platform webhook retries now use bounded fetch and claim-before-dispatch, but claimed RETRYING rows have no lease expiry/reconciler after worker crash. | `WebhookRetryService`, `WebhookRetryRepository` | Add claimed-at expiry, max in-flight age, and scheduled stale-claim recovery with tests. |
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
- 2026-05-14: platform config and content landing-page write/publish/archive/delete endpoints now have explicit RBAC checks. Residual: product workspace-scoping decisions remain open above.
- 2026-05-14: platform foundation-settings bridge now relays the caller JWT from inbound `Authorization` or `legent_token` cookie as `Authorization: Bearer ...` to foundation and fails closed when no caller token exists; foundation admin settings keep existing authentication/RBAC enforcement.
- 2026-05-14: landing-page form-capable UX was removed rather than kept inert: frontend/backend sanitizers remove form/control tags, the default landing template uses a normal link, docs/tests reflect no-form policy, and no public submit endpoint was added.
- 2026-05-14: campaign send execution now rejects oversized rendered `email.send.requested` payloads before Kafka publish with `CONTENT_PAYLOAD_TOO_LARGE`; residual externalization/storage-reference design remains open above.
- 2026-05-14: platform async task decoration and platform event consumers now propagate workspace/environment context where present and reject workspace-scoped platform events without workspace ID. Residual platform/content schema workspace scoping remains open above.
- 2026-05-14: release gate now checks native command exit codes, load live mode rejects placeholder or under-specified campaign IDs, route validation checks broad controller mappings, and production overlay validation checks DDL/Flyway invariants.
- 2026-05-14: service-local Flyway defaults now use baseline false, validation true, and out-of-order false across all service `application.yml` files.
- 2026-05-16: foundation bootstrap no longer silently acknowledges failed signup bootstrap work; critical bootstrap requested/completed publishes are awaited and unexpected failures rethrow for Kafka retry/DLQ.
- 2026-05-16: automation workflow trigger idempotency now uses pending claim/mark/release semantics and validates payload before claiming; workflow action publish failures rethrow.
- 2026-05-16: deliverability feedback-loop idempotency now uses pending claim/mark/release semantics, and suppression/reputation side-effect failures release claims and rethrow.
- 2026-05-16: audience subscriber intelligence no longer swallows parse/repository failures after idempotency registration; failures propagate so transaction rollback and Kafka retry can engage.
- 2026-05-16: platform webhook create validation now rejects unsupported, malformed, and excessive event subscriptions and weak HMAC secrets. Residual DNS rebinding SSRF risk remains open above.
- 2026-05-16: frontend API client now fails closed without tenant/workspace context on protected routes and bounds tenant-free path matching to documented public/auth/health/tracking endpoints.
- 2026-05-16: shared unsafe-method origin guard now rejects unsafe cookie-authenticated requests without Origin/Referer; non-cookie service clients remain allowed.
- 2026-05-16: shared tenant filter now limits `t=` tenant query fallback to signed public tracking open/click paths and uses boundary-aware tenant-free path matching.
- 2026-05-16: federated SSO raw groups can no longer grant privileged roles unless explicitly mapped, and JIT login can no longer attach to existing native or other-provider users by email fallback.
- 2026-05-16: SCIM operations are now identity-provider bound, so one provider token cannot list or mutate native/other-provider tenant users.
- 2026-05-16: audience and delivery operation controllers now have method-level RBAC checks.
- 2026-05-16: campaign launch/readiness now blocks missing or mismatched sender/sending-domain alignment. Residual verified DNS, warmup, and provider-capacity gates remain open above.
- 2026-05-16: tracking Redis dedupe keys now include workspace ID, preventing cross-workspace duplicate suppression for the same tenant/message/subscriber tuple.
- 2026-05-16: platform webhook retry processing now uses bounded repository fetch and claim-before-dispatch. Residual stale RETRYING lease recovery remains open above.
- 2026-05-16: identity invitation create/list APIs now return token-free DTOs and bound invitation TTL to seven days.
- 2026-05-16: content latest-version retrieval now requires content/template read RBAC.
- 2026-05-16: delivery retries now mark missing/unresolved rendered content terminally failed instead of sending placeholder content or retrying every minute.
- 2026-05-16: foundation teams/departments reads and role-definition create paths now enforce current workspace/tenant scope for non-tenant-wide roles.
- 2026-05-16: frontend workspace search no longer routes all results to admin, mobile shell exposes Admin/Settings through a More menu, and dependency-proven dead frontend/shared/delivery code was removed.
- 2026-05-13: campaign handoff failures now rethrow for retry/DLQ and failed publish payloads remain for retry; recipient counters remain delivery-feedback owned.
- 2026-05-13: foundation public-content admin update/publish and workspace-aware foundation APIs now reject cross-workspace mismatches.
- 2026-05-13: audience resolution idempotency now rolls back on listener/publish failure and waits for `send.audience.resolved` publish futures.
- 2026-05-13: campaign idempotency now marks events processed only after side effects finish; critical handoff publishes are awaited, unfinished batches can be requeued, and terminal batch failure reconciles job/campaign state.
- 2026-05-13: production ExternalSecret required runtime keys are rendered and validated; production overlay validation still fails closed on unresolved egress.
- 2026-05-13: public optional-auth handling, public landing fetches, SSO/SCIM tenant-free routing, identity environment context minting, and foundation dynamic workspace-scoped helpers were hardened.

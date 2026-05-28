# Enterprise Production Readiness Audit

Date: 2026-05-27

Scope: current local working tree of Legent as-is, including existing dirty files. This audit checks code quality, enterprise production readiness, Salesforce Marketing Cloud Engagement parity, broader market parity, functional/workflow gaps, weak algorithms, performance gaps, and security gaps.

Important conclusion: Legent is not ready for public multi-tenant enterprise GA today. The codebase is much stronger than a prototype, and local engineering gates are mostly healthy, but release promotion is blocked by target evidence, stale autonomous-ops state, low frontend coverage, sparse integration/runtime proof, and incomplete Salesforce-class product parity.

## Executive Readiness Estimate

These percentages are audit estimates, not release claims.

| Area | Readiness | Notes |
|---|---:|---|
| Local engineering/build health | 78% | Backend unit tests, frontend lint/build/coverage, route map, artifact hygiene, production overlay, and Compose config passed locally. |
| Code quality/maintainability | 68% | Good modular architecture and shared safety modules, but large service/UI files and broad orchestration classes remain high-change-risk. |
| Security posture in code | 72% | Tenant context, cookie auth, origin guard, runtime config guard, signed internal service identity, trusted Kafka package allowlist, and sanitization exist. Target proof and CSP hardening remain open. |
| Production release readiness | 40% | Strict production evidence is missing, and local release gate currently fails before app gates because `.codex` leases are stale. |
| Enterprise operational/SRE readiness | 50% | Kubernetes overlays, validators, dashboards/runbooks/evidence schemas exist, but live smoke, restore, monitoring, CI security transcript, TLS/admission, and egress evidence are missing. |
| High-volume send readiness | 45% | Chunking, rate reservations, warmup, retry/DLQ, outbox, and tracking isolation primitives exist, but 10 lakh/10h needs target load and provider proof. |
| Salesforce email-marketing parity | 52% | Strong local contracts in several areas, but Journey, Automation, Contact Builder, AI, analytics, and enterprise governance are partial. |
| Overall public enterprise GA readiness | 50% | Controlled internal/beta is plausible after release-gate cleanup; public production needs evidence and product gap closure. |

## Validation Run In This Audit

| Check | Result |
|---|---|
| `git status --short --branch` | Dirty tree, `main...origin/main [ahead 1]`; many modified/untracked files already present. |
| `.codex\utilities\validate-codex-system.ps1` | Failed: active lease validation found expired leases. |
| Parsed `.codex/worktrees/leases/active-leases.json` | 386 leases, 386 expired. |
| `scripts\ops\validate-route-map.ps1` | Passed for 49 routes and 10 source-discovered internal routes. |
| `scripts\ops\validate-repo-artifact-hygiene.ps1` | Passed. |
| `scripts\ops\validate-production-overlay.ps1` | Passed with warning about placeholder-like text in ExternalSecret template. |
| `docker compose config --quiet` | Passed. |
| `frontend`: lint, `build:ci`, `test:coverage`, `npm audit --omit=dev --audit-level=high` | Passed; 0 production npm vulnerabilities reported. |
| Frontend coverage | Statements 14.55%, branches 27.08%, functions 5.14%, lines 18.26%. |
| `.\mvnw.cmd -T1 test` | Passed; Surefire XML totals: 242 suites, 1403 tests, 0 failures, 0 errors, 0 skipped. |
| `scripts\ops\release-gate.ps1 -LocalOnly -SkipBackend -SkipFrontend -SkipCompose -SkipKustomize` | Failed at codex autonomous system validation due expired leases. |

Not run in this audit: strict production release gate with external evidence, Playwright E2E/visual suites, Docker Compose health startup, Failsafe/Testcontainers integration profile, gitleaks, Trivy, live target smoke, live load, restore drill, TLS/admission checks, monitoring handoff, and provider egress/capacity proof.

## Codebase Inventory

| Metric | Count |
|---|---:|
| Total tracked/workspace files scanned excluding generated folders | 1441 |
| Backend main Java files | 673 |
| Backend test Java files | 245 |
| Frontend source TS/TSX files | 162 |
| Frontend test TS/TSX files | 26 |
| SQL files | 138 |
| Flyway migrations | 136 |
| YAML/YML files | 77 |

Largest current files include:

| File | Lines |
|---|---:|
| `frontend/src/app/(workspace)/email/templates/[id]/page.tsx` | 1680 |
| `services/automation-service/src/main/java/com/legent/automation/service/AutomationStudioService.java` | 1508 |
| `frontend/src/app/(workspace)/automation/page.tsx` | 1467 |
| `frontend/src/components/admin/EnterpriseAdminConsole.tsx` | 1467 |
| `frontend/src/app/(workspace)/campaigns/new/page.tsx` | 1283 |
| `frontend/src/components/settings/EnterpriseSettingsConsole.tsx` | 1281 |
| `services/foundation-service/src/main/java/com/legent/foundation/service/CorePlatformService.java` | 1135 |
| `services/delivery-service/src/main/java/com/legent/delivery/service/DeliveryOrchestrationService.java` | 1068 |
| `services/identity-service/src/main/java/com/legent/identity/service/FederatedIdentityService.java` | 1057 |
| `services/campaign-service/src/main/java/com/legent/campaign/service/SendExecutionService.java` | 951 |

## Strengths

- Service ownership is clear across identity, foundation, audience, content, campaign, delivery, tracking, automation, deliverability, platform, and shared modules.
- Route ownership is actively validated across gateway/Nginx/Kubernetes intent.
- Backend uses Flyway, tenant/workspace context, shared envelopes, runtime configuration guards, Kafka contract validation, idempotency services, and focused tests.
- Security-sensitive local controls exist: HTTP-only cookie orientation, tenant mismatch rejection, unsafe-method origin/referer guard, SCIM validation in service, outbound URL guard, HTML sanitization, signed tracking, internal-service signatures, and Kafka trusted-package allowlisting.
- Delivery safety is not naive: warmup, suppression checks, provider/domain readiness, rate reservations, retry/DLQ, and outbox concepts are present.
- The frontend builds on Next.js App Router with a centralized API client and current production build passes.
- Local release and evidence tooling is more mature than typical pre-GA projects.

## Critical Production Blockers

1. Release gate currently fails locally because `.codex/worktrees/leases/active-leases.json` has 386 expired active leases, while `.codex/memory/active-work-items.md` says no active leases. This is a release governance contradiction.
2. Strict production evidence is missing: image digest/SBOM/signature/provenance, reviewed production egress, GA evidence manifest, live synthetic smoke, live load, restore drill, monitoring handoff, TLS/admission, CI security transcript, and Kafka broker topology proof.
3. Provider/domain proof is missing: SPF/DKIM/DMARC, feedback loops, warmup state, sender reputation, provider capacity approval, real provider egress, and provider throttling evidence.
4. Target credentialed login smoke cannot run because target credentials are not available in repository evidence.
5. Public GA and 10 lakh/10h claims remain unsafe without target-like load proof and warmed sender/provider capacity.

## Salesforce Marketing Cloud Engagement Comparison

Current official/primary source baseline checked against repository source refresh and fresh web checks on 2026-05-27. Salesforce source examples used: Email Studio data-extension sending and throttling, Einstein STO, Einstein Engagement Scoring, and Salesforce Marketing Cloud setup docs.

| Salesforce area | Salesforce-class expectation | Legent status | Gap |
|---|---|---|---|
| Email Studio / Content Builder | Content creation, reusable blocks, send classifications, sender/delivery profiles, throttling, send logging, approvals, test sends. | Partial. Templates, content, governance policies, campaign preflight, content references, and template UI exist. | Policy UI/audit history, legal/compliance proof, sender/delivery profile parity, broader approvals, runtime snapshots, and production send evidence remain incomplete. |
| Contact Builder / Data Extensions | Contact model, data extensions, sendable keys, relationships, retention, deletion, shared segments, imports. | Partial. Subscribers, imports, data extensions, suppressions, preferences, relationships, and contact lifecycle audit substrate exist. | Sendable-key migration proof, indexed relationship execution, data retention/deletion target proof, relationship preview/explain, provenance population, and high-volume query evidence. |
| Journey Builder | Entry sources, waits, decisions, joins/splits, goals, exits, versioned activation, simulation, live monitoring. | Partial. Runtime-supported subset and validation exist for limited nodes. | Advanced nodes, goals/exits, simulation, versioning depth, journey analytics, replay, and target high-throughput proof are missing. |
| Automation Studio | Schedules, file triggers, SQL/query, imports, extracts, scripts, send activities, run history, dependency/error handling. | Partial. SQL/import/file/extract/webhook/notification/send activity contracts, dry-run defaulting, locks, idempotency, and redacted history exist. | Script sandbox/signing runtime, target object-store drills, data-extension extract provider handoff, replay evidence, artifact browse/search API, and target cleanup/retention evidence. |
| Deliverability | DNS auth, IP/domain warmup, throttling, feedback loops, suppression, reputation, policy controls. | Code partial, evidence missing. | No target provider capacity/reputation/egress proof; no guarantee of inbox placement can be claimed. |
| Analytics | Campaign/journey metrics, exports, attribution, reports, deliverability health, reconciliation. | Partial. Tracking, ClickHouse rollups, canonical raw semantics, and dashboards exist locally. | Live ClickHouse/PostgreSQL proof, BI-grade reconciliation, funnel/segment semantics, attribution, anomaly detection, and load evidence. |
| Einstein / AI | GenAI content, STO, engagement scoring/frequency, predictive segmentation, trust controls. | Low to partial. Deterministic governance and preview contracts exist. | No proven model-backed AI provider/runtime, recipient-level STO, model metering, model evaluation, generated workflow/segment execution, or Salesforce-equivalent AI depth. |
| Enterprise admin | Roles, setup permissions, SSO/SAML, SCIM, audit, business units/environments, package movement. | Partial. SSO/SCIM/foundation/admin/package dry-run contracts exist. | Deny-precedence evidence, full audit lifecycle, live package apply/rollback, target auth smoke, tenant lifecycle policy, and production release evidence. |

Estimated Salesforce email-marketing parity: 50-55%. Legent has meaningful local contracts, but it is not Salesforce-class in production until the external evidence and journey/automation/contact/AI gaps are closed.

## Other Leader Comparison

| Leader | Market baseline from official sources | Legent gap |
|---|---|---|
| Adobe Journey Optimizer / Adobe Campaign | Multichannel journeys/campaigns, AEP-backed data, AI assistant/features, experimentation, real-time/live reporting, deliverability best practices, orchestrated campaigns. | Legent is email-first; lacks mature cross-channel orchestration, AEP-equivalent data depth, AI assistant/runtime proof, content/path experimentation depth, and live reporting proof. |
| Braze | Real-time cross-channel Canvas journeys, first-party data activation, AI optimization, journey/path/message experimentation, operational scale claims. | Legent lacks cross-channel breadth, sub-second/live scale proof, mature experimentation automation, and real-time decisioning evidence. |
| Klaviyo | Dynamic ecommerce segments, flows, flow analytics, deliverability health indicators, predictive analytics. | Legent lacks ecommerce-native connectors, predictive analytics proof, mature flow analytics UX, and merchant-oriented workflow polish. |
| HubSpot Marketing Hub | CRM-connected segments/workflows, Breeze AI summaries/insights, email delivery/performance dashboards, marketer-friendly workflows. | Legent has stronger enterprise architecture ambitions but less finished low-friction marketer workflow, AI insight surface, and CRM-native packaging. |
| Mailchimp | Segmentation, predicted demographics/predictive segments, approachable campaign builder and automation flows. | Legent is more enterprise/backend-heavy but lacks equivalent polished self-serve flow, predictive segmentation proof, and beginner-ready workflow completion. |

## Weak Algorithms And Product Logic

| Area | Weakness | Evidence |
|---|---|---|
| Campaign send execution | `SendExecutionService.executeBatch` is transactional and loops over recipients while rendering content and awaiting Kafka publishes. This can create long transactions, slow retries, and throughput pressure under high volume. | `SendExecutionService.java:95`, `:187`, `:207`, `:342`, `:470` |
| Render cache | Render cache keys include recipient variables, so personalized sends will often miss cache and render per recipient. | `SendExecutionService.java:651`, `:666` |
| Audience resolution | Chunking exists, but eligibility filtering still does chunk-level deliverability calls plus local eligibility evaluation. Target proof is needed for large audiences and cross-service latency. | `AudienceResolutionConsumer.java:146`, `:295`, `:304`, `:311`, `:316` |
| Rate control | DB-locked rate state and reservation batches are safer than naive counters, but hot provider/domain keys can still contend at scale. Batch size is capped at 100 and needs target concurrency evidence. | `SendRateControlService.java:34`, `:191`, `:399`, `:542`, `:590`, `:628` |
| Tracking outbox defaults | Polling defaults are conservative: batch size 100, max pages 1, scheduled polling. Runtime can tune these, but target tracking ingestion proof is missing. | `TrackingOutboxService.java:92`, `:93`, `:94`, `:115`, `:144` |
| AI/STO/frequency | Current AI is mostly deterministic governance, previews, and approved snapshots, not model-backed prediction comparable to Einstein/Braze/Klaviyo. | `docs/product/salesforce-parity-matrix.md`, `.codex/memory/unresolved-risks.md` |
| Segment builder | Rule taxonomy and limited execution plans exist, but relationship/event/recursive/high-volume execution is not proven. | `docs/product/salesforce-parity-matrix.md` |
| Automation expressions/scripts | Script activity is validation-only without sandbox runtime proof; condition/expression support is not yet a mature rules engine. | `AutomationStudioService.java:48`, `:620`, `:1421`, `.codex/memory/blocked-items.md` |

## Performance Gaps

- No target-like load test proves 10 lakh accepted send attempts in 10 hours.
- No provider capacity evidence proves the external SMTP/API provider will accept the desired rate.
- No warmed-domain sender reputation evidence exists.
- No live Kafka lag, partition distribution, broker topology, min ISR, under-replicated partitions, or replay evidence is attached.
- No live PostgreSQL/Redis/ClickHouse CPU, lock, latency, connection, insert latency, outbox depth/age, or dedupe evidence is attached.
- Campaign send path still contains synchronous per-recipient render and publish behavior.
- Tracking ingress rate limits exist in Nginx, but rate-limit configuration is not the same as target throughput proof.
- Frontend has no meaningful performance budget evidence for key workspace routes beyond successful build.

## Security Gaps

- CSP in `config/nginx/nginx.conf:75` still permits `unsafe-inline` for scripts and styles.
- Internal endpoints are Spring `permitAll` at the security config level and guarded by internal token/signature checks in controllers. That is acceptable locally, but production still needs mTLS/NetworkPolicy/egress proof and drift monitoring.
- Strict release evidence for gitleaks, Trivy, image signatures/provenance, External Secrets, admission controls, TLS, and egress is missing.
- Production overlay passes with a placeholder-like ExternalSecret template warning that must not be mistaken for real secret evidence.
- Script automation sandbox/signing model is blocked.
- AI provider/data-use controls are not production proven.
- Tenant lifecycle policy and public contact tenant inbox ownership remain unresolved.
- Credentialed target login smoke is blocked.

## Functional Issues

- Public multi-tenant GA is blocked.
- Release gate currently fails due `.codex` lease drift before it can evaluate the rest of the local-only gate.
- Journey Builder runtime supports only a subset of expected journey nodes.
- Automation Studio live execution is partial and target replay/object-store proof is missing.
- Contact Builder lacks proven sendable-key migration, relationship execution, and retention/deletion workflows.
- Segment Builder lacks persisted governance/audit APIs, draft count previews, event rollups, relationship traversal, and high-volume execution proof.
- Campaign launch readiness is improved but deeper DNS/auth ownership evidence is not yet fully integrated into the wizard.
- Policy governance lacks a complete approval/audit-history lifecycle.
- Analytics are not yet BI-grade for live ClickHouse/PostgreSQL behavior, attribution, funnel/segment semantics, and anomaly detection.
- Model-backed AI and AI-generated workflow/segment apply flows are not production ready.

## Workflow And UX Issues

- Dirty worktree and expired lease state make release governance unreliable until cleaned.
- Frontend operational screens are feature-rich but have several very large page/components, making workflow bugs harder to isolate.
- Beginner/operator workflows need more guided, safe defaults and clearer next actions around deliverability, audience selection, launch readiness, automation, and analytics.
- Admin workflows need stronger evidence attachment, approval history, audit trail, and target environment status visibility.
- Playwright visual/E2E smoke was not rerun in this audit, so visible workflow quality is not freshly proven.
- The product still reads as "many enterprise surfaces exist" more than "all primary marketer workflows are complete end to end".

## Priority Pending Work

1. Clean `.codex/worktrees/leases/active-leases.json` and reconcile memory/state so local release gate can run.
2. Re-run full local release gate after lease cleanup, including frontend, backend, Compose, and Kustomize.
3. Collect strict production evidence: image, SBOM, signatures, provenance, egress, GA manifest, CI security, TLS/admission, restore, monitoring, Kafka topology, live load.
4. Execute target credentialed login smoke.
5. Run target-like high-volume load test with warmed sender/provider approval.
6. Prove provider/domain deliverability readiness: DNS, FBL, warmup, reputation, bounces/complaints, suppression, unsubscribe, throttling.
7. Split the largest frontend pages and backend services when touched, starting with Automation Studio, campaign creation, template editor, enterprise admin/settings, and core platform services.
8. Raise frontend unit/flow coverage and add focused Playwright coverage for top workspace workflows.
9. Expand integration tests beyond the current small Failsafe/Testcontainers footprint.
10. Move campaign send execution toward shorter transactions, pre-render/reference snapshots, async handoff, and bounded batch reservations.
11. Add persisted segment governance/audit, count preview, relationship/event execution, and performance proof.
12. Complete Automation Studio script sandboxing, artifact browse/search, target replay, object-store cleanup/retention, and data-extension extract provider handoff.
13. Add model-provider AI contract, metering, tenant policy, human review, evaluation, and kill switch before claiming AI parity.
14. Tighten CSP without breaking the Next.js app.

## Enterprise Release Decision

Current decision: BLOCKED-PENDING-EVIDENCE.

Legent can be treated as a strong local pre-production platform with many enterprise safety primitives. It should not be marketed or deployed as a public enterprise production replacement for Salesforce Marketing Cloud Engagement, Adobe Journey Optimizer, Braze, Klaviyo, HubSpot, or Mailchimp until the blockers above are closed and strict evidence is attached.

## Sources

Repository evidence:

- `ARCHITECTURE.md`
- `PROJECT_CONTEXT.md`
- `.codex/bootstrap.md`
- `.codex/memory/active-work-items.md`
- `.codex/memory/blocked-items.md`
- `.codex/memory/unresolved-risks.md`
- `docs/product/salesforce-parity-matrix.md`
- `docs/product/competitor-research/2026-05-24-source-refresh.md`
- `docs/operations/ga-evidence-matrix.md`
- `.codex/workflows/validation-gates.md`

Official/product sources checked:

- Salesforce Marketing Cloud Engagement send to data extensions: https://help.salesforce.com/s/articleView?id=mktg.mc_es_send_to_de_enhanced.htm&language=en_US&type=5
- Salesforce Einstein Send Time Optimization: https://help.salesforce.com/s/articleView?id=sf.mc_jb_einstein_sto.htm&language=en_US
- Salesforce Einstein Engagement Scoring: https://help.salesforce.com/s/articleView?id=mktg.mc_anb_einstein_engagement_scoring.htm&language=en_US&type=5
- Adobe Journey Optimizer journeys: https://experienceleague.adobe.com/en/docs/journey-optimizer/using/orchestrate-journeys/journey
- Adobe Journey Optimizer reporting: https://experienceleague.adobe.com/en/docs/journey-optimizer/using/reporting/gs-reports
- Braze journey orchestration: https://www.braze.com/product/journey-orchestration
- Klaviyo flow analytics and deliverability metrics: https://help.klaviyo.com/hc/en-us/articles/115002779351
- HubSpot email delivery analysis: https://knowledge.hubspot.com/marketing-email/analyze-email-delivery
- Mailchimp segmentation: https://mailchimp.com/features/segmentation/

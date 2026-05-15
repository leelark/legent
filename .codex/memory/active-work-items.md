# Active Work Items

Last updated: 2026-05-16.

FrontendAdminSettingsUi-20260516:
assigned_task: align internal admin dashboard and settings UI with workspace screen chrome, remove local animation usage, and verify relevant frontend suites
task_type: ENHANCEMENT/REFACTOR/TESTING
priority_score: 24
risk_level: MEDIUM
status: completed
changed_files: `frontend/src/components/admin/EnterpriseAdminConsole.tsx`, `frontend/src/components/settings/EnterpriseSettingsConsole.tsx`, `frontend/tests/e2e/admin.spec.ts`, `frontend/tests/e2e/smoke.spec.ts`, `frontend/tests/e2e/visual-smoke.spec.ts`
branch_or_worktree: main
dependencies: workspace `PageHeader`/`Card`/`Panel`/`MetricCard`, existing admin/settings API mocks, `.codex` pending-work audit
blockers: none for local UI work; production egress and data/workspace blockers remain external and unresolved
next_actions: no commit or push performed; keep large admin/settings console split as residual debt when next touched

PendingSweep-20260514:
assigned_task: implement remaining locally actionable `.codex` pending items with parallel subagents
task_type: SECURITY/PERFORMANCE/RELEASE/REFACTOR
priority_score: 34
risk_level: HIGH
status: completed
changed_files: platform foundation-settings bridge caller-token relay/tests, platform async/event workspace context tests, landing-page no-form sanitizer/default UX/tests/docs, campaign oversized send-request guard/tests, README env command, foundation dead helper removal, `.codex` memory
branch_or_worktree: main
dependencies: `.codex/bootstrap.md`, `.codex/memory/unresolved-risks.md`, `.codex/memory/blocked-items.md`, current codebase
blockers: production egress CIDR/FQDN policy, content/platform workspace product semantics and legacy mapping, campaign rendered-content externalization storage contract, audience production mapping metadata still require external decisions
next_actions: focused backend/frontend/route/env validations passed; keep external blockers fail-closed and do not commit or push

Resume-org-20260514:
assigned_task: mandatory five-loop repository organization execution using .codex operating system
task_type: SECURITY/PERFORMANCE/RELEASE/TESTING
priority_score: 43
risk_level: HIGH
status: completed
changed_files: delivery/tracking event scope/idempotency, platform/content RBAC and webhook secrets, landing-page sanitizers, release/load/route/prod validators, Flyway defaults, docs and .codex memory
branch_or_worktree: main
dependencies: AGENTS.md, .codex/bootstrap.md, .codex/commands, .codex/memory, .codex/checkpoints, CTO/PM/repository intelligence/specialized agents
blockers: production egress still requires reviewed provider/VPC CIDRs or approved CNI FQDN policy model; content/platform workspace-scoping requires product/security design
next_actions: final validation sweep recorded; resolve approval-bound blockers before release; do not commit or push

Copernicus:
assigned_task: repository intelligence scan
task_type: ARCHITECTURE
priority_score: 30
risk_level: MEDIUM
status: completed
changed_files: none
branch_or_worktree: main
dependencies: AGENTS.md, manifests, repo scan
blockers: none
next_actions: closed after findings merged into report, database-map, test-impact-map, deployment-flow

Lorentz:
assigned_task: backend architecture and high-volume path scan
task_type: PERFORMANCE
priority_score: 43
risk_level: HIGH
status: completed
changed_files: none
branch_or_worktree: main
dependencies: services, shared
blockers: none
next_actions: closed after findings merged into performance, security, service dependency, and risk maps

Goodall:
assigned_task: frontend ownership, UX architecture, test maturity scan
task_type: ARCHITECTURE
priority_score: 24
risk_level: MEDIUM
status: completed
changed_files: none
branch_or_worktree: main
dependencies: frontend package/app/tests
blockers: none
next_actions: closed after findings merged into repo-map, hotspot-map, test-impact-map, security-findings, technical-debt, unresolved-risks

Sartre:
assigned_task: runtime/container/release scan
task_type: RELEASE
priority_score: 32
risk_level: HIGH
status: completed
changed_files: none
branch_or_worktree: main
dependencies: Compose, Dockerfiles, K8s, CI, scripts
blockers: none
next_actions: closed after findings merged into deployment-flow, unresolved-risks, and security-findings

Peirce:
assigned_task: database and API contract scan
task_type: ARCHITECTURE
priority_score: 34
risk_level: HIGH
status: completed
changed_files: none
branch_or_worktree: main
dependencies: migrations, route-map, controllers, DTOs
blockers: none
next_actions: closed after findings merged into database-map, api-contracts, security-findings, unresolved-risks

Nietzsche:
assigned_task: QA, technical debt, performance hotspot scan
task_type: TESTING
priority_score: 36
risk_level: HIGH
status: completed
changed_files: none
branch_or_worktree: main
dependencies: tests, large files, rg risk scans
blockers: none
next_actions: closed after findings merged into performance, security, test, hotspot, debt, and risk memory

Ramanujan:
assigned_task: memory and command consistency review
task_type: ARCHITECTURE
priority_score: 28
risk_level: MEDIUM
status: completed
changed_files: none
branch_or_worktree: main
dependencies: AGENTS.md, .codex/bootstrap.md, .codex/commands, .codex/memory
blockers: none
next_actions: closed after command/memory fixes applied

Beauvoir:
assigned_task: product capability and production-readiness gap scan
task_type: ENHANCEMENT
priority_score: 30
risk_level: MEDIUM
status: completed
changed_files: none
branch_or_worktree: main
dependencies: docs, README, architecture docs, frontend routes, service capability scan
blockers: none
next_actions: closed after findings merged into enhancement-log and unresolved-risks

Mendel:
assigned_task: harden non-test JPA DDL defaults to validate
task_type: SECURITY
priority_score: 36
risk_level: MEDIUM
status: completed
changed_files: services/*/src/main/resources/application.yml, infrastructure/kubernetes/base/configmap.yml
branch_or_worktree: main
dependencies: service application.yml files, Kubernetes base configmap
blockers: none
next_actions: closed after patch review and memory update

McClintock:
assigned_task: Kafka trusted package narrowing design
task_type: SECURITY
priority_score: 37
risk_level: HIGH
status: completed
changed_files: none
branch_or_worktree: main
dependencies: service application.yml, Kafka consumers, EventEnvelope payload classes
blockers: none
next_actions: closed after allowlist recommendation merged; implementation queued

Planck:
assigned_task: fix Compose health validator frontend port and Redis healthcheck auth drift
task_type: BUG_FIX
priority_score: 30
risk_level: MEDIUM
status: completed
changed_files: scripts/ops/validate-compose-health.ps1, docker-compose.yml
branch_or_worktree: main
dependencies: Compose runtime config and health validation script
blockers: none
next_actions: closed after diff review, parser check, and Compose config checks

Curie-parallel-20260513:
assigned_task: harden high-volume Kafka publisher partition keys
task_type: PERFORMANCE
priority_score: 39
risk_level: HIGH
status: completed
changed_files: shared/legent-kafka/src/main/java/com/legent/kafka/producer/EventPublisher.java, shared/legent-kafka/src/test/java/com/legent/kafka/service/EventPublisherTest.java
branch_or_worktree: main
dependencies: shared Kafka EventPublisher, AppConstants topics
blockers: none
next_actions: closed after integrated Maven validation; service-level publisher contract tests remain

Copernicus-parallel-20260513:
assigned_task: bound audience resolved-event payloads
task_type: PERFORMANCE
priority_score: 43
risk_level: HIGH
status: completed
changed_files: services/audience-service/src/main/java/com/legent/audience/event/AudienceResolutionConsumer.java, services/audience-service/src/test/java/com/legent/audience/event/AudienceResolutionConsumerTest.java
branch_or_worktree: main
dependencies: audience service, shared Kafka publisher
blockers: none
next_actions: closed after integrated Maven validation; stream/page in-memory resolution remains open

Lorentz-parallel-20260513:
assigned_task: delivery send-request consumer failure propagation
task_type: SECURITY
priority_score: 38
risk_level: HIGH
status: completed
changed_files: services/delivery-service/src/main/java/com/legent/delivery/event/DeliveryEventConsumer.java, services/delivery-service/src/test/java/com/legent/delivery/event/DeliveryEventConsumerTest.java
branch_or_worktree: main
dependencies: delivery service Kafka consumer
blockers: none
next_actions: closed after integrated Maven validation; non-delivery consumer failure paths remain open

Ada-loop1-20260513:
assigned_task: Loop 1 repository intelligence for audience resolution paging
task_type: PERFORMANCE
priority_score: 41
risk_level: HIGH
status: completed
changed_files: none
branch_or_worktree: main
dependencies: audience-service repositories, consumer, suppression path
blockers: none
next_actions: found full audience materialization, unbounded/fail-open suppression checks, and consumer silent-ack risk; implementation queued

Turing-loop1-20260513:
assigned_task: Loop 1 repository intelligence for remaining Kafka silent-ack paths outside platform
task_type: SECURITY
priority_score: 37
risk_level: HIGH
status: completed
changed_files: none
branch_or_worktree: main
dependencies: tracking, deliverability, automation, campaign, audience, delivery consumers
blockers: none
next_actions: found automation, tracking, deliverability, campaign, audience listener catch/log/return paths; implementation queued

Hopper-loop1-20260513:
assigned_task: Loop 1 repository intelligence for tracking WebSocket auth and workspace handshake
task_type: SECURITY
priority_score: 31
risk_level: HIGH
status: completed
changed_files: none
branch_or_worktree: main
dependencies: tracking-service WebSocket/security config, frontend WebSocket clients
blockers: none
next_actions: found anonymous /ws/analytics, missing workspace handshake, and missing gateway route; implementation queued

Lamport-loop1-20260513:
assigned_task: Loop 1 repository intelligence for platform consumer failure paths
task_type: SECURITY
priority_score: 34
risk_level: HIGH
status: completed
changed_files: none
branch_or_worktree: main
dependencies: platform-service consumers, webhook/search/import retry logic
blockers: none
next_actions: found platform listener silent-ack, async webhook handoff, search serialization swallow, and stale retrying debt; implementation queued

Knuth-loop1-20260513:
assigned_task: Loop 1 repository intelligence for campaign send render pressure
task_type: PERFORMANCE
priority_score: 34
risk_level: HIGH
status: completed
changed_files: none
branch_or_worktree: main
dependencies: campaign-service SendExecutionService, content render contract
blockers: none
next_actions: found per-recipient remote render loop and handoff idempotency pressure; implementation queued

Noether-loop1-20260513:
assigned_task: Loop 1 repository intelligence for production infra drift
task_type: RELEASE
priority_score: 33
risk_level: HIGH
status: completed
changed_files: none
branch_or_worktree: main
dependencies: Kubernetes overlays, ingress, configmaps, network policies
blockers: none
next_actions: found production latest tags, local hosts, ClickHouse DB drift, egress uncertainty, and missing drift validation; implementation queued

Ada-loop2-20260513:
assigned_task: implement audience resolution paging/fail-closed suppression hardening
task_type: PERFORMANCE
priority_score: 41
risk_level: HIGH
status: completed
changed_files: services/audience-service/src/main/java/com/legent/audience/event/AudienceResolutionConsumer.java, services/audience-service/src/main/java/com/legent/audience/client/DeliverabilityServiceClient.java, services/audience-service/src/test/java/com/legent/audience/event/AudienceResolutionConsumerTest.java
branch_or_worktree: worker fork/main
dependencies: audience-service resolution consumer, repositories, deliverability client
blockers: none
next_actions: default all-subscriber path now pages and suppression failures fail closed; residual list/segment full-ID materialization remains open

Turing-loop2-20260513:
assigned_task: implement Kafka DLQ/retry wiring and non-platform listener failure propagation
task_type: SECURITY
priority_score: 37
risk_level: HIGH
status: completed
changed_files: shared/legent-kafka/src/main/java/com/legent/kafka/config/KafkaConsumerConfig.java, services/automation-service/src/main/java/com/legent/automation/event/WorkflowTriggerConsumer.java, services/tracking-service/src/main/java/com/legent/tracking/event/TrackingEventConsumer.java, services/deliverability-service/src/main/java/com/legent/deliverability/event/FeedbackLoopConsumer.java, services/campaign-service/src/main/java/com/legent/campaign/event/CampaignEventConsumer.java, services/audience-service/src/main/java/com/legent/audience/event/AudienceIntelligenceConsumer.java, services/audience-service/src/main/java/com/legent/audience/event/DeliveryEventConsumer.java
branch_or_worktree: worker fork/main
dependencies: shared Kafka config and non-platform event consumers
blockers: none
next_actions: shared error handler now publishes exhausted retries to dlq topics; owned listener processing failures rethrow

Hopper-loop2-20260513:
assigned_task: implement tracking WebSocket authenticated tenant/workspace handshake and frontend client guard
task_type: SECURITY
priority_score: 31
risk_level: HIGH
status: completed
changed_files: services/tracking-service/src/main/java/com/legent/tracking/config/SecurityConfig.java, services/tracking-service/src/main/java/com/legent/tracking/ws/TenantHandshakeInterceptor.java, frontend/src/lib/analytics-ws.ts, frontend/src/components/tracking/AnalyticsDashboard.tsx, services/tracking-service/src/test/java/com/legent/tracking/ws/TenantHandshakeInterceptorTest.java
branch_or_worktree: worker fork/main
dependencies: tracking WebSocket/security config and frontend analytics websocket client
blockers: none
next_actions: /ws requires auth, handshake requires tenant/workspace, signed open/click routes stay public

Lamport-loop2-20260513:
assigned_task: implement platform listener failure propagation and durable webhook/search failure behavior
task_type: SECURITY
priority_score: 34
risk_level: HIGH
status: completed
changed_files: services/platform-service/src/main/java/com/legent/platform/event/PlatformEventConsumer.java, services/platform-service/src/main/java/com/legent/platform/service/WebhookDispatcherService.java, services/platform-service/src/main/java/com/legent/platform/service/GlobalSearchService.java, services/platform-service/src/test/java/com/legent/platform/event/PlatformEventConsumerTest.java, services/platform-service/src/test/java/com/legent/platform/service/WebhookDispatcherServiceTest.java, services/platform-service/src/test/java/com/legent/platform/service/GlobalSearchServiceTest.java
branch_or_worktree: worker fork/main
dependencies: platform-service event consumers and webhook/search services
blockers: none
next_actions: platform listeners rethrow, webhook retry persistence failures are fatal, search metadata serialization fails cleanly

Knuth-loop2-20260513:
assigned_task: implement bounded campaign render cache in send execution
task_type: PERFORMANCE
priority_score: 34
risk_level: HIGH
status: completed
changed_files: services/campaign-service/src/main/java/com/legent/campaign/service/SendExecutionService.java, services/campaign-service/src/test/java/com/legent/campaign/service/SendExecutionServiceTest.java
branch_or_worktree: worker fork/main
dependencies: campaign-service SendExecutionService and tests
blockers: none
next_actions: bounded per-batch render cache implemented and focused tests pass

Noether-loop2-20260513:
assigned_task: implement production infra drift guards and manifest patches
task_type: RELEASE
priority_score: 33
risk_level: HIGH
status: completed
changed_files: config/gateway/route-map.json, config/nginx/nginx.conf, infrastructure/kubernetes/ingress/ingress.yml, infrastructure/kubernetes/overlays/production/kustomization.yml, infrastructure/kubernetes/overlays/production/production-configmap-patch.yml, scripts/ops/validate-production-overlay.ps1, scripts/ops/release-gate.ps1
branch_or_worktree: worker fork/main
dependencies: infrastructure/kubernetes, config/nginx, scripts release validation
blockers: none
next_actions: production render no longer uses latest/local/ClickHouse default; ws route added; release gate validates drift

Reviewer-backend-loop3-20260513:
assigned_task: Loop 3 backend event/security diff review
task_type: TESTING
priority_score: 36
risk_level: HIGH
status: completed
changed_files: none
branch_or_worktree: main
dependencies: shared Kafka, event consumers, platform/audience/campaign/tracking changes
blockers: none
next_actions: found unprovisioned per-topic DLQ routing and refreshed-token workspace loss; fixed with central declared DLQ and refresh claim preservation

Reviewer-frontend-infra-loop3-20260513:
assigned_task: Loop 3 frontend/infrastructure integration diff review
task_type: RELEASE
priority_score: 32
risk_level: HIGH
status: completed
changed_files: none
branch_or_worktree: main
dependencies: tracking websocket frontend client, Nginx, route-map, ingress, production overlay
blockers: none
next_actions: found production analytics WebSocket host mismatch and missing workspace/environment CORS headers; fixed in frontend URL resolution and ingress CORS

Reviewer-performance-loop3-20260513:
assigned_task: Loop 3 performance and high-volume diff review
task_type: PERFORMANCE
priority_score: 34
risk_level: HIGH
status: completed
changed_files: none
branch_or_worktree: main
dependencies: audience resolution paging, campaign render cache, Kafka partitioning
blockers: none
next_actions: found audience two-pass last-chunk drift and offset paging; fixed with one-pass chunk lookahead and keyset all-subscriber paging; residual list/segment full-ID materialization remains open

Ampere-loop5-20260513:
assigned_task: fix Compose health validator frontend host-port resolution
task_type: RELEASE
priority_score: 30
risk_level: MEDIUM
status: completed
changed_files: scripts/ops/validate-compose-health.ps1
branch_or_worktree: worker fork/main
dependencies: Docker Compose service publisher metadata
blockers: none
next_actions: validator now resolves frontend port from live Compose publishers, then port text, then FRONTEND_HOST_PORT, then default

Hegel-loop5-20260513:
assigned_task: fix gateway restart after WebSocket route addition
task_type: RELEASE
priority_score: 34
risk_level: HIGH
status: completed
changed_files: config/nginx/nginx.conf
branch_or_worktree: worker fork/main
dependencies: Nginx gateway config and shared proxy params
blockers: none
next_actions: duplicate `proxy_http_version` removed from `/ws/analytics`; gateway is healthy on 8080

Resume-loop1-20260513:
assigned_task: resumed five-loop execution, Loop 1 implementation lanes
task_type: SECURITY/PERFORMANCE/RELEASE
priority_score: 41
risk_level: HIGH
status: completed
changed_files: audience-service, identity-service, content-service, frontend sanitizer sinks, route/ingress config, GA evidence docs, .codex memory
branch_or_worktree: main
dependencies: CTO/PM/repository-intelligence agents
blockers: production egress remains blocked pending CIDRs or CNI FQDN policy model
next_actions: Loop 2 reviewers found and fixed sanitizer TS, delegation subject membership, CSS escape, repository test coverage, ingress shadowing, and docs precision

Resume-loop2-20260513:
assigned_task: reanalyze previous fixes and patch missed regressions
task_type: TESTING/SECURITY/RELEASE
priority_score: 39
risk_level: HIGH
status: completed
changed_files: frontend/src/lib/sanitize-html.ts, identity AuthService/repository/tests, content sanitizer/tests, audience repository tests, ingress/route validator, docs/memory
branch_or_worktree: main
dependencies: Loop 1 changes and reviewer findings
blockers: none for Loop 2 validation
next_actions: Loop 3 deep architecture/security/performance review

Resume-loop3-20260513:
assigned_task: deep architecture/runtime/security/performance review and safe production hardening fixes
task_type: SECURITY/PERFORMANCE/RELEASE
priority_score: 40
risk_level: HIGH
status: completed
changed_files: audience data-extension workspace scope migration/service/tests, campaign batch payload retention cleanup/tests, production overlay validation, frontend/backend landing-page form sanitizer, jsoup dependency management, .codex memory
branch_or_worktree: main
dependencies: Loop 3 specialized agents, audience/campaign/content/frontend/infrastructure modules
blockers: production egress still requires reviewed provider/VPC CIDRs or confirmed CNI FQDN policy before production overlay validation can pass
next_actions: Loop 4 full regression, module interaction, event flow, and frontend/backend integration analysis

Resume-loop4-20260513:
assigned_task: full regression, module interaction, event flow, and frontend/backend integration hardening
task_type: SECURITY/PERFORMANCE/RELEASE/TESTING
priority_score: 42
risk_level: HIGH
status: completed
changed_files: campaign retry/handoff semantics, identity delegation requester authorization, audience migration preflights/tests, infra route/ingress/network validation, frontend context hydration/sanitizer tests, foundation workspace-scoped public content/routes
branch_or_worktree: main
dependencies: Loop 4 read-only reviewers and disjoint execution workers
blockers: production egress remains blocked pending exact provider/VPC CIDRs or approved CNI FQDN policy model
next_actions: Loop 5 final production-readiness review, then full validation sweep

Resume-loop5-20260513:
assigned_task: final production-readiness, architecture, quality, event-flow, security, frontend/backend integration, infra, DB, and validation review
task_type: SECURITY/PERFORMANCE/RELEASE/TESTING
priority_score: 43
risk_level: HIGH
status: completed
changed_files: audience event consumer/tests, campaign event/idempotency/batching/send tests, production external secrets/validation, shared security filters/tests, identity context switching/tests, frontend public API/context bootstrap, content security config/tests, foundation dynamic workspace-scoped helpers/tests, .codex memory
branch_or_worktree: main
dependencies: Loop 5 reviewers and disjoint audience/campaign execution workers
blockers: production egress remains blocked pending exact provider/VPC CIDRs or approved CNI FQDN policy model
next_actions: final full validation sweep completed; resolve production egress policy data, content/platform workspace-scope decisions, landing-page controlled submission policy, and high-volume load evidence before release

# 100 Percent Readiness Backlog

Date: 2026-05-23

Purpose: source-of-truth backlog for the work required to move Legent toward 100 percent local quality, product parity, production readiness, high-volume evidence, AI safety, and operational maturity. Percent-complete claims remain evidence-bound.

Queue counts: READY 15, BACKLOG 20, BLOCKED 16, DONE 172.

## READY

These are locally actionable now, subject to normal lease/checkpoint/validation rules.

| ID | Owner | Score | Next action |
|---|---|---:|---|
| `delivery-policy-runtime-snapshot-contract` | DELIVERY_SERVICE_OWNER | 63 | Implement the delivery runtime policy snapshot contract as the next highest local parity/safety slice. |
| `ai-provider-contract-metering` | FOUNDATION_SERVICE_OWNER | 59 | Implement the provider-contract and metering layer before any model-backed AI work. |
| `automation-activity-lock-concurrency-policy` | AUTOMATION_SERVICE_OWNER | 58 | Implement activity lock/concurrency controls. |
| `delivery-feedback-outbox-retention-cleanup` | DELIVERY_SERVICE_OWNER | 56 | Add retention controls for the delivery feedback outbox. |
| `contact-provenance-import-population` | AUDIENCE_SERVICE_OWNER | 55 | Wire import/contact paths into existing provenance metadata. |
| `ai-content-draft-application-workflow` | FRONTEND_OWNER | 53 | Add safe frontend workflow for existing reviewed AI evidence. |
| `deployment-manager-evidence-attachment-workflow` | RELEASE_MANAGER | 52 | Add the evidence-ledger workflow to Deployment Manager. |
| `backend-it-failsafe-testcontainers-profile` | TEST_ARCHITECT | 50 | Introduce the integration-test profile and naming contract. |
| `campaign-provider-domain-selectors-readiness` | FRONTEND_OWNER | 50 | Promote when it is the highest-score compatible READY/BACKLOG item. |
| `qa-ci-coverage-threshold-gate` | QA_ENGINEER | 48 | Add coverage gates with conservative initial thresholds. |
| `delivery-policy-ui-management-audit` | FRONTEND_OWNER | 48 | Add the policy-management UI slice. |
| `frontend-ai-claim-boundary-copy` | PRODUCT_MANAGER_AGENT | 43 | Promote when it is the highest-score compatible READY/BACKLOG item. |
| `qa-ci-full-playwright-chromium-gate` | FRONTEND_OWNER | 41 | Promote full Chromium Playwright coverage into the gate set. |
| `segment-builder-v2-rule-taxonomy` | PRODUCT_MANAGER_AGENT | 40 | Write the v2 rule taxonomy and keep implementation slices separate. |
| `audience-import-parser-and-modal-polish` | FRONTEND_OWNER | 39 | Promote when it is the highest-score compatible READY/BACKLOG item. |

## BACKLOG

These need design refinement, sequencing, or dependency cleanup before promotion.

| ID | Owner | Score | Next action |
|---|---|---:|---|
| `audience-resolution-metadata-only-chunks` | AUDIENCE_SERVICE_OWNER | 64 | Refine and promote when dependencies are clear. |
| `ai-frequency-decision-runtime` | CAMPAIGN_SERVICE_OWNER | 63 | Refine and promote when dependencies are clear. |
| `contact-builder-relationship-cardinality` | DATA_ARCHITECT | 62 | Refine and promote when dependencies are clear. |
| `delivery-rate-control-sharded-reservations` | DELIVERY_SERVICE_OWNER | 61 | Refine and promote when dependencies are clear. |
| `segment-builder-v2-execution-plan` | AUDIENCE_SERVICE_OWNER | 60 | Refine and promote when dependencies are clear. |
| `ai-sto-runtime-scheduler` | CAMPAIGN_SERVICE_OWNER | 59 | Refine and promote when dependencies are clear. |
| `contact-retention-deletion-audit` | AUDIENCE_SERVICE_OWNER | 58 | Refine and promote when dependencies are clear. |
| `automation-live-file-movement-storage-adapter` | AUTOMATION_SERVICE_OWNER | 58 | Refine and promote when dependencies are clear. |
| `ai-segment-workflow-generation-preview` | PRODUCT_MANAGER_AGENT | 57 | Refine and promote when dependencies are clear. |
| `retry-dlq-target-readiness` | KAFKA_CONTRACTS_OWNER | 56 | Refine and promote when dependencies are clear. |
| `journey-advanced-node-handlers-contract` | AUTOMATION_SERVICE_OWNER | 53 | Refine and promote when dependencies are clear. |
| `service-to-service-identity-hardening` | SECURITY_ENGINEER | 52 | Promote when it is the highest-score compatible READY/BACKLOG item. |
| `enterprise-package-export-import-contract` | FOUNDATION_SERVICE_OWNER | 47 | Refine and promote when dependencies are clear. |
| `tracking-analytics-canonical-raw-query-contract` | TRACKING_SERVICE_OWNER | 45 | Refine and promote when dependencies are clear. |
| `segment-builder-v2-governance-ui` | FRONTEND_OWNER | 44 | Refine and promote when dependencies are clear. |
| `automation-artifact-selector-ux` | FRONTEND_OWNER | 43 | Promote when it is the highest-score compatible READY/BACKLOG item. |
| `alertmanager-placeholder-secret-coverage` | SRE_MONITORING_ENGINEER | 39 | Refine and promote when dependencies are clear. |
| `qa-ci-live-compose-health-evidence` | DEVOPS_ENGINEER | 36 | Refine and promote when dependencies are clear. |
| `product-parity-source-refresh-current` | SALESFORCE_PARITY_RESEARCHER | 34 | Refine and promote when dependencies are clear. |
| `qa-ci-visual-smoke-gate` | FRONTEND_OWNER | 29 | Refine and promote when dependencies are clear. |

## BLOCKED

These require external evidence, credentials, policy decisions, schema decisions, target runtime proof, or human review before they can be marked ready or done.

| ID | Owner | Score | Next action |
|---|---|---:|---|
| `delivery-policy-provider-egress-proof` | DELIVERABILITY_ENGINEER | 68 | External provider/domain evidence missing |
| `ga-smoke-restore-monitoring-admission-evidence` | RELEASE_MANAGER | 67 | External GA evidence not present |
| `production-evidence-pack` | RELEASE_MANAGER | 64 | Collect external evidence, then run strict release gate. |
| `live-high-volume-proof` | PERFORMANCE_ENGINEER | 62 | Run a target-like load harness with provider-approved capacity before making throughput claims. |
| `production-image-digest-provenance-evidence` | RELEASE_MANAGER | 62 | External image evidence not present |
| `production-egress-policy-evidence` | DEVOPS_ENGINEER | 61 | External reviewed egress evidence not present |
| `automation-target-runtime-replay-evidence` | AUTOMATION_SERVICE_OWNER | 56 | Target replay evidence missing |
| `tracking-ingestion-batch-consumer-readiness` | TRACKING_SERVICE_OWNER | 55 | Collect Docker/PostgreSQL and live ClickHouse evidence for idempotency, ambiguous/partial batch writes, physical raw-event duplicate behavior, and downstream reconciliation before marking DONE or making BI/throughput claims. |
| `delivery-policy-legal-evidence-pack` | COMPLIANCE_OWNER | 55 | Human compliance review evidence missing |
| `contact-sendable-key-migration-proof` | DATA_ARCHITECT | 55 | Target Flyway/data migration evidence missing |
| `foundation-tenant-lifecycle-policy` | FOUNDATION_SERVICE_OWNER | 55 | Tenant lifecycle policy not decided |
| `foundation-public-contact-tenant-inbox` | FOUNDATION_SERVICE_OWNER | 55 | Public contact ownership schema not decided |
| `qa-auth-target-login-smoke` | QA_ENGINEER | 54 | Target credentials not available in repository |
| `external-provider-capacity` | DELIVERABILITY_ENGINEER | 52 | Collect provider, DNS, feedback-loop, warmup, and reputation evidence before production send claims. |
| `automation-script-activity-security-sandbox` | SECURITY_ENGINEER | 52 | Blocked until sandbox/signing model and runtime isolation evidence exist. |
| `campaign-content-reference-target-proof` | CAMPAIGN_SERVICE_OWNER | 51 | Target load evidence missing |

## Completion Standard

A work item is not DONE until implementation or evidence is present, relevant validation has passed or is explicitly blocked with reason, queue/memory/checkpoint state is updated, and residual release/parity/high-volume risks are recorded.

Detailed completed history remains in .codex/backlog/queue.json doneWork, .codex/checkpoints, .codex/audit/events, and historical reports. Current-state memory intentionally stays compact.

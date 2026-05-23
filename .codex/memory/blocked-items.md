# Blocked Items

Fresh baseline date: 2026-05-20.
Last compacted: 2026-05-23.

Only live blockers are listed here. Completed local fixes and stale historical notes were removed from current-state memory; detailed history remains in queue doneWork, checkpoints, audit events, and reports.

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

## Blocking Rules

- Production readiness requires strict release evidence, not local-only gates.
- 10 lakh / 10h send readiness requires warmed senders, provider-approved capacity, load evidence, queue metrics, retry/DLQ proof, and tracking isolation.
- Provider capacity, sender reputation, DNS/FBL, production egress, image provenance, restore, monitoring, and target runtime proof remain external evidence.
- Policy/schema items require human product/security/data decisions before implementation.

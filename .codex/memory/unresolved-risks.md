# Unresolved Risks

Fresh baseline date: 2026-05-20.
Last compacted: 2026-05-23.

| Priority | Area | Risk | Queue items | Next action |
|---|---|---|---|---|
| P0 | Release | Production readiness cannot be claimed without strict target evidence for egress, GA, image provenance, restore, monitoring, TLS/admission, CI/security, and load. Deployment Manager now has a local evidence ledger, but ledger entries are not production proof until reviewed and run through strict gates. | `production-evidence-pack`, `production-image-digest-provenance-evidence`, `production-egress-policy-evidence`, `ga-smoke-restore-monitoring-admission-evidence` | Attach reviewed target evidence and run strict release gate. |
| P0 | High volume | 10 lakh / 10h remains evidence-bound and unsafe for new/unwarmed senders. | `live-high-volume-proof`, `delivery-policy-provider-egress-proof`, `campaign-content-reference-target-proof` | Run provider-approved target-like load proof. |
| P0 | Deliverability | Guaranteed inbox placement must never be claimed. Current public/admin copy was tightened to delivery review, provider route, policy guidance, and evidence-bound AI wording on 2026-05-23. | `external-provider-capacity` | Keep future copy and docs evidence-based. |
| P1 | Campaign delivery readiness | Campaign Wizard now blocks approval/send when selected provider or domain readiness is blocked, but deeper `/deliverability/auth/checks` DNS/ownership evidence is not yet part of the wizard. | follow-up candidate | Add typed auth-check client/readiness if product wants stricter preflight before launch. |
| P1 | Policy governance | Admin UI now manages send-governance policies and campaign selection uses named versions, but there is no dedicated approval workflow or audit-history endpoint beyond current policy version/timestamps/publication state. | follow-up candidate | Add policy audit/approval history API if operators need forensic policy lifecycle evidence. |
| P1 | Tracking/analytics | Local query dedupe is not live ClickHouse/PostgreSQL idempotency or BI-grade proof. | `tracking-ingestion-batch-consumer-readiness`, `tracking-analytics-canonical-raw-query-contract` | Collect runtime evidence and normalize raw/canonical semantics. |
| P1 | AI | Current implementation is mostly deterministic/governance; model-backed AI parity is unproven. Public/admin copy now uses evidence-bound AI wording, but runtime provider/model claims remain blocked. | `ai-provider-contract-metering`, `ai-content-draft-application-workflow`, `ai-sto-runtime-scheduler`, `ai-frequency-decision-runtime`, `ai-segment-workflow-generation-preview` | Implement governed slices before any provider/model claim. |
| P1 | Product parity | Salesforce/competitor parity is partial and source freshness must be maintained. | `product-parity-source-refresh-current`, segment/contact/journey/automation policy items | Refresh official sources before product claims. |
| P1 | Security/policy | Tenant lifecycle, public contact inbox, service-to-service identity, script sandbox, and AI data use need explicit controls. | `foundation-tenant-lifecycle-policy`, `foundation-public-contact-tenant-inbox`, `service-to-service-identity-hardening`, `automation-script-activity-security-sandbox` | Resolve policy/design and validate fail-closed behavior. |
| P1 | QA/runtime | Unit, local build, backend Failsafe profile, conservative coverage thresholds, and full Chromium Playwright CI/manual gate exist locally; Docker-capable runtime proof, target login smoke, and Compose health evidence remain incomplete. | `qa-auth-target-login-smoke`, `qa-ci-live-compose-health-evidence` | Add runtime and target smoke evidence. |

Current queue/report: .codex/backlog/queue.json and .codex/reports/100-percent-readiness-backlog-2026-05-23.md.

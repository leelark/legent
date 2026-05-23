# Competitor Capability Scan

Access date: 2026-05-20.

Local queue reconciliation: 2026-05-22 (`parity-doc-state-reconcile-20260522`). No new competitor source refresh was performed for this reconciliation.

Scope: official or primary vendor sources for current email marketing automation parity signals as of 2026-05-20. Facts are summarized for backlog routing. Treat any current-market claim after that date as needing refresh.

## Source Register

| ID | Source | Vendor | Area |
|---|---|---|---|
| `SRC-HUBSPOT-20260520-001` | [Create workflows](https://knowledge.hubspot.com/workflows/create-workflows?irgwc=1) | HubSpot | Workflows and AI workflow generation |
| `SRC-HUBSPOT-20260520-002` | [Run A/B tests for marketing emails](https://knowledge.hubspot.com/marketing-email/run-an/a-b-test-on-your-marketing-email) | HubSpot | Experimentation |
| `SRC-HUBSPOT-20260520-003` | [Change the type of a segment](https://knowledge.hubspot.com/lists/convert-active-lists-to-static-lists?lang=en) | HubSpot | Segments |
| `SRC-HUBSPOT-20260520-004` | [Overview of email deliverability](https://knowledge.hubspot.com/marketing-email/overview-of-email-deliverability?web=1) | HubSpot | Deliverability |
| `SRC-BRAZE-20260520-001` | [Canvas](https://www.braze.com/docs/user_guide/engagement_tools/canvas/) | Braze | Journey orchestration |
| `SRC-BRAZE-20260520-002` | [Segments](https://www.braze.com/docs/user_guide/engagement_tools/segments/) | Braze | Segmentation |
| `SRC-BRAZE-20260520-003` | [Intelligent Timing](https://www.braze.com/docs/user_guide/brazeai/intelligence/intelligent_timing/) | Braze | Send-time optimization |
| `SRC-BRAZE-20260520-004` | [Automated IP Warming](https://www.braze.com/docs/user_guide/message_building_by_channel/email/email_setup/ip_warming/automated_ip_warming/) | Braze | Deliverability |
| `SRC-BRAZE-20260520-005` | [BrazeAI](https://www.braze.com/product/optimization-ai/) | Braze | AI optimization |
| `SRC-KLAVIYO-20260520-001` | [Getting started with flows](https://help.klaviyo.com/hc/en-us/articles/115002774932-Getting-started-with-flows) | Klaviyo | Flows |
| `SRC-KLAVIYO-20260520-002` | [Segmentation](https://www.klaviyo.com/features/segmentation) | Klaviyo | Segments |
| `SRC-KLAVIYO-20260520-003` | [Predictive analytics](https://help.klaviyo.com/hc/en-us/articles/360020919731) | Klaviyo | Predictive lifecycle |
| `SRC-MAILCHIMP-20260520-001` | [Marketing Automation Flows](https://mailchimp.com/features/automations/marketing-automation-flows/) | Mailchimp | Journey automation |
| `SRC-MAILCHIMP-20260520-002` | [Automation Flow Triggers](https://mailchimp.com/help/all-the-starting-points/) | Mailchimp | Triggers |
| `SRC-MAILCHIMP-20260520-003` | [Advanced Segment Builder](https://mailchimp.com/help/about-advanced-segmentation/) | Mailchimp | Segmentation |
| `SRC-MAILCHIMP-20260520-004` | [Intuit AI templates](https://mailchimp.com/help/create-pre-built-journey-emails-intuit-ai/) | Mailchimp | AI templates |
| `SRC-CUSTOMERIO-20260520-001` | [Introduction to Journeys](https://docs.customer.io/journeys/journeys-overview/) | Customer.io | Journeys |
| `SRC-CUSTOMERIO-20260520-002` | [How segments work](https://docs.customer.io/journeys/segments/) | Customer.io | Segments |
| `SRC-CUSTOMERIO-20260520-003` | [Data-driven segments](https://docs.customer.io/journeys/data-driven-segments/) | Customer.io | Behavioral segments |
| `SRC-CUSTOMERIO-20260520-004` | [AI segment builder](https://docs.customer.io/journeys/segment-builder/) | Customer.io | AI segmentation |

## Facts

- HubSpot workflows can be created from AI, templates, or scratch, and include enrollment triggers, re-enrollment, unenrollment, actions, and publishing controls. HubSpot email A/B tests route a sample to variants and send a winning version by selected metric.
- Braze Canvas and related docs position journey orchestration around visual paths, decisions, experiments, delays, message steps, segmentation, intelligent timing, and warmup/deliverability controls.
- Klaviyo flows are triggered by behavior, events, lists, checkout, orders, and dates, with flow analytics and predictive lifecycle signals for eligible ecommerce datasets.
- Mailchimp Automation Flows provide visual maps, starting points, flow points, templates, triggers, AI template generation, and segmentation capabilities with plan and automation constraints.
- Customer.io Journeys uses real-time people/object data, segments, campaigns, broadcasts, channel steps, webhooks, attribute updates, manual/data-driven segments, and AI segment generation based on attribute names rather than values.

## Inference For Legent

Competitor parity is converging around marketer-owned visual journeys, guided setup, dynamic behavioral segmentation, ecommerce and event triggers, cross-channel steps, predictive audience signals, step-level analytics, experimentation, and deliverability-aware sending. As of the 2026-05-22 local queue reconciliation, Legent has closed the local runtime-node contract and local flow-analytics/experimentation slices. That evidence supports internal queue state only; it does not establish competitor parity, production behavior, or deliverability/throughput outcomes.

## Gap Candidates

| Gap ID | Mode Impact | Severity | Queue Status |
|---|---|---|---|
| `journey-runtime-node-contract` | Beginner recipes and Advanced node library must reflect executable runtime behavior. | High | DONE_LOCAL as of 2026-05-22: runtime-supported node subset, capability reporting, publish/resume/rollback guardrails, and builder gating exist locally; broader journey-node runtime expansion remains future work. |
| `segment-builder-v2` | Beginner presets, Advanced nested behavioral filters, Admin governance. | Medium | SPLIT_PROPOSED: see `segment-builder-v2-*` descriptions below. |
| `mode-aware-workflow-contract` | BASIC and ADVANCED exist; role-gated Admin is not yet a product contract. | Medium | BACKLOG |
| `deliverability-aware-automation` | Warmup, engaged-first ramps, quiet hours, suppression, and volume caps must be journey-aware. | High | BACKLOG: related send handoff controls are split under `automation-send-activity-handoff`; live deliverability and delivery-policy snapshot evidence remain external. |
| `flow-analytics-experimentation` | Step metrics, conversion goals, observed path tests, campaign experiment metrics, and scope-separated reporting. | Medium | DONE_LOCAL as of 2026-05-22: bounded step/path/goal analytics and separated journey/campaign experiment scopes exist locally; causal winner-path reporting, anomaly accuracy, and production-volume evidence remain future work. |
| `ai-governance-optimization-foundation` | AI suggestions require opt-in, provider disclosure, audit, and privacy guardrails; local draft-only policy/audit scaffold exists, model-provider generation remains future work. | High | PARTIAL_LOCAL: see `ai-*` descriptions below. |
| `contact-builder-governance-split` | Relationship, provenance, retention, deletion, and sendable-key governance must stay separated from generic segmentation. | High | SPLIT_PROPOSED: see `contact-builder-*` descriptions below. |
| `delivery-policy-runtime-snapshot` | Send classification and provider/domain constraints must be immutable at execution time and backed by target evidence. | High | SPLIT_PROPOSED: see `delivery-policy-*` descriptions below. |

## Narrow Backlog Descriptions

These descriptions split broad parity themes into docs/backlog candidates without mutating queue state.

| Candidate | Description | Local Contract Boundary | Refresh / Evidence Requirement |
|---|---|---|---|
| `segment-builder-v2-rule-taxonomy` | Define static, dynamic, behavioral, computed, nested, null, timezone, consent, and send-eligibility rule semantics before UI expansion. | Local segment and predictive governance exists, but the complete v2 rule contract is future work. | Refresh segmentation competitor facts before public parity claims. |
| `segment-builder-v2-execution-plan` | Compile and evaluate segment rules with bounded reads, explain output, indexed execution, recompute scheduling, and failure handling. | Current local evidence does not prove indexed high-volume behavioral execution. | Requires backend tests and performance evidence before throughput claims. |
| `segment-builder-v2-governance-ui` | Add mode-aware presets, admin locks, PII classification constraints, preview warnings, and audit. | Existing UI is not proof of competitor-grade Segment Builder parity. | Use 2026-05-20 sources only until refreshed. |
| `automation-script-activity-security-sandbox` | Design signed script artifact execution with sandboxing, no ambient secrets, resource caps, egress/file limits, audit, and approval. | Script execution remains blocked locally. | Security design and sandbox validation required before implementation. |
| `automation-live-file-movement-storage-adapter` | Move file-drop and extract activities from validation-only history to tenant-scoped object movement with ownership, hash, size, type, and retention proof. | Artifact ownership exists; live movement remains future. | Needs storage adapter and target evidence. |
| `automation-target-runtime-replay-evidence` | Prove automation side effects survive Kafka/outbox replay, retry, and recovery without duplicate external effects. | Local idempotency exists for implemented families, not target replay proof. | Requires runtime evidence. |
| `ai-provider-contract-metering` | Add model-provider abstraction, policy disclosure, tenant opt-in/out, metering, audit, and kill switch before model-backed features. | Local policy/audit and deterministic intelligence exist; no proven provider-backed model calls. | Refresh official AI feature claims before parity language. |
| `ai-content-draft-application-workflow` | Route generated content through draft review, hash-only audit, human approval, and safe template application. | Draft-only governance exists; content workflow application remains future. | Needs content workflow tests and privacy review. |
| `ai-sto-runtime-scheduler` | Convert deterministic readiness checks into scheduled send-time decisions with confidence, fallback, and evidence. | Readiness/fallback contract exists; live scheduling does not. | Needs production-like evidence before optimization claims. |
| `ai-frequency-decision-runtime` | Apply live frequency/cadence decisions with caps, suppression safety, fallback, and audit. | Deterministic frequency readiness exists; live decisioning does not. | Needs campaign/delivery safety validation. |
| `ai-segment-workflow-generation-preview` | Generate segment or workflow drafts as non-executing previews with approval gates and audit. | No Salesforce-equivalent generation is proven locally. | Requires source refresh and model-safety validation. |
| `contact-builder-relationship-cardinality` | Model contact/data-extension relationships, cardinality, query preview, and admin governance separately from segmentation. | Data extensions exist; full Contact Builder relationship governance does not. | Requires schema/product design and migration proof. |
| `contact-provenance-import-population` | Populate contact/import provenance and field classification so downstream segmentation and deletion policy can be audited. | Governance metadata exists; historical population remains future. | Needs target migration and import tests. |
| `contact-retention-deletion-audit` | Add retention, deletion, suppression, preference, and send eligibility audit history for Contact Builder style governance. | Current local controls are partial. | Needs privacy/security review. |
| `contact-sendable-key-migration-proof` | Prove sendable-key uniqueness and migration behavior against target data before parity or production claims. | Local migration intent is not target data proof. | Requires Flyway/target data evidence. |
| `delivery-policy-runtime-snapshot-contract` | Persist immutable delivery-owned policy snapshots for execution, retries, feedback, and audit. | Content-service policies and campaign preflight exist; delivery-owned runtime snapshots do not. | Needs delivery/campaign/content contract tests. |
| `delivery-policy-ui-management-audit` | Add Admin policy management, defaults, overrides, approval history, and audit evidence. | Current local contract is backend-focused. | Needs frontend/admin validation. |
| `delivery-policy-legal-evidence-pack` | Attach legal/compliance review evidence to send classification, unsubscribe, suppression, and retention policy. | Local policy object does not prove legal compliance. | External review required. |
| `delivery-policy-provider-egress-proof` | Prove provider/domain egress, warmup, rate, capacity, and feedback-loop behavior for target environments. | Local controls do not prove provider capacity or deliverability outcomes. | External target evidence required. |

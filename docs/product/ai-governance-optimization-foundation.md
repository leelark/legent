# AI Governance And Optimization Foundation

Source refresh date: 2026-05-20.

This document defines the claim, data-use, audit, and safety baseline that must exist before Legent ships or claims model-backed AI features such as content assistance, send-time optimization, predictive segments, engagement or frequency optimization, decisioning, or autonomous campaign recommendations.

## Current Legent Boundary

Current local evidence shows deterministic intelligence and rules-based assistance, not true model-backed AI parity:

- `PerformanceIntelligenceController.java` exposes performance-intelligence APIs for personalization evaluation, optimization policies/evaluation, extension governance, operations assistance, and workflow benchmarks.
- `OperationsAssistanceService.java` computes severity, risk, checklists, and recommended actions from telemetry using deterministic thresholds and fixed rules.
- `ClosedLoopOptimizationService.java` scores consent, deliverability, engagement, revenue, consent-loop, send-time, and frequency optimization readiness risk through explicit rules and guardrails.
- Local source search found no model-provider integration for OpenAI, Anthropic, Vertex, Bedrock, prompt orchestration, embeddings, or LLM completion in `services`, `shared`, or `frontend`.

Legent can describe these surfaces as rules-based intelligence, heuristic scoring, or deterministic recommendations. It must not describe them as generative AI, predictive AI, model-backed AI, Einstein-equivalent, or autonomous optimization unless the implementation and validation evidence exists.

## Current Market Facts

These facts are used only as product and governance references. They do not prove Legent parity.

- Salesforce describes the Einstein Trust Layer as including CRM data grounding, sensitive-data masking, toxicity detection, audit trail and feedback, and zero-data-retention agreements with third-party LLM partners: https://developer.salesforce.com/docs/ai/agentforce/guide/trust.html
- Salesforce generative AI guidance states that human review remains important for external-facing model outputs and that the customer remains responsible for responses shared with end users: https://developer.salesforce.com/docs/ai/agentforce/guide/trust.html
- Salesforce Help search results for Einstein Send Time Optimization state that Marketing Cloud uses machine learning, weekly model recreation from engagement data, low-data fallback to a generalized model, and about 90 days of engagement data for email STO: https://help.salesforce.com/s/articleView?id=sf.mc_anb_einstein_sto_app.htm&language=en_US&type=0
- Salesforce Help search results for Einstein Engagement Scoring state that activation requires historical contact or subscriber engagement data, including at least 1,000 events in the prior 90 days, and that scores are typically available after implementation delay: https://help.salesforce.com/s/articleView?id=mktg.mc_anb_activate_einstein_engagement_scoring.htm&language=en_US&type=5
- Salesforce Help search results for Einstein Engagement Frequency state that frequency metrics derive from the prior 28 days, transactional and test sends are excluded, and at least five sending-frequency variants are required: https://help.salesforce.com/s/articleView?id=sf.mc_anb_eef.htm&type=5
- Salesforce model-card results for Einstein Engagement Frequency state that the model analyzes up to 90 days of engagement history, requires sufficient subscriber and frequency-variant data, excludes third-party, demographic, and rendered-content inputs, and refreshes scores/models weekly: https://help.salesforce.com/s/articleView?id=mktg.mktg_einstein_model_card_eef.htm&language=en_US&type=5
- Klaviyo Smart Sending documentation separates channel windows and notes that attempted delivery, not only successful inbox receipt, can reset a profile timer; transactional email is normally exempt from marketing smart-sending windows: https://help.klaviyo.com/hc/en-us/articles/115002779311
- Braze frequency-capping documentation distinguishes individual-level message caps from infrastructure rate limits and frames dynamic caps as a way to avoid over-messaging while preserving relevance: https://www.braze.com/resources/articles/whats-frequency-capping
- Salesforce model-card search results for Einstein Engagement Scoring describe model details, intended use, analyzed and excluded factors, and performance/data-richness metrics: https://help.salesforce.com/s/articleView?id=mktg.mktg_einstein_model_card_ees.htm&language=en_US&type=5
- HubSpot describes AI trust controls around admin data access controls, third-party provider contractual restrictions, model cards, limited vendor retention, and no third-party model training on customer data: https://www.hubspot.com/products/artificial-intelligence/ai-trust
- Braze public documentation describes send-time optimization as statistically analyzing past interactions and channel behavior to select likely engagement windows: https://www.braze.com/resources/articles/send-time-optimization

## Claim Taxonomy

| Claim Class | Allowed Legent Wording | Required Evidence Before Claim | Disallowed Without Evidence |
|---|---|---|---|
| Deterministic heuristic | Rules-based recommendation, deterministic risk score, heuristic checklist, policy simulation. | Source code showing explicit rules, tests for thresholds and fail-closed behavior. | AI, ML, predictive, generative, autonomous intelligence. |
| Predictive model | Model-backed prediction, likelihood score, predictive segment, send-time prediction. | Model provider or training/runtime architecture, model card, tenant data-use policy, minimum data thresholds, validation metrics, fallback behavior, audit trail. | Salesforce Einstein-equivalent, guaranteed uplift, guaranteed engagement. |
| Generative model | Drafted by AI, generated subject/body, AI-assisted content variation. | Provider disclosure, prompt/data classification, sensitive-data filtering, toxicity/safety checks, human review, output audit, opt-in policy, rollback path. | Autopublished AI content, compliance-safe by default, no-review content generation. |
| Decisioning | AI decision support, guarded recommendation, human-approved apply. | Decision rule, confidence or risk band, explainability fields, approval workflow, rollback snapshot, tenant/workspace audit, suppression/deliverability invariants. | Fully autonomous campaign changes, automatic audience expansion, send bypass. |
| Autonomous action | Auto-apply with guardrails for low-risk internal changes only. | Explicit policy mode, low-risk threshold, guardrail pass evidence, rollback snapshot, audit, human override, tests. | Autonomous external sends, autonomous compliance changes, autonomous deliverability bypass. |
| Evidence required | Evaluation preview, beta, research prototype. | Dated source and validation note. | Parity, production-ready, proven, enterprise-safe. |

## Data-Use Policy Baseline

Every future AI feature must declare a policy record before implementation. The record must be tenant and workspace scoped and include:

- Feature class and claim class.
- Provider name, model family, model version when known, deployment region, and whether a third-party processor is involved.
- Input data classes: prompt text, template body, brand kit, subscriber attributes, event history, campaign metrics, deliverability signals, suppression status, and free-form user text.
- Prohibited data classes by default: raw credentials, secrets, private keys, access tokens, refresh tokens, provider credentials, unmasked payment data, unsupported health data, and any customer data not required for the feature.
- Training stance: whether customer data is used to train, fine-tune, evaluate, or improve global models. The default must be no customer-data training unless a tenant admin explicitly opts in under a separate policy.
- Retention stance for prompts, outputs, embeddings, model traces, feedback, and audit summaries.
- Masking and minimization controls for PII and sensitive fields before provider handoff.
- Admin enablement, opt-in, opt-out, and kill-switch behavior.
- Metering fields for usage, cost, feature, tenant, workspace, actor, request, and model version.

## Feature Gates

### Content Assistance

- Default mode is draft-only and human-reviewed before send or publication.
- Current local implementation evidence is a foundation-service governance control plane in `AiContentAssistanceGovernanceService.java`, backed by `ai_content_assistance_policies` and `ai_content_assistance_audits`; it does not call model providers or generate content.
- Policy records require tenant/workspace scope, feature class, provider disclosure, allowed/prohibited data classes, training stance, retention, opt-in/out, kill switch, draft-only mode, and human review.
- Evaluation records block publish, auto-publish, send, and test-send actions; draft application requires human review and stores prompt/output evidence as hashes only.
- Prompts must use minimum necessary context. Brand kit and template fragments are allowed only under tenant policy.
- Subscriber attributes and event history are prohibited unless the tenant policy explicitly enables them for personalization generation.
- Outputs require safety checks, brand-policy checks, and audit records with prompt template version and output hash. Avoid storing raw customer-sensitive prompts unless policy permits it.
- Generated content must not claim guaranteed inbox placement or compliance.

### Send-Time Optimization

- Current implementation evidence is a deterministic governance contract in `ClosedLoopOptimizationService.java` plus campaign-owned approved send-time decision metadata consumed by scheduling and send-job creation. This is not model-backed STO, not per-recipient prediction, and not autonomous live scheduling.
- The `SEND_TIME` policy path evaluates data readiness, confidence, fallback, and safety gates before any recommendation can affect launch timing.
- Campaign scheduling can apply a send-time recommendation only from an immutable approved `SEND_TIME` decision snapshot with policy key, optimization run id, snapshot hash, original and recommended schedule, timezone, confidence, fallback mode, reason codes, approval evidence, rollback snapshot, and launch safety gates.
- Default readiness thresholds are at least 1,000 eligible engagement events, 500 eligible contacts, a 90-day lookback window, and 60% engagement-window coverage unless tenant policy guardrails override them.
- A lookback window below 28 days is treated as a data-quality risk. Low event/contact volume returns `fallbackMode=LOW_DATA_DEFAULT_SCHEDULE`, `confidenceBand=LOW`, data-quality reasons, and a fallback recommendation.
- Commercial STO evaluation blocks use of transactional engagement data unless the policy explicitly allows it. Transactional sends require a separate transactional send-time policy unless explicitly enabled.
- Launch-time changes require human approval and rollback evidence even when every safety gate passes.
- Launch-time changes are blocked unless quiet-hours, campaign approval, suppression, warmup, rate-limit, provider-capacity, and deliverability gates have passed.
- Campaign schedule, Launch Command Center handoff, send-job creation, and due-job dispatch revalidate the approved snapshot and fail closed on missing evidence, low confidence, fallback mode, blocked reasons, timezone mismatch, quiet-hours/send-window violation, stale/mismatched scheduled time, or failed safety gate.
- Auto-apply of send-time launch changes is blocked in this governance slice.
- Requires an explicit data-readiness threshold before personalized predictions are shown.
- Must define low-data fallback behavior, such as tenant-level generalized scheduling or default schedule, and label it as fallback.
- Must exclude transactional sends from commercial-engagement training or evaluation unless the policy explicitly supports a separate transactional model.
- Must preserve quiet hours, timezone policy, suppression, warmup, provider capacity, rate limits, campaign approvals, and deliverability gates.
- Must expose confidence, data quality, and reason codes before recommendations can affect launch timing.

### Predictive Segments

- Requires data provenance, feature-source disclosure, minimum event/contact counts, freshness thresholds, and bias/drift checks.
- Must not infer or target protected/sensitive categories unless explicitly lawful, tenant-approved, and implemented with compliance review.
- Must expose why a segment was built, what data classes influenced it, and whether the result is modeled or rule-derived.
- Audience-changing recommendations require human approval, preview counts, suppression impact, rollback snapshots, and tests for tenant/workspace isolation.

### Segment And Workflow Generation Preview

- Current implementation evidence is a provider-free, deterministic preview contract in `AiGenerationPreviewService.java` under `POST /api/v1/performance-intelligence/ai-segments/preview` and `POST /api/v1/performance-intelligence/ai-workflows/preview`.
- The preview composes existing AI content governance audit and AI provider metering controls, but returns `providerInvoked=false`, `modelInvocation=NOT_PERFORMED`, `modelBacked=false`, `previewOnly=true`, `applyAllowed=false`, `activationAllowed=false`, and `publishAllowed=false`.
- Segment preview output is rule-derived only. It emits `BOUNDED_SQL` execution-plan metadata, supported operator/field constraints, custom-field and wildcard-scan warnings, and rejects relationship/data-extension traversal, reserved fields, unsupported operators, and missing values before audit side effects.
- Workflow preview output is graph v2 only and uses the current live runtime-supported node subset: `ENTRY_TRIGGER`, `DELAY`, optional `SEND_EMAIL` by `campaignId`, and `END`. It does not emit webhook/provider/sender/domain/governance/safety overrides.
- Segment preview requires `audience:write`; workflow preview requires `workflow:write`. The existing workspace filter covers the performance-intelligence prefix, and the service rejects secret-like context keys before governance or metering calls.
- This is not model-backed generation, not Salesforce-equivalent generation, not a publish/apply workflow, and not runtime proof. Frontend UX, audience/automation apply flows, model-provider generation, stronger provider-specific data processing controls, and target runtime evidence remain separate work.

### Frequency Optimization

- Current implementation evidence is a deterministic governance contract in `ClosedLoopOptimizationService.java` plus campaign-owned approved frequency decision metadata that can only hold or reduce the runtime cap. This is not model-backed engagement-frequency optimization and not autonomous live cadence control.
- Policy scope is tenant, workspace, and environment scoped. Commercial, transactional, and test sends must be classified separately; commercial optimization must exclude transactional and test-send history unless a separate transactional policy explicitly exists.
- Data readiness requires a defined lookback window, minimum eligible send events and contacts, minimum frequency variants, and data freshness. Default contract minimums are a 28-day active metric window, 90-day richer history when available, at least 5 sending-frequency variants, at least 1,000 eligible send events, and at least 500 eligible contacts unless a tenant policy tightens them.
- Low-data evaluation must return a low-confidence fallback such as `LOW_DATA_CURRENT_CAP`; it must not present personalized cadence certainty or increase a cap.
- Must treat fatigue, unsubscribe, complaint, bounce, suppression, warmup rollback, provider block, rate-limit block, current cap utilization, and frequency-ledger state as safety signals, not only optimization features.
- Must never increase send frequency for a recipient or cohort that is suppressed, unsubscribed, complaint-prone, bounce-risk, warmup-blocked, over cap, provider-blocked, or rate-limit-blocked.
- Reputation and deliverability inputs must be tenant/workspace scoped. Do not use legacy domain-only reputation records for frequency decisions, and treat missing sent-count or unavailable reputation evidence as low-confidence/no-increase.
- Delivery safety inputs must remain authoritative after a recommendation is approved. Warmup capacity, provider health, circuit state, provider/domain capacity, inbox safety, and send-rate reservations can only further reduce or defer sends; frequency optimization must not override them.
- When risk tiers differ across delivery services, the frequency contract must use the stricter effective tier/cap and expose the tier source in reason codes before recommending an increase.
- Campaign interaction points are launch/preflight for advisory blockers and `CampaignSendSafetyService.prepareRecipient` for final per-recipient enforcement. Scheduled sends must either revalidate readiness at execution time or carry an immutable approved frequency snapshot before publishing audience resolution.
- Campaign send safety uses approved frequency decision evidence only as a conservative cap reducer: the effective cap is the lower of the configured campaign cap and the approved recommended cap. Cap increases remain blocked for a separate reviewed runtime slice.
- Recommendation output must expose saturation category, current cap, recommended cap, confidence band, fallback mode, data-quality reasons, expected safety impact, and blocker reason codes.
- Cap increases and other cadence changes require human approval and rollback evidence, and are blocked unless suppression, unsubscribe/preference, warmup, rate-limit, provider-capacity, deliverability, and frequency-ledger gates have passed.
- Auto-apply remains blocked in this governance slice. Future live cadence control must prove campaign/journey integration, policy versioning, tenant/workspace audit, rollback snapshot, and target-environment provider evidence before release claims.
- Focused validation map: foundation policy tests cover low-data fallback, unsafe cap increases, approval and rollback flags, saturation output, confidence band, recommended cap, and gate blockers; campaign send-safety tests cover tenant/workspace frequency caps before delivery handoff; delivery/deliverability follow-ups must keep warmup, provider capacity, reputation, suppression, and rate controls authoritative at send time.

## Audit And Review Requirements

All AI and model-backed optimization features must emit audit records that can answer:

- Who enabled the feature, in which tenant/workspace/environment, and under which policy version.
- What provider and model version were used, if any.
- What data classes were used and which were masked, excluded, or blocked.
- Which thresholds, confidence bands, safety checks, and guardrails applied.
- Whether the feature produced a draft, recommendation, decision, auto-apply action, or blocked result.
- Which human approved, rejected, edited, or applied the recommendation.
- Which campaign, audience, content, journey, or operational artifact was affected.
- Which rollback snapshot or evidence reference can restore or explain the decision.

For external-facing content, human review is mandatory until the feature has explicit tenant policy, QA evidence, safety tests, and release approval for a narrower no-review mode. No AI feature may bypass existing approval, suppression, unsubscribe, warmup, rate control, signed tracking, content safety, or provider health checks.

## Governance Slice Status

The backlog must split model-backed AI work into separate implementation slices. Completed deterministic governance slices are product-contract evidence only; they do not prove model-backed AI or live autonomous action.

| Backlog ID | Purpose | Status | First Validation |
|---|---|---|---|
| `ai-content-assistance-governance` | Implement tenant/workspace AI policy and audit scaffolding for draft-only content assistance. | DONE_LOCAL_CONTRACT | `AiContentAssistanceGovernanceServiceTest`; `.\\mvnw.cmd -pl services/foundation-service,services/content-service -am test`. |
| `send-time-optimization-governance` | Implement deterministic STO data-readiness, fallback, confidence, and send-path safety contracts before scheduling changes. | DONE_LOCAL_CONTRACT | Focused foundation policy/evaluation tests plus campaign/delivery integration follow-up before runtime scheduling. |
| `predictive-segments-governance` | Define predictive segment data provenance, threshold, preview, approval, and rollback controls. | DONE_LOCAL_CONTRACT | Audience/foundation tests for tenant isolation, preview counts, and policy denial. |
| `frequency-optimization-governance` | Define frequency optimization lookback, variants, fatigue safety, and deliverability guardrails. | DONE_LOCAL_CONTRACT | Foundation frequency policy tests plus campaign send-safety frequency cap tests; delivery/deliverability runtime evidence remains follow-up. |
| `ai-frequency-decision-runtime` | Carry approved frequency decision evidence into campaign frequency policy and enforce it only as a cap reducer in send safety. | DONE_LOCAL_CONTRACT | `CampaignEngineServiceTest`; `CampaignSendSafetyServiceTest`; broader delivery/deliverability evidence remains separate. |
| `ai-sto-runtime-scheduler` | Carry approved send-time decision evidence into campaign scheduling and scheduled send-job dispatch. | DONE_LOCAL_CONTRACT | `OptimizationPerformanceServiceTest`; `CampaignServiceScheduleTest`; `OrchestrationServiceTest`; `SchedulingServiceTest`; `CampaignLaunchReadinessGateTest`. |
| `ai-segment-workflow-generation-preview` | Add provider-free segment/workflow preview drafts behind existing governance, metering, RBAC, hash-only audit, and no-activation controls. | DONE_LOCAL_CONTRACT | `AiGenerationPreviewServiceTest`; `PerformanceIntelligenceControllerSecurityTest`; broader audience/automation apply UX and model-backed generation remain separate. |

Open follow-ups remain separate from these governance contracts: model/provider integration, generated draft application in content workflows, live cadence control, recipient-level or journey-level STO, provider-capacity proof, and production evidence. None should call external model APIs or add provider credentials without a separate security and data-processing review.

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
- Prompts must use minimum necessary context. Brand kit and template fragments are allowed only under tenant policy.
- Subscriber attributes and event history are prohibited unless the tenant policy explicitly enables them for personalization generation.
- Outputs require safety checks, brand-policy checks, and audit records with prompt template version and output hash. Avoid storing raw customer-sensitive prompts unless policy permits it.
- Generated content must not claim guaranteed inbox placement or compliance.

### Send-Time Optimization

- Current implementation evidence is a deterministic governance contract in `ClosedLoopOptimizationService.java`, not model-backed STO and not live send scheduling.
- The `SEND_TIME` policy path evaluates data readiness, confidence, fallback, and safety gates before any future recommendation could affect launch timing.
- Default readiness thresholds are at least 1,000 eligible engagement events, 500 eligible contacts, a 90-day lookback window, and 60% engagement-window coverage unless tenant policy guardrails override them.
- A lookback window below 28 days is treated as a data-quality risk. Low event/contact volume returns `fallbackMode=LOW_DATA_DEFAULT_SCHEDULE`, `confidenceBand=LOW`, data-quality reasons, and a fallback recommendation.
- Commercial STO evaluation blocks use of transactional engagement data unless the policy explicitly allows it. Transactional sends require a separate transactional send-time policy unless explicitly enabled.
- Launch-time changes require human approval and rollback evidence even when every safety gate passes.
- Launch-time changes are blocked unless quiet-hours, campaign approval, suppression, warmup, rate-limit, provider-capacity, and deliverability gates have passed.
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

### Frequency Optimization

- Current implementation evidence is a deterministic governance contract in `ClosedLoopOptimizationService.java`, not model-backed engagement-frequency optimization and not live cadence control.
- Requires a defined lookback window, minimum frequency variants, and low-data fallback classification.
- Must treat fatigue, unsubscribe, complaint, bounce, and suppression signals as safety signals, not only optimization features.
- Must never increase send frequency for a recipient who is suppressed, unsubscribed, complaint-prone, warmup-blocked, over cap, or provider-blocked.
- Must expose saturation categories, recommended cap, confidence band, and expected safety impact.
- Cadence changes require human approval and rollback evidence, and cap increases are blocked unless suppression, unsubscribe/preference, warmup, rate-limit, provider-capacity, deliverability, and frequency-ledger gates have passed.

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

## Required Follow-Up Slices

The backlog must split model-backed AI work into separate implementation slices:

| Backlog ID | Purpose | First Validation |
|---|---|---|
| `ai-content-assistance-governance` | Implement tenant/workspace AI policy and audit scaffolding for draft-only content assistance. | Focused foundation/content tests plus docs and artifact hygiene. |
| `send-time-optimization-governance` | Implement deterministic STO data-readiness, fallback, confidence, and send-path safety contracts before scheduling changes. | Focused foundation policy/evaluation tests plus campaign/delivery integration follow-up before runtime scheduling. |
| `predictive-segments-governance` | Define predictive segment data provenance, threshold, preview, approval, and rollback controls. | Audience/foundation tests for tenant isolation, preview counts, and policy denial. |
| `frequency-optimization-governance` | Define frequency optimization lookback, variants, fatigue safety, and deliverability guardrails. | Deliverability/campaign tests for suppressions, caps, and fail-closed policy. |

These slices remain BACKLOG until their implementation scope, schema/API shape, and validation plans are narrowed. None should call external model APIs or add provider credentials without a separate security and data-processing review.

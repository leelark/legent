# Competitor Capability Scan

Access date: 2026-05-20.

Scope: official or primary vendor sources for current email marketing automation parity signals. Facts are summarized for backlog routing.

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

Competitor parity is converging around marketer-owned visual journeys, guided setup, dynamic behavioral segmentation, ecommerce and event triggers, cross-channel steps, predictive audience signals, step-level analytics, experimentation, and deliverability-aware sending. Legent should prioritize runtime correctness and safety before broadening the visual surface.

## Gap Candidates

| Gap ID | Mode Impact | Severity | Queue Status |
|---|---|---|---|
| `journey-runtime-node-contract` | Beginner recipes and Advanced node library must reflect executable runtime behavior. | High | READY |
| `segment-builder-v2` | Beginner presets, Advanced nested behavioral filters, Admin governance. | Medium | PROPOSED |
| `mode-aware-workflow-contract` | BASIC and ADVANCED exist; role-gated Admin is not yet a product contract. | Medium | BACKLOG |
| `deliverability-aware-automation` | Warmup, engaged-first ramps, quiet hours, suppression, and volume caps must be journey-aware. | High | BACKLOG |
| `flow-analytics-experimentation` | Step metrics, conversion goals, A/B, random cohorts, and winning-path reporting. | Medium | BACKLOG |
| `ai-governance-optimization-foundation` | AI suggestions require opt-in, provider disclosure, audit, and privacy guardrails. | High | BACKLOG |

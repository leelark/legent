# Autonomous Organization Rebuild Audit

Date: 2026-05-20.

## Scope

This audit rebuilt the Legent project-local autonomous engineering organization around current repository facts.

## Current Product Baseline

Legent has broad enterprise email marketing coverage: identity, tenant/workspace governance, audience/data extensions, content, campaigns, delivery, tracking, automation, deliverability, integrations, admin, local runtime, and Kubernetes manifests.

Legent is not public-GA ready. Missing proof is mostly external and operational: production egress, image supply chain, live load, restore, monitoring, TLS/admission, and CI/security evidence.

## Market Capability Baseline To Preserve

Use current official sources when making product parity claims. The baseline categories are:

- Audience/contact data: data extensions, imports, preferences, suppression, consent, segmentation, SQL/query activities.
- Email/content studio: editors, blocks, personalization, dynamic content, brand kits, previews, test sends, legal/unsubscribe validation.
- Campaign/send flow: batch, scheduled, recurring, triggered, transactional, API-triggered, approvals, exclusions, frequency caps, throttling, diagnostics.
- Journey/automation: canvas, entry sources, waits, decisions, goals, exits, versioning, simulation, live monitoring, run history, retries.
- Deliverability/compliance: SPF, DKIM, DMARC, warmup, branded tracking, provider/domain rate control, one-click unsubscribe, feedback loops.
- Analytics/operations: campaign, journey, deliverability, audience, revenue/conversion, raw event analytics, exports, alerts.
- Enterprise/admin: RBAC, SSO/SCIM, audit, workspaces/business units, environments, webhooks, API keys/scopes, evidence handling.
- AI/optimization: content assistant, send-time optimization, predictive segments, frequency optimization, next-best action, anomaly/root-cause assistant.

## Safety Baseline

Legent can optimize deliverability but cannot guarantee inbox placement. The 10 lakh in 10 hours objective is a mature-throughput certification target, not a default product promise and not valid for new or unwarmed senders.

## Sources

- Salesforce Marketing Cloud Engagement overview: https://www.salesforce.com/marketing/engagement/
- Salesforce Trailhead Marketing Cloud basics: https://trailhead.salesforce.com/content/learn/modules/mrkt_cloud_basics/mrkt_cloud_basics_get_started
- Salesforce service descriptions: https://help.salesforce.com/s/articleView?id=xcloud.basics_sf_service_descriptions.htm&language=en_US&type=5
- Adobe Journey Optimizer documentation: https://experienceleague.adobe.com/docs/journey-optimizer/using/get-started/get-started.html
- Iterable journeys: https://iterable.com/product/journeys/
- Braze platform overview: https://www.braze.com/
- Klaviyo features: https://www.klaviyo.com/features
- HubSpot marketing email: https://www.hubspot.com/products/marketing/email
- Google sender guidelines: https://support.google.com/a/answer/81126
- Yahoo sender best practices: https://senders.yahooinc.com/best-practices/

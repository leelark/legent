# Deliverability And Safety

Legent must describe deliverability as optimization, not guarantee.

## Product Copy Rule

Allowed:

- "Improve deliverability readiness."
- "Verify authentication and warmup posture."
- "Throttle sends based on provider and domain capacity."
- "Monitor bounces, complaints, and engagement."

Forbidden:

- "Guaranteed inbox placement."
- "Send 10 lakh emails from a new sender in 10 hours."
- "Bypass warmup for urgent campaigns."
- "Ignore suppressions to maximize reach."

## Launch Safety

Campaign launch must preserve:

- SPF, DKIM, DMARC/domain readiness checks.
- Sender/domain warmup.
- Suppression and preference checks.
- One-click unsubscribe/legal requirements where applicable.
- Provider/domain rate limits and health.
- Signed tracking/link safety.
- Bounce/complaint feedback loops.
- Retry/DLQ and observability.

## Validation Map

| Safety invariant | Local validation or evidence | External blocker before production claim | Decision boundary |
| --- | --- | --- | --- |
| No guaranteed inbox-placement or forced high-volume claim | This document, product parity notes, and release evidence docs require optimization language only. | Provider inbox placement is outside repository control and depends on mailbox providers, recipient behavior, reputation, and live policy. | Do not claim guaranteed inbox placement or 10 lakh in 10 hours from a new or unwarmed sender. |
| SPF, DKIM, DMARC, sender-domain readiness | Deliverability domain verification, DMARC, reputation, and launch-safety tests cover local readiness gates. | `external-provider-capacity` remains blocked until target DNS, provider, feedback-loop, and warmup evidence exists. | Local checks can block unsafe launch; they cannot prove target DNS/provider state without live evidence. |
| Warmup, provider capacity, and rate control | Warmup, reputation, provider capacity, delivery reservation, and campaign send-safety tests cover local fail-closed behavior. | `live-high-volume-proof` and `external-provider-capacity` require warmed domains, approved provider limits, and target load evidence. | Throughput claims require mature sender reputation and provider-approved capacity, not only passing unit tests. |
| Suppression, unsubscribe, preferences, and legal safety | Audience suppression, deliverability suppression, campaign eligibility, and unsubscribe flows are locally covered by focused tests. | Production compliance evidence still requires target tenant policy, unsubscribe endpoint, suppression import, and feedback-loop verification. | Suppressed or opted-out recipients must remain excluded even when it reduces campaign reach. |
| Signed tracking and outbound link safety | Tracking signature, outbound URL guard, route-map, Nginx, and ingress validators protect local route and link semantics. | Target TLS, ingress, public domain, and synthetic tracking evidence are part of `production-evidence-pack`. | Public tracking can be considered releasable only after target ingress and signed-link evidence is present. |
| Bounce, complaint, retry, DLQ, and feedback loops | Delivery feedback outbox, Kafka topic coverage, campaign feedback reconciliation, and DLQ-focused tests cover local durability and retry paths. | Provider webhook/FBL proof, broker partition/retention policy, and target outbox lag evidence remain external. | Local durable outbox behavior is necessary but not sufficient for production feedback-loop readiness. |
| 10 lakh in 10 hours objective | Local batching, audience chunking, rate reservation, feedback backpressure, and tracking dedupe controls reduce high-volume risk. | `live-high-volume-proof` requires bounded staging/load evidence with warmed domains, provider approvals, queue lag, tracking isolation, and retry/backpressure proof. | Treat this as a mature, warmed, provider-approved throughput target only. |
| Observability and release handoff | Local release validators, dashboard state, audit events, and monitoring docs preserve evidence accounting. | `production-evidence-pack` remains blocked until CI/security transcript, image provenance, restore drill, target monitoring, TLS/admission, and egress evidence are collected. | Do not mark public multi-tenant GA or production readiness without strict release evidence. |

## External Guidance

- Google sender guidelines: https://support.google.com/a/answer/81126
- Yahoo sender best practices: https://senders.yahooinc.com/best-practices/

# Deployment Manager Provider Options

Date: 2026-05-23
Owner: RELEASE_MANAGER
Status: Local readiness plan complete; production claims remain evidence-bound.

## Purpose

This guide turns the requested maturity targets into a Deployment Manager operating model. It defines what can reach 100% locally, what needs free external setup, and what needs paid production services before Legent can claim production readiness, Salesforce-class parity, AI parity, or 10 lakh / 10h sending readiness.

## Completion Boundary

| Area | Requested target | Local completion after this guide | Claimable production status |
|---|---:|---:|---|
| Frontend local maturity | 100% | 100% path defined through settings UI, lint, build, and Playwright smoke | Needs target browser/session transcript |
| Infra/release controls | 100% | 100% path defined through compose, route, overlay, hygiene, and release validators | Needs strict non-local release evidence |
| Production readiness | 100% | 100% local checklist and runtime setting map | Blocked until target runtime evidence exists |
| Salesforce-class product parity | 100% | 100% parity roadmap boundary defined | Blocked until missing product capabilities are implemented and verified |
| 10 lakh / 10h send readiness | 100% | 100% architecture checklist defined | Blocked until provider quota, warmed reputation, and target load proof exist |
| AI parity | 100% | 100% governance and provider checklist defined | Blocked until AI provider, evals, safety, cost, and product UX evidence exists |

Final interpretation: repository-only work can complete the local plan, configuration surface, and validation map. It cannot honestly convert external evidence gaps into production proof.

## Runtime Settings

| Key | Free/local value | Paid production value | Where configured |
|---|---|---|---|
| `deployment.track` | `LOCAL_VALIDATION` | `PRODUCTION_MANAGED` | Settings > Deployment, admin runtime config |
| `deployment.evidence.dir` | `docs/release-evidence/local` | `docs/release-evidence/production` | release evidence pack |
| `deployment.egress.evidence.path` | local outbound mock transcript | production egress allowlist and NAT/provider proof | release gate input |
| `deployment.image.evidence.manifest` | local image metadata | registry digest, SBOM, signature, provenance | release gate input |
| `delivery.provider.mode` | `MAILHOG` or test SMTP | `AMAZON_SES`, `SENDGRID`, or `MAILGUN` | runtime config and provider secrets |
| `delivery.provider.capacityProfileId` | `local-validation-profile` | provider-approved capacity profile | delivery rate policy |
| `ai.provider.mode` | `LOCAL_STUB` or `OFF` | `OPENAI` or `AZURE_OPENAI` | AI service config and secrets |
| `tracking.analytics.clickhouse.mode` | local ClickHouse | managed ClickHouse or production cluster | analytics config |
| `observability.provider` | local Prometheus/Grafana logs | Datadog, Grafana Cloud, or equivalent pager stack | observability config |
| `release.strictEvidenceRequired` | `true` | `true` | release gate |

Secrets must stay outside the repository. Runtime config may store non-secret mode and policy values; API keys, SMTP passwords, signing keys, and provider tokens belong in environment secrets or ExternalSecrets.

## Free Validation Path

Use this path to reach 100% local validation confidence, not production proof.

| Capability | Free option | Evidence to attach |
|---|---|---|
| App runtime | Docker Compose | `docker compose config --quiet`, local health transcript |
| Email delivery | MailHog or local SMTP sink | send transcript showing no real recipient delivery |
| TLS | Let's Encrypt for a controlled public domain, or local TLS for dev | certificate/renewal transcript or local TLS note |
| DNS | Cloudflare Free primary DNS or existing registrar DNS | SPF, DKIM, DMARC record screenshots/transcripts |
| CI | GitHub Actions included/free minutes or self-hosted runner | latest CI/security transcript |
| Load harness | local k6/JMeter and synthetic recipients | non-provider load report and resource metrics |
| Observability | local logs, Prometheus, Grafana | dashboard screenshots and alert dry-run |
| AI | local stub provider | deterministic eval transcript and disabled real-cost mode |

Free path limitation: it cannot prove production egress, real provider quota, sender reputation, dedicated IP warmup, restore drill in target infrastructure, live synthetic smoke, or 10 lakh / 10h delivery.

## Paid Production Path

Use this path before any production or high-volume claim.

| Capability | Paid options | Evidence required |
|---|---|---|
| Runtime | EKS, GKE, AKS, managed Kubernetes, or managed app platform | deployment manifest, pod health, ingress, TLS, network policy |
| Database/cache | managed PostgreSQL, Redis, Kafka, object storage, ClickHouse/OpenSearch | backup/restore drill, retention, encryption, scaling limits |
| Email provider | Amazon SES, Twilio SendGrid, Mailgun, or approved SMTP/API vendor | quota, dedicated/managed IP posture, domain authentication, bounce/complaint feedback |
| Sender reputation | warmed dedicated IP/pool or provider-approved shared pool | warmup status, traffic ramp, complaint/bounce trends |
| Observability | Datadog, Grafana Cloud, PagerDuty, CloudWatch, or equivalent | SLO dashboard, alerts, on-call handoff |
| Registry/supply chain | managed container registry and signing | digest, SBOM, signature, provenance, vulnerability scan |
| AI provider | OpenAI or Azure OpenAI with project/rate/cost controls | evals, moderation/safety evidence, usage limits, audit trail |

Production path limitation: even paid services do not guarantee inbox placement. They only make capacity, authentication, feedback, observability, and operational evidence available.

## Category Gates

### Frontend Local Maturity

100% local requires:
- `cd frontend; npm run lint`
- `cd frontend; npm run build:ci`
- `cd frontend; .\node_modules\.bin\playwright.cmd test tests/e2e/admin.spec.ts --project=chromium --reporter=line`
- Browser/session evidence for touched settings routes when a visible workflow changes.

### Infra and Release Controls

100% local requires:
- `docker compose config --quiet`
- `powershell -ExecutionPolicy Bypass -File scripts\ops\validate-route-map.ps1`
- `powershell -ExecutionPolicy Bypass -File scripts\ops\validate-production-overlay.ps1`
- `powershell -ExecutionPolicy Bypass -File scripts\ops\validate-repo-artifact-hygiene.ps1`
- `powershell -ExecutionPolicy Bypass -File scripts\ops\test-release-evidence-validators.ps1`
- Strict release gate dry run with documented skips only when local evidence is intentionally used.

### Production Readiness

100% claimable production readiness requires all release evidence pack items:
- image digest, SBOM, signature, and provenance
- production egress evidence
- GA/synthetic smoke evidence
- restore drill
- CI/security transcript
- TLS/admission/network policy evidence
- monitoring and incident handoff
- target runtime health with no critical startup errors

### Salesforce-Class Product Parity

100% implementation parity requires verified capabilities across:
- journeys and automation branching
- segmentation, data extensions, and imports
- content builder, dynamic content, approvals, and test sends
- A/B or multivariate testing
- deliverability governance and suppression handling
- analytics, attribution, and experimentation
- integrations, webhooks, audit, and role governance
- AI-assisted content, segmentation, recommendations, and operations assistance

Current repo evidence supports a roadmap and many foundations, not full Salesforce implementation parity.

### 10 Lakh / 10h Send Readiness

100% claimable throughput requires:
- warmed sender domains and aligned SPF/DKIM/DMARC
- provider-approved quota for at least 100,000 messages/hour plus retry headroom
- chunked audience resolution and idempotent batch IDs
- Kafka partition, lag, retry, and DLQ evidence
- provider health, rate reservation, warmup, and suppression in the send path
- ClickHouse/tracking ingestion isolation under load
- bounce, complaint, unsubscribe, and feedback-loop reconciliation
- target-like load report with SLOs and failure budget

Do not claim this for new domains, unwarmed IPs, unapproved provider quotas, or local-only MailHog runs.

### AI Parity

100% claimable AI parity requires:
- approved provider mode, rate limits, spend limits, and audit logs
- prompt/version governance and deterministic fallback behavior
- evals for content, segmentation, operations assistance, and safety
- moderation/safety handling for generated content
- user review and approval before campaign-impacting actions
- tenant/workspace isolation for prompts, generated drafts, and logs
- production monitoring for cost, latency, failures, and model quality drift

## Source References Checked

- Amazon SES production and sending controls: https://docs.aws.amazon.com/ses/latest/dg/request-production-access.html, https://docs.aws.amazon.com/ses/latest/dg/quotas.html, https://docs.aws.amazon.com/ses/latest/dg/dedicated-ip.html
- Twilio SendGrid dedicated IP guidance: https://support.sendgrid.com/hc/en-us/articles/9237413560219-Dedicated-IP-Addresses
- Mailgun warmup and best practices: https://documentation.mailgun.com/docs/inboxready/api-reference/optimize/mailgun/ip-address-warmup, https://documentation.mailgun.com/docs/mailgun/email-best-practices/ip_address/
- Let's Encrypt documentation: https://letsencrypt.org/docs/
- Cloudflare DNS setup: https://developers.cloudflare.com/dns/zone-setups/
- GitHub Actions billing/free use: https://docs.github.com/en/billing/concepts/product-billing/github-actions
- Salesforce Marketing Cloud pricing/capability positioning: https://www.salesforce.com/products/marketing-cloud/pricing/email-mobile-web-marketing/
- Salesforce data extensions and segmentation: https://trailhead.salesforce.com/en/content/learn/modules/marketing-cloud-contact-management/learn-about-data-extensions
- Salesforce Einstein Marketing Cloud help: https://help.salesforce.com/s/articleView?id=marketing_cloud_einstein.htm&type=5
- HubSpot automated A/B email testing: https://knowledge.hubspot.com/workflows/automate-ab-emails-with-workflows
- Mailchimp journey automation and A/B testing: https://mailchimp.com/guesswork/customer-journey-builder/, https://mailchimp.com/help/create-ab-tests/
- OpenAI production and evaluation guidance: https://platform.openai.com/docs/guides/production-best-practices, https://platform.openai.com/docs/guides/evaluation-best-practices

## Final Conclusion

Legent can be brought to 100% local readiness planning for the requested categories through the Deployment Manager surface, this guide, and the existing validation gates. True 100% production readiness, Salesforce-class parity, AI parity, and 10 lakh / 10h send readiness remain blocked until paid or target external services provide dated evidence and the missing product capabilities are implemented and validated.

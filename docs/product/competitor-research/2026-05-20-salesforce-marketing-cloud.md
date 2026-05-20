# Salesforce Marketing Cloud Research

Access date: 2026-05-20.

Scope: current official Salesforce Help sources for Marketing Cloud Engagement parity. Facts below are summarized; URLs are the source of record.

## Source Register

| ID | Source | Vendor | Area |
|---|---|---|---|
| `SRC-SFMC-20260520-001` | [Email overview](https://help.salesforce.com/s/articleView?id=mktg.mc_email.htm&language=en_US&type=5) | Salesforce | Email Studio |
| `SRC-SFMC-20260520-002` | [Send Email](https://help.salesforce.com/s/articleView?id=mktg.mc_es_send_email.htm&language=en_US&type=5) | Salesforce | Email Studio |
| `SRC-SFMC-20260520-003` | [Email Settings](https://help.salesforce.com/s/articleView?id=mktg.mc_overview_email_studio_settings.htm&language=en_US&type=5) | Salesforce | Email Studio and deliverability |
| `SRC-SFMC-20260520-004` | [Send Classifications](https://help.salesforce.com/s/articleView?id=mktg.mc_es_send_classifications.htm&language=en_US&type=5) | Salesforce | Send governance |
| `SRC-SFMC-20260520-005` | [Contact Builder](https://help.salesforce.com/s/articleView?id=mktg.mc_cab_contact_builder.htm&language=en_US&type=5) | Salesforce | Contact model |
| `SRC-SFMC-20260520-006` | [Manage Data Extensions](https://help.salesforce.com/s/articleView?id=mktg.mc_cab_data_extensions_manage.htm&language=en_US) | Salesforce | Data extensions |
| `SRC-SFMC-20260520-007` | [Data Designer](https://help.salesforce.com/s/articleView?id=mktg.mc_cab_data_designer.htm&language=en_US&type=5) | Salesforce | Contact relationships |
| `SRC-SFMC-20260520-008` | [Journeys and Messages](https://help.salesforce.com/s/articleView?id=mktg.mc_jb_journey_builder.htm&language=en_US&type=5) | Salesforce | Journey Builder |
| `SRC-SFMC-20260520-009` | [Entry Sources](https://help.salesforce.com/s/articleView?id=000232652&language=en_US&type=1) | Salesforce | Journey entry |
| `SRC-SFMC-20260520-010` | [Journey Activities](https://help.salesforce.com/s/articleView?id=mktg.mc_jb_canvas_activities.htm&language=en_US&type=5) | Salesforce | Journey actions |
| `SRC-SFMC-20260520-011` | [High-Throughput Sending](https://help.salesforce.com/s/articleView?id=mktg.mc_jb_high_throughput_sending.htm&language=en_US&type=5) | Salesforce | Journey send throughput |
| `SRC-SFMC-20260520-012` | [Automation Studio](https://help.salesforce.com/s/articleView?language=en_US&id=mktg.mc_as_automation_studio.htm&type=5) | Salesforce | Automation Studio |
| `SRC-SFMC-20260520-013` | [Automation Activities](https://help.salesforce.com/articleView?id=mc_as_using_automation_studio_activities.htm&type=5) | Salesforce | Automation activities |
| `SRC-SFMC-20260520-014` | [Bulk Sender Guidelines](https://help.salesforce.com/s/articleView?id=mktg.mc_es_bulk_sender_guidelines.htm&language=en_US&type=5) | Salesforce | Deliverability |
| `SRC-SFMC-20260520-015` | [Audit Trail](https://help.salesforce.com/s/articleView?id=mktg.mc_overview_audit_trail.htm&language=en_US&type=5) | Salesforce | Audit |
| `SRC-SFMC-20260520-016` | [Einstein overview](https://help.salesforce.com/s/articleView?id=mktg.mc_ees_einstein_feature_overview.htm&language=en_US&type=5) | Salesforce | AI |
| `SRC-SFMC-20260520-017` | [Einstein generative AI](https://help.salesforce.com/s/articleView?id=mktg.mc_anb_einstein_use_genai.htm&type=5) | Salesforce | AI content |
| `SRC-SFMC-20260520-018` | [Einstein data usage](https://help.salesforce.com/s/articleView?id=mktg.mc_anb_einstein_features_data_usage_marketing_cloud.htm&language=en_US&type=5) | Salesforce | AI governance |
| `SRC-SFMC-20260520-019` | [User roles](https://help.salesforce.com/s/articleView?id=mktg.mc_overview_marketing_cloud_roles.htm&language=en_US&type=5) | Salesforce | Governance |
| `SRC-SFMC-20260520-020` | [Setup permissions](https://help.salesforce.com/s/articleView?id=mktg.mc_overview_permissions_setup.htm&language=en_US&type=5) | Salesforce | Governance |
| `SRC-SFMC-20260520-021` | [Business Units](https://help.salesforce.com/s/articleView?id=mktg.mc_es_business_units.htm&language=en_US&type=5) | Salesforce | Enterprise hierarchy |
| `SRC-SFMC-20260520-022` | [Package Manager](https://help.salesforce.com/s/articleView?id=mktg.mc_overview_marketing_cloud_package_manager.htm&language=en_US&type=5) | Salesforce | Package movement |
| `SRC-SFMC-20260520-023` | [Journey Decisioning](https://help.salesforce.com/s/articleView?id=mktg.mceplus_journey_decisioning.htm&language=en_US&type=5) | Salesforce | AI decisioning |

## Facts

- Email Studio covers personalized and operational send workflows, including settings, send classifications, test and scheduled sends, suppression behavior, delivery settings, and send logging concepts.
- Contact Builder centers customer data around contact records and Data Extensions. Data Designer relationship changes can affect filters, sends, and performance.
- Journey Builder covers single-send, transactional, and multi-step journeys with entry sources, re-entry behavior, activities, waits, decisions, goals, exits, and high-throughput sending constraints.
- Automation Studio supports scheduled, file-drop, and triggered automations with activities such as SQL/query, import, extract, file transfer, scripts, filters, validation, and send work.
- Einstein capabilities include engagement scoring, send-time optimization, engagement frequency, generative copy/content help, and decisioning. Salesforce documents data-use and trust behavior for these features.
- Enterprise controls include role/permission models, business units, audit trail extraction, and package movement across business units.

## Inference For Legent

Legent should treat Salesforce parity as operational depth, not a UI clone. The durable product gaps are send policy objects, contact-data governance, activated-journey/runtime accuracy, automation observability, AI trust controls, and admin auditability.

Summer 2026 Salesforce release material beyond 2026-05-20 was not used as current baseline scope.

## Gap Candidates

| Gap ID | Mode Impact | Severity | Queue Status |
|---|---|---|---|
| `email-governance-policy-objects` | Beginner safe defaults, Advanced sender controls, Admin policy/audit | High | BACKLOG |
| `contact-data-designer-governance` | Beginner list/import presets, Advanced relationships/query preview, Admin retention/deletion | High | BACKLOG |
| `journey-runtime-node-contract` | Beginner recipes, Advanced accurate node availability, Admin publish/simulation gates | High | READY |
| `automation-studio-activity-orchestration` | Beginner scheduled recipes, Advanced activity builder, Admin run policy | High | SPLIT_LOCAL: parent decomposed into security design, dependency/run contract, capability UI, file/extract, webhook/notification, send handoff, and script sandbox slices. |
| `ai-governance-optimization-foundation` | Suggestions require uncertainty, opt-in, audit, and data-use controls; draft-only policy/audit scaffold is local, model-provider generation remains future work | High | PARTIAL_LOCAL |

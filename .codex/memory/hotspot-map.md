# Hotspot Map

Last updated: 2026-05-13.

Source: line-count scan over `services`, `shared`, and `frontend/src`.

Largest files found:

- `frontend/src/components/marketing/PublicPageView.tsx` 1243 lines in main-thread scan; QA agent later saw 1307 lines after local edits/context.
- `frontend/src/app/(workspace)/email/templates/[id]/page.tsx` 1060 lines in main-thread scan; QA agent later saw 1121 lines.
- `frontend/src/components/admin/EnterpriseAdminConsole.tsx` 1001 lines in main-thread scan; QA agent later saw 1056 lines.
- `services/foundation-service/.../GlobalEnterpriseService.java` 991 lines in main-thread scan; QA agent later saw 1074 lines.
- `services/foundation-service/.../CorePlatformService.java` 885 lines in main-thread scan; QA agent later saw 964 lines.
- `services/identity-service/.../FederatedIdentityService.java` 844 lines in main-thread scan; QA agent later saw 914 lines.
- `frontend/src/app/(workspace)/campaigns/new/page.tsx` 785 lines.
- `services/campaign-service/.../CampaignLaunchOrchestrationService.java` 779 lines.
- `frontend/src/components/settings/EnterpriseSettingsConsole.tsx` 741 lines.
- `services/delivery-service/.../DeliveryOrchestrationService.java` 607 lines.

Risk:

- Touching these files needs extra focused tests and preferably modular extraction.
- Campaign send path hotspots: `BatchingService.java` groups subscriber chunks and stores payload JSON; `SendExecutionService.java` reads full batch JSON.
- Delivery send path hotspot: `DeliveryOrchestrationService.java` is large and contains multiple catch blocks around provider/rate/retry behavior.
- Frontend high-change operational pages: workspace campaign wizard, campaign tracking, deliverability, and launch command center.
- Frontend API clients are broad and unevenly typed; frontend scan found 290 `any` matches across `frontend/src`, mainly API normalization, large pages, admin panels, and tables.

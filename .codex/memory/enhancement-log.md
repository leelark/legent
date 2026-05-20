# Enhancement Log

Fresh baseline date: 2026-05-20.

## 2026-05-20 Salesforce and competitor parity refresh

Source: `docs/product/salesforce-parity-matrix.md`, `docs/product/competitor-research/2026-05-20-salesforce-marketing-cloud.md`, `docs/product/competitor-research/2026-05-20-competitor-capability-scan.md`, current source audit of `services/**` and `frontend/src/**`.

Status: completed research cycle.

Key themes:
- Email/content capabilities are partially implemented, but explicit send governance objects are not first-class yet.
- Audience/data-extension capabilities are partially implemented, but Contact Builder style relationship/cardinality governance and deletion/provenance audit need decomposition.
- Journey and automation surfaces are partially implemented; the immediate READY risk is that the visual Journey Builder exposes node types beyond proven runtime support.
- Deliverability controls exist locally, but external provider capacity, reputation, egress, and live load evidence remain blocked.
- Analytics exists for core campaign/event metrics and rollups, but journey step analytics, attribution depth, and anomaly evidence are partial.
- Current local intelligence features are deterministic or heuristic; true model-backed AI parity is not proven.
- `BASIC` and `ADVANCED` UI modes exist, but role-gated Admin mode is not yet a current product contract.

Queue items added:
- READY: `journey-runtime-node-contract`.
- BACKLOG: `ai-governance-optimization-foundation`, `email-governance-policy-objects`, `contact-data-designer-governance`, `automation-studio-activity-orchestration`, `mode-aware-workflow-contract`, `flow-analytics-experimentation`.

## 2026-05-20 Journey runtime node contract

Source: `services/automation-service/src/main/java/com/legent/automation/service/WorkflowGraphValidator.java`, `WorkflowStudioService.java`, `WorkflowEngine.java`, `frontend/src/components/automation/JourneyBuilder.tsx`, `NodeEditorModal.tsx`, and `frontend/tests/e2e/automation-builder.spec.ts`.

Status: completed implementation cycle.

Outcome:
- Live journey runtime support is explicit: `ENTRY_TRIGGER`, `SEND_EMAIL`, `DELAY`, `CONDITION`, and `END`.
- Known but unsupported journey node types remain loadable as draft-only design elements and cannot be newly selected or published until runtime support is added.
- Publish/activate requires affirmative backend validation, and backend runtime/start/resume/rollback paths fail closed for unsupported graph semantics.
- Simulation/dry-run reports unsupported runtime nodes instead of implying generic execution.

Validation:
- `.\mvnw.cmd -pl services/automation-service -am test`
- `cd frontend; npm run lint`
- `cd frontend; npm run build:ci`
- `cd frontend; npx playwright test tests/e2e/automation-builder.spec.ts --project=chromium`
- `.codex\utilities\validate-codex-system.ps1`
- `git diff --check`

Current enhancement themes to discover and score:
- Salesforce-parity gaps across email, journeys, automations, data, analytics, deliverability, governance, and AI assistance.
- Beginner, advanced, and admin workflow completeness, with Admin treated as role-gated until implemented.
- High-volume campaign send safety and throughput.
- Observability, release evidence, and operational readiness.
- UI/UX density, navigation, and workflow efficiency.

No enhancement is considered selected until it is scored, scoped, assigned, checkpointed, and validated.

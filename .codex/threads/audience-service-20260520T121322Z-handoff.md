# Audience Service Module Handoff

Owner: AUDIENCE_SERVICE_OWNER
Thread ID: audience-service-20260520T121322Z
Work item ID: predictive-segments-governance
Worktree ID:
Lease IDs: audience-service-20260520T121322Z-predictive-segments-governance-20260520T122113Z
Branch: main
Base commit: 2b5628702c92ab773085f5df306e248f6a174133
Scope: audience-service predictive segment governance contract

Files inspected:
- AGENTS.md
- ARCHITECTURE.md
- PROJECT_CONTEXT.md
- .codex/bootstrap.md
- .codex/teams/module-team-registry.json
- .codex/threads/thread-registry.json
- .codex/backlog/queue.json
- .codex/memory/active-work-items.md
- .codex/memory/blocked-items.md
- .codex/memory/unresolved-risks.md
- services/audience-service/src/main/java/com/legent/audience/**
- services/audience-service/src/test/java/com/legent/audience/**
- docs/product/ai-governance-optimization-foundation.md
- docs/product/salesforce-parity-matrix.md

Files changed:
- services/audience-service/src/main/java/com/legent/audience/controller/SegmentController.java
- services/audience-service/src/main/java/com/legent/audience/domain/Segment.java
- services/audience-service/src/main/java/com/legent/audience/dto/SegmentDto.java
- services/audience-service/src/main/java/com/legent/audience/mapper/SegmentMapper.java
- services/audience-service/src/main/java/com/legent/audience/service/PredictiveSegmentGovernanceService.java
- services/audience-service/src/main/java/com/legent/audience/service/SegmentService.java
- services/audience-service/src/main/java/com/legent/audience/service/SegmentEvaluationService.java
- services/audience-service/src/test/java/com/legent/audience/service/PredictiveSegmentGovernanceServiceTest.java
- services/audience-service/src/test/java/com/legent/audience/service/SegmentServiceTest.java
- services/audience-service/src/test/java/com/legent/audience/service/SegmentEvaluationServiceTest.java
- services/audience-service/src/test/java/com/legent/audience/controller/AudienceControllerRbacTest.java
- .codex/checkpoints/20260520T122119Z-predictive-segments-governance.json
- .codex/audit/events/2026-05-20.jsonl
- .codex/memory/active-work-items.md
- .codex/memory/technical-debt.md
- .codex/memory/security-findings.md
- .codex/memory/performance-bottlenecks.md
- .codex/memory/unresolved-risks.md

Changes made:
- Added `POST /api/v1/segments/predictive-preview` as a read-only governance preview surface.
- Added predictive preview request/response DTOs covering feature sources, data classes, freshness, minimum counts, suppression impact, approval, rollback, confidence, and risk.
- Added `PREDICTIVE` segment type and response exposure of derivation mode plus predictive governance metadata.
- Added fail-closed predictive segment persistence and materialization guards.
- Blocked scheduled predictive recompute in this slice.
- Added regression coverage for tenant/workspace context, policy denial, protected data rejection, approved predictive creation, unapproved materialization denial, and RBAC.

Validation run:
- PASS: `.\mvnw.cmd -pl services/audience-service -am "-Dtest=PredictiveSegmentGovernanceServiceTest,SegmentServiceTest,SegmentEvaluationServiceTest,AudienceControllerRbacTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- PASS: `.\mvnw.cmd -pl services/audience-service -am test`
- PASS: `git diff --check` on touched audience files, with CRLF warnings only.
- PASS: `powershell -ExecutionPolicy Bypass -File scripts\ops\validate-repo-artifact-hygiene.ps1`
- BLOCKED: `.codex\utilities\validate-codex-system.ps1` due unrelated foundation backlog score mismatch for `differentiation-upsert-workspace-exact-match`.

Validation not run:
- Production/load/provider evidence gates were not run; this is a local audience-service contract slice.

Validation artifacts:
- Maven test output in current thread.
- Checkpoint: .codex/checkpoints/20260520T122119Z-predictive-segments-governance.json

Findings:
- The implemented contract does not add model providers, external credentials, model cards, or first-class AI policy tables.
- Existing rule segment recompute still materializes full membership sets in memory; chunked recompute and durable rollback snapshot storage remain follow-up work.
- Data-extension records are not approved predictive feature sources beyond declared metadata because row-level provenance/classification is not present.

Memory updates completed:
- Recorded local predictive governance contract in technical debt and security findings.
- Recorded high-volume recompute/rollback snapshot debt in performance bottlenecks.
- Updated unresolved AI risk to distinguish local governance contract from absent model/provider and parity evidence.
- Registered the active audience-service module thread in active work memory.

Audit events written:
- LEASE_ACQUIRED for predictive-segments-governance.
- ASSIGNED for predictive-segments-governance.
- VALIDATED for predictive-segments-governance.

Residual risks:
- No production or model-provider evidence; do not claim true AI parity or production readiness.
- Predictive governance is stored in segment rules JSON for this slice.

Handoff status: DONE
Recommended next action: Promote the next audience-service item after coordinator review, likely `contact-data-designer-governance` or a refined internal endpoint security test slice.

---

## Contact Data Designer Preview Governance

Owner: AUDIENCE_SERVICE_OWNER
Thread ID: audience-service-20260520T121322Z
Work item ID: contact-data-designer-preview-governance
Parent backlog item: contact-data-designer-governance
Lease ID: audience-service-20260520T121322Z-contact-data-designer-preview-governance-20260520T125358Z
Checkpoint: .codex/checkpoints/20260520T125000Z-contact-data-designer-preview-governance.json
Scope: audience-service no-schema data-extension preview, relationship, sendable-key, and segment metadata governance

Files changed:
- services/audience-service/src/main/java/com/legent/audience/dto/DataExtensionDto.java
- services/audience-service/src/main/java/com/legent/audience/service/DataExtensionService.java
- services/audience-service/src/main/java/com/legent/audience/service/SegmentService.java
- services/audience-service/src/test/java/com/legent/audience/service/DataExtensionServiceTest.java
- services/audience-service/src/test/java/com/legent/audience/service/SegmentServiceTest.java
- .codex/checkpoints/20260520T125000Z-contact-data-designer-preview-governance.json
- .codex/audit/events/2026-05-20.jsonl

Changes made:
- Added nested DTO validation and bounded collection sizes for field definitions, relationships, record data, import preview, query fields, filters, and sort direction.
- Added service-side relationship validation for supported cardinality, source/target field existence, type compatibility, and key compatibility.
- Locked effective sendable-key changes once records exist and required sendable/primary-key fields to be required and primary-key compatible.
- Hardened query preview by validating requested fields, filter fields, and sort fields before scanning, rejecting relationship path syntax, and sorting before projection.
- Rejected unsupported data-extension relationship metadata in segment conditions instead of treating it as subscriber custom fields.

Validation run:
- PASS: `.\mvnw.cmd -pl services/audience-service -am "-Dtest=DataExtensionServiceTest,SegmentServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- PASS: `.\mvnw.cmd -pl services/audience-service -am test`
- PASS: `powershell -ExecutionPolicy Bypass -File .codex\utilities\validate-codex-system.ps1`
- PASS: `powershell -ExecutionPolicy Bypass -File scripts\ops\validate-repo-artifact-hygiene.ps1`
- PASS: scoped `git diff --check` for audience-service touched files, checkpoint, and audit JSONL; CRLF warnings only.

Residual risks:
- No first-class provenance, classification, deletion-audit, or import source metadata tables were added in this no-schema slice.
- Data-extension relationships remain JSON metadata; relationship joins and indexed execution are intentionally unsupported until a schema/performance design exists.
- Historical sendable-key uniqueness/null migration checks need target data proof before release claims.
- Frontend Contact Builder relationship-designer controls remain follow-up work.

Handoff status: DONE and safe-stopped at user request.
Recommended next action: resume `contact-data-designer-governance` with additive provenance/classification/audit tables and controller/API validation, or pick the smaller audience-service internal endpoint security-chain test slice.

# Bug History

Fresh baseline date: 2026-05-20.

## 2026-05-20 Suppression Delete Tenant Scope

Source: read-only predictive segment/audience audit, `services/audience-service/src/main/java/com/legent/audience/service/SuppressionService.java`, `SuppressionRepository.java`, `SuppressionServiceTest.java`.

Status: fixed.

Impact: `SuppressionService.delete` previously used raw `findById(id)`, so a caller with `audience:delete` and a known suppression ID could soft-delete a suppression outside the current tenant/workspace.

Resolution: delete now uses tenant+workspace+ID+not-deleted lookup before soft delete. Focused tests cover same-scope delete and foreign-ID denial.

## 2026-05-20 Segment Rules Fail Closed

Source: read-only predictive segment/audience audit, `services/audience-service/src/main/java/com/legent/audience/service/SegmentEvaluationService.java`, `SegmentService.java`, `SegmentEvaluationServiceTest.java`, `SegmentServiceTest.java`.

Status: fixed.

Impact: `list_membership` conditions previously mapped to `null` before `IN_LIST` and `NOT_IN_LIST` handling, so list membership rules were silently skipped. Unsupported operators could also return `null` and be skipped, broadening segments.

Resolution: evaluator now handles list membership operators before field-column mapping and throws on unsupported operators or invalid list/operator combinations. Segment create/update validates rule trees before persistence. Focused and full audience tests passed.

## 2026-05-20 Automation Studio Live Run Confirmation

Source: read-only automation audit, `services/automation-service/src/main/java/com/legent/automation/service/AutomationStudioService.java`, `AutomationStudioDto.java`, `AutomationStudioServiceTest.java`.

Status: fixed.

Impact: an empty JSON run request `{}` deserialized to `dryRun=false` because `RunRequest.dryRun` was primitive boolean, allowing ACTIVE SQL/IMPORT activities to execute live without an explicit live-run confirmation.

Resolution: `RunRequest.dryRun` is nullable and defaults to dry-run unless explicitly false, live runs require `confirmLiveRun=true`, and focused tests cover empty request default dry-run, missing confirmation denial, confirmed live runs, and unsupported legacy active rows.

## 2026-05-20 Platform Event Idempotency

Source: read-only platform/Kafka audit, `services/platform-service/src/main/java/com/legent/platform/event/PlatformEventConsumer.java`, `PlatformEventIdempotencyService.java`, `V7__platform_event_idempotency.sql`, `PlatformEventConsumerTest.java`, `PlatformEventIdempotencyServiceTest.java`.

Status: fixed locally.

Impact: platform Kafka replays could duplicate webhook dispatches, notifications, and search indexing because event IDs and idempotency keys were validated but not claimed before side effects.

Resolution: platform consumers now claim tenant/workspace-scoped event identity before side effects, skip duplicates, release pending claims only when side effects fail before completion, and keep claims when processed-marker updates fail after a side effect to prevent duplicate replay. Focused and full platform tests passed.

## 2026-05-20 Audience Resolution Final Eligibility First Slice

Source: campaign final-gate audit, `services/audience-service/src/main/java/com/legent/audience/event/AudienceResolutionConsumer.java`, `SendEligibilityService.java`, `SuppressionRepository.java`, `AudienceResolutionConsumerTest.java`, `SendEligibilityServiceTest.java`.

Status: fixed locally; parent work item remains `REVIEW`.

Impact: audience resolution previously filtered external deliverability suppressions and then used an in-memory `isSendEligible` shortcut, which skipped local audience suppressions and did not enforce nested `channels.email=false` or future `pausedUntil` preferences before campaign batching.

Resolution: audience resolution now calls authoritative batch eligibility before publishing resolved chunks; `SendEligibilityService` checks local suppressions in bulk, nested email-channel denial, and pause windows; the shortcut was removed. Remaining review item: campaign legacy recipient payloads need an eligibility marker contract before the parent can be marked done.

No product bug entries exist in the fresh memory baseline.

When a bug is confirmed:
- Record date, source file or command, impact, reproduction, owner, fix status, validation, and residual risk.
- Move root-cause analysis to `root-cause-history.md`.
- Move validated fixes to `successful-fixes.md`.

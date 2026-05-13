# Checkpoint: parallel hardening pass

Date: 2026-05-13.

Source commands:

- `git status --short --branch`
- `.\mvnw.cmd -pl shared/legent-kafka,shared/legent-security,services/audience-service,services/delivery-service -am test`

State:

- Branch `main...origin/main`.
- Worktree already contained orchestration setup, DDL default hardening, Compose health fixes, Kafka trusted-package hardening, and untracked `.codex/` memory files before this pass.
- User explicitly requested parallel subagents. Curie owned shared Kafka publisher keys, Copernicus owned audience resolved-event chunking, Lorentz owned delivery send-request failure handling.

Changes:

- `shared/legent-security/src/main/java/com/legent/security/TenantFilter.java`: rejects workspace/environment header conflicts against JWT-populated context and preserves authenticated context when headers are omitted.
- `shared/legent-kafka/src/main/java/com/legent/kafka/producer/EventPublisher.java`: derives or requires non-tenant keys for configured high-volume topics; low-volume topics keep tenant fallback.
- `services/audience-service/src/main/java/com/legent/audience/event/AudienceResolutionConsumer.java`: publishes bounded deterministic resolved-audience chunks.
- `services/delivery-service/src/main/java/com/legent/delivery/event/DeliveryEventConsumer.java`: rethrows unexpected send-request processing failures after logging.
- Focused tests added for the four changed behavior areas.

Validation:

- Integrated Maven target passed for shared Kafka, shared security, audience service, delivery service, and required upstream modules.

Residual risks:

- Audience resolution still materializes full subscriber ID/subscriber/suppression email lists before chunk publishing.
- Non-delivery Kafka consumers still need retry/DLQ failure-path audits.
- High-volume Kafka topic allowlist must be maintained when new high-throughput topics are added.
- Workspace membership/authorization checks remain needed beyond request-header conflict checks.

Rollback:

- Revert the four code/test behavior changes listed above and the associated `.codex/memory` updates for this checkpoint.

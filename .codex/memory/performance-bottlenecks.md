# Performance Bottlenecks

Last updated: 2026-05-13.

Open:

- 2026-05-13, source `services/campaign-service/.../BatchingService.java` and `SendExecutionService.java`: campaign batching groups full subscriber chunk in memory, stores batch payload JSON until processing, and send execution reads full batch JSON. Completed batches now clear payload retention, but active/retry pressure remains. Impact: high-volume memory and database payload pressure. Next action: store recipient checkpoint/chunk references or bounded payload pages.
- 2026-05-13, source `config/nginx/nginx.conf`: tracking gateway rate limit is 200 r/s per IP. Impact: may be too low for legitimate high-volume tracking behind shared NAT; must validate against load model before production.

Resolved:

- 2026-05-13, source `shared/legent-kafka/.../EventPublisher.java`: high-volume topics now derive or require non-tenant routing keys from explicit keys, payload routing metadata, or event/idempotency/correlation metadata; low-volume topics still fall back to tenant ID. Validation: `.\mvnw.cmd -pl shared/legent-kafka,shared/legent-security,services/audience-service,services/delivery-service -am test`.
- 2026-05-13, source `services/audience-service/.../AudienceResolutionConsumer.java`: `send.audience.resolved` payloads are split into deterministic chunks with `chunkId`, `chunkIndex`, `totalChunks`, `chunkSize`, `totalResolvedSubscribers`, and correct `isLastChunk`. Residual high-volume memory risk remains open above.
- 2026-05-13, source `services/audience-service/.../AudienceResolutionConsumer.java`: all-subscriber resolution now uses keyset paging and one-pass lookahead chunk publishing instead of count-pass plus offset scan. Validation: focused audience/security/kafka/tracking/identity Maven slice passed.
- 2026-05-13, source `services/campaign-service/.../SendExecutionService.java`: send execution now uses a bounded per-batch render cache so repeated identical content/variable renders do not make duplicate remote content-service calls. Validation: impacted Maven modules passed during Loop 2.
- 2026-05-13, source `services/audience-service/.../AudienceResolutionConsumer.java` and `AudienceCandidateRepositoryImpl.java`: LIST and SEGMENT audience resolution now pages candidates with DB keyset queries over subscribers, list memberships, and segment memberships instead of building full Java include/exclude ID sets. Validation: `.\mvnw.cmd -pl services/audience-service clean test -Dtest=AudienceResolutionConsumerTest` passed.
- 2026-05-13, source `services/audience-service/src/main/resources/db/migration/V15__audience_candidate_query_indexes.sql`: candidate paging now has supporting scoped indexes for subscribers, active list memberships, and segment memberships. Validation: audience-service compile passed.
- 2026-05-13, source `services/campaign-service/.../SendExecutionService.java`: completed campaign send batches now clear retained JSON payload to `[]`; partial/retry payloads are preserved for recovery. Validation: `.\mvnw.cmd -pl services/campaign-service -Dtest=SendExecutionServiceTest test` passed.
- 2026-05-13, source `infrastructure/kubernetes/ingress/ingress.yml`: production tracking and analytics ingress no longer inherits the shared API `100r/s` ingress-nginx annotation. Residual gateway-side tracking limit remains open above pending load model. Validation: route validation and production Kustomize render passed.
- 2026-05-13, source `services/audience-service/.../AudienceResolutionConsumer.java`: resolved-audience publish handoff now waits for Kafka futures and rolls back idempotency on failure, preventing invisible throughput loss under broker/backpressure failures. Validation: focused and full audience-service Maven tests passed.
- 2026-05-13, source `services/campaign-service/.../BatchingService.java` and `SendExecutionService.java`: campaign batch and send handoffs now wait for critical Kafka futures, requeue unfinished batches after failed publish handoff, and reconcile terminal batch failure. Residual active/retry payload pressure remains open above. Validation: focused campaign Maven tests passed.

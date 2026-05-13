# Performance Bottlenecks

Last updated: 2026-05-13.

Open:

- 2026-05-13, source `services/audience-service/.../AudienceResolutionConsumer.java`: LIST and SEGMENT paths still build included subscriber ID sets before the subscriber fetch. Impact: large lists/segments can still grow heap and cannot safely handle 10 lakh recipient resolution yet. Next action: stream/page list and segment memberships with cursor checkpoints or a job selection table.
- 2026-05-13, source `services/campaign-service/.../BatchingService.java` and `SendExecutionService.java`: campaign batching groups full subscriber chunk in memory, stores batch payload JSON, and send execution reads full batch JSON. Impact: high-volume memory and database payload pressure. Next action: store recipient checkpoint/chunk references or bounded payload pages.
- 2026-05-13, source `config/nginx/nginx.conf`: tracking gateway rate limit is 200 r/s per IP. Impact: may be too low for legitimate high-volume tracking behind shared NAT; must validate against load model before production.

Resolved:

- 2026-05-13, source `shared/legent-kafka/.../EventPublisher.java`: high-volume topics now derive or require non-tenant routing keys from explicit keys, payload routing metadata, or event/idempotency/correlation metadata; low-volume topics still fall back to tenant ID. Validation: `.\mvnw.cmd -pl shared/legent-kafka,shared/legent-security,services/audience-service,services/delivery-service -am test`.
- 2026-05-13, source `services/audience-service/.../AudienceResolutionConsumer.java`: `send.audience.resolved` payloads are split into deterministic chunks with `chunkId`, `chunkIndex`, `totalChunks`, `chunkSize`, `totalResolvedSubscribers`, and correct `isLastChunk`. Residual high-volume memory risk remains open above.
- 2026-05-13, source `services/audience-service/.../AudienceResolutionConsumer.java`: all-subscriber resolution now uses keyset paging and one-pass lookahead chunk publishing instead of count-pass plus offset scan. Validation: focused audience/security/kafka/tracking/identity Maven slice passed.
- 2026-05-13, source `services/campaign-service/.../SendExecutionService.java`: send execution now uses a bounded per-batch render cache so repeated identical content/variable renders do not make duplicate remote content-service calls. Validation: impacted Maven modules passed during Loop 2.

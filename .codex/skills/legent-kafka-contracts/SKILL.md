---
name: legent-kafka-contracts
description: Work on Legent Kafka topics, event envelopes, partition keys, consumer retry/DLQ behavior, idempotency, and high-volume event contracts.
---

# Legent Kafka Contracts

1. Identify publisher, topic, key, envelope, consumer group, retry, DLQ, idempotency key, and tenant/workspace headers.
2. Keep high-volume topics shard-aware; do not key only by tenant ID.
3. Validate event envelope before side effects.
4. Do not widen `spring.json.trusted.packages`.
5. Do not catch-and-log permanent failures without retry, DLQ, alert, or visible status.
6. Keep recipient/audience payloads chunked and bounded.

Validation:

```powershell
rg -n "@KafkaListener|KafkaTemplate|trusted\.packages" services shared -g "*.java" -g "*.yml" -g "*.yaml" -g "*.properties"
.\mvnw.cmd -pl <module> -am test
```

Required output:
- producer/consumer contract,
- topic/key decision,
- retry/DLQ behavior,
- idempotency proof,
- tests or residual risk.

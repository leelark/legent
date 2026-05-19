# performance-pass

Purpose: remove scalability blockers on high-volume send, tracking, imports, and webhooks.

Discovery:

```powershell
rg -n "TOPIC_AUDIENCE_RESOLVED|SEND_BATCH_SIZE|KafkaTemplate|publish\(" services shared -g "*.java"
rg -n "findAll|findBy.*In|stream\(\)|readValue\(payloadJson|renderTemplate|@Scheduled" services shared -g "*.java"
rg -n "limit_req_zone|partitions|KAFKA_MESSAGE_MAX_BYTES|CLICKHOUSE|REDIS" docker-compose.yml config infrastructure -g "*"
```

Required checks:

- Recipient resolution and send execution are chunked/snapshot-backed.
- Kafka keys are job/batch/provider/domain/shard aware.
- Send loops do not perform unbounded rendering, DB loading, or remote calls.
- Rate/warmup/provider health reservations avoid hot single rows.
- Tracking ingestion and ClickHouse writes are isolated from send execution pressure.
- Suppression, unsubscribe, warmup, inbox safety, and idempotency remain fail-closed.
- Live performance claims are backed by load evidence.

Validation:

```powershell
.\mvnw.cmd -pl services/campaign-service,services/delivery-service,services/tracking-service,services/audience-service -am test
```

If live evidence is absent, record residual risk in `performance-bottlenecks.md` and keep release blocked.

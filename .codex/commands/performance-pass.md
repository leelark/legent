# performance-pass

Purpose: remove scalability blockers on high-volume send, tracking, import, and webhooks.

Priority checks:

```powershell
rg -n "TOPIC_AUDIENCE_RESOLVED|subscribers|SEND_BATCH_SIZE|KafkaTemplate|publish\(" services shared -g "*.java"
rg -n "findAll|findBy.*In|stream\(\)|readValue\(payloadJson|renderTemplate|@Scheduled" services shared -g "*.java"
rg -n "limit_req_zone|partitions|KAFKA_MESSAGE_MAX_BYTES" docker-compose.yml config/nginx/nginx.conf
```

Targets:

- Stream/chunk audience resolution with checkpointed chunk IDs.
- Use job/batch/provider/domain/shard Kafka keys for high-volume topics.
- Bound or cache content rendering in send loops.
- Keep suppression, warmup, rate control, provider health, and idempotency intact.
- Document any unvalidated load claim as risk.

# Operations And Troubleshooting

## Health

```powershell
curl http://localhost:8081/api/v1/health
curl http://localhost:8081/actuator/health
curl http://localhost:8081/actuator/prometheus
```

## Docker Diagnostics

```powershell
docker compose ps
docker compose logs -f postgres
docker compose logs -f kafka
docker compose logs -f redis
docker compose logs -f opensearch
```

## Kafka Checks

```powershell
docker exec -it legent-kafka kafka-topics --bootstrap-server kafka:9092 --list
docker exec -it legent-kafka kafka-consumer-groups --bootstrap-server kafka:9092 --list
```

## PostgreSQL Checks

```powershell
docker exec -it legent-postgres psql -U legent -d postgres -c "\l"
```

## Common Issues

| Symptom | Likely Cause | Fix |
| --- | --- | --- |
| Frontend redirects to login | Missing auth session or workspace context | Verify identity service and `/auth/session` |
| 400 missing workspace | `X-Workspace-Id` missing | Complete context bootstrap or set local storage in test |
| Flyway validation fails | Migration mismatch | Inspect service DB, migration history, and clean local DB if safe |
| Kafka consumers idle | Topics missing or bootstrap wrong | Check `KAFKA_BOOTSTRAP` and topic setup container |
| Delivery cannot send | Provider config or MailHog/SMTP down | Check provider health and MailHog ports |

# Hotspot Map

Fresh baseline date: 2026-05-20.

Initial audit hotspots:
- Tenant/workspace context propagation and fail-closed behavior.
- Campaign, audience, delivery, tracking, and automation high-volume paths.
- Kafka listener retry/DLQ behavior and shard-aware keying.
- Route map, Nginx, ingress, and frontend client drift.
- Large frontend workspace routes and large backend service classes.
- Production overlay, external secrets, network policy, image provenance, and release evidence.

These are audit priorities, not confirmed defects.

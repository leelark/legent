# Tracking Analytics Raw And Canonical Contract

Date: 2026-05-24.
Status: local implementation contract only.

This note defines the local analytics semantics for tracking-service. It does not prove production throughput, ClickHouse runtime behavior, provider capacity, Salesforce parity, or release readiness.

## Semantics

- `PHYSICAL_RAW_ROW`: one stored row in `raw_events`. Exports use this mode so operators can inspect the exact persisted tracking stream.
- `CANONICAL_EVENT_ID`: operational analytics count one event per `(tenant_id, workspace_id, event_type, id)`. Dashboard counts, timelines, experiment metrics, journey goal metrics, rollups, and reconciliation use this mode.
- Canonical grouping must preserve tenant and workspace scope. Missing workspace context still fails closed in controllers through `TenantContext.requireWorkspaceId()`.

## API Contract

- `POST /api/v1/analytics/events/export` returns physical raw rows. Response metadata includes `sourceDataset=raw_events`, `querySemantics=PHYSICAL_RAW_ROW`, and `canonicalOperationalDefault=CANONICAL_EVENT_ID`.
- `GET /api/v1/analytics/rollups` returns canonical operational rows. The response includes `querySemantics=CANONICAL_EVENT_ID` and the dedupe key.
- `GET /api/v1/analytics/campaigns/{id}/reconciliation` compares campaign summaries to `canonicalEventCounts`; `rawEventCounts` remains available as physical-row diagnostics.
- `GET /api/v1/analytics/bi/datasets` marks `campaign_day_rollups` as canonical and `raw_events` as physical raw.

## Remaining Evidence Gaps

- Funnel and segment analytics endpoints still need a separate semantics review because they are separate services/controllers outside this slice.
- Local SQL tests do not prove live PostgreSQL or ClickHouse performance at production volume.
- Target evidence is still required for ClickHouse ingestion lag, rollup freshness, replay behavior, alert delivery, and high-volume tracking isolation.

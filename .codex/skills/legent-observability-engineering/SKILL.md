---
name: legent-observability-engineering
description: Add or review Legent observability for metrics, logs, traces, dashboards, SLOs, alerts, correlation IDs, Kafka lag, ClickHouse ingestion, provider health, and stuck-job detection.
---

# Legent Observability Engineering

1. Identify failure mode and operator question.
2. Add or verify metric/log/trace/dashboard/alert coverage.
3. Preserve correlation IDs, tenant/workspace-safe labels, and low-cardinality metrics.
4. Cover Kafka lag, provider health, send jobs, tracking ingestion, imports, webhooks, and workflow runs when touched.
5. Link runbooks and residual gaps.

Do not log secrets, tokens, customer data, raw email content, or high-cardinality recipient identifiers.

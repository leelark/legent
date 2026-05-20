# Active Work Items

Fresh baseline date: 2026-05-20.

## No Active Work

Status: safely stopped after completing `production-egress-policy-render-gate` per user instruction on 2026-05-20.

Next handoff candidate when resumed: refine/select the highest-priority READY or implementation-ready item from `.codex/backlog/queue.json`; do not start it until the autonomous loop is resumed.

Most recent completion:
- `production-egress-policy-render-gate` completed on 2026-05-20. Strict egress release validation now validates reviewed evidence, generates or checks a reviewed external egress NetworkPolicy, proves it renders through a temporary production Kustomize overlay, verifies evidence hash/CIDR/port/protocol inclusion, rejects stale or missing generated policies, rejects FQDN rules until a supported generator exists, and guards the `app Exists` selector against uncovered Deployment pod templates. Validation: release evidence self-test, production overlay validation, production Kustomize render, local release gate, Codex validation, repo artifact hygiene, and `git diff --check`.
- `delivery-provider-workspace-isolation` completed on 2026-05-20. Delivery provider CRUD, routing, selection, orchestration handoff, provider health, capacity profiles, and failover drills now require tenant+workspace scope; V16 adds forward workspace ownership/backfill and composite provider ownership constraints. Validation: focused delivery isolation suites, `.\mvnw.cmd -pl services/delivery-service -am test`, Codex validation, repo artifact hygiene, and `git diff --check`.

Current organization status:
- `fresh-repo-production-audit` completed on 2026-05-20. Report: `.codex/reports/production-readiness-audit-2026-05-20.md`.
- `feature-flag-tenant-scoped-id-operations` completed on 2026-05-20. Validation: `.\mvnw.cmd -pl services/foundation-service -am test`.
- `internal-routes-edge-blocking` completed on 2026-05-20. Validation: `scripts\ops\validate-route-map.ps1`, `kubectl kustomize infrastructure\kubernetes\overlays\production`, and `docker compose config --quiet`.
- `audience-bulk-suppression-check` completed on 2026-05-20. Validation: `.\mvnw.cmd -pl services/audience-service,services/deliverability-service -am test`, route validation, Kustomize render, Compose config, repo artifact hygiene, production overlay validation, Codex system validation, and `git diff --check`.
- `delivery-feedback-outbox` completed on 2026-05-20. Validation: focused delivery tests, `.\mvnw.cmd -pl services/delivery-service -am test`, Codex system validation, repo artifact hygiene, and `git diff --check`.
- `campaign-send-content-reference-contract` completed on 2026-05-20. Validation: focused campaign tests, focused shared Kafka tests, `.\mvnw.cmd -pl services/campaign-service,shared/legent-kafka -am test`, Codex system validation, repo artifact hygiene, and `git diff --check`.
- `egress-evidence-validator-hardening` completed on 2026-05-20. Validation: release evidence self-test passed, template evidence validation failed as expected, local release gate passed, Codex system validation passed, and `git diff --check` passed.
- `kafka-dlq-sharding` completed on 2026-05-20. Validation: focused Kafka config tests, `.\mvnw.cmd -pl shared/legent-kafka -am test`, Compose config, fixed-DLQ drift scan, Codex system validation, repo artifact hygiene, and `git diff --check`.
- `salesforce-parity-research-refresh` completed on 2026-05-20. It refreshed dated Salesforce and competitor source notes, updated `docs/product/salesforce-parity-matrix.md`, and added implementation backlog slices to `.codex/backlog/queue.json`.
- `journey-runtime-node-contract` completed on 2026-05-20. It aligned Journey Builder node availability, backend publish/runtime validation, simulation, and focused tests so unsupported known journey nodes remain draft-only and fail closed before publish or runtime execution.
- `tracking-ingress-rate-policy` completed on 2026-05-20. Kubernetes ingress now separates signed tracking ingestion from analytics/websocket routes, uses community ingress-nginx `limit-rps` annotations aligned with local Nginx tracking posture, and route validation enforces the split. This is protective configuration, not throughput evidence.
- `sso-tenant-cookie-httponly` completed on 2026-05-20. SSO callback tenant cookies now match normal auth cookie posture; OIDC, SAML ACS, and failed-callback no-cookie tests cover the regression.
- `delivery-provider-workspace-isolation` completed on 2026-05-20. Workspace ownership is enforced for delivery provider config and related operational provider references.
- `production-egress-policy-render-gate` completed on 2026-05-20. Strict release egress proof now renders reviewed external egress policy through a temporary production overlay without checking generated artifacts into the normal Kustomize path.

Update rule: keep this file limited to live `IN_PROGRESS`, `REVIEW`, or `VALIDATING` work plus the next handoff candidate. Move completed outcomes to the owning memory file or `.codex/reports/`.

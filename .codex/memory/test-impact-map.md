# Test Impact Map

Fresh baseline date: 2026-05-20.

Default test selection:
- Backend service behavior: focused Maven module tests with `-pl <module> -am test`.
- Backend integration/runtime behavior: Maven Failsafe tests named `*IT`, `*ITCase`, or `*IntegrationTest`, run with `-DskipITs=false verify`.
- Current integration gate proof: `FailsafeProfileIT` runs without Docker; Docker-backed Testcontainers ITs may skip locally when Docker is unavailable and need a Docker-capable runtime for live DB/Kafka proof.
- Shared module changes: shared module tests plus impacted service tests.
- Kafka/event behavior: publisher/consumer contract tests and retry/DLQ tests where available.
- Tenant/workspace behavior: missing-context and cross-tenant isolation tests.
- Coverage gates: backend JaCoCo runs serialized with `-T1 -Pcoverage -DskipITs=true verify`, conservative per-module thresholds, and `scripts/ops/validate-jacoco-coverage.ps1` aggregate reporting; frontend Vitest/V8 runs through `npm run test:coverage` with 0.1% ratchetable thresholds over `src/lib`, stores, and hooks. Exclusions are bootstrap/generated/data-shape paths only, not service/security/policy runtime logic.
- Frontend UI changes: lint, `npm run build:ci`, impacted Playwright/browser checks, `npm run test:e2e:visual` for required desktop/mobile visual shell evidence in CI and local frontend release gates, and `npm run test:e2e:chromium` for full Chromium suite/release-gate coverage. Playwright uses the standalone production artifact, so rebuild after source changes before E2E. Review visual-smoke screenshots after intentional layout changes and update route/heading/viewport expectations in the same change; `test:e2e:smoke` and `test:e2e:sanitize` remain fast targeted gates.
- Route/runtime changes: route map validator, Compose config, Nginx/ingress render checks.
- Release changes: release gate and evidence validators. Frontend release runs must keep visual smoke, full Chromium, and smoke coverage wired through `release-gate.ps1`; `test-release-evidence-validators.ps1` now source-checks that wiring before long evidence fixtures.

If a gate cannot run, record the exact reason and residual risk.

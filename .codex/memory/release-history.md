# Release History

Fresh baseline date: 2026-05-20.

No product release entries exist in the fresh memory baseline.

Current release posture:
- Production release remains blocked without target-environment evidence.
- The `.codex` rebuild is an internal orchestration and documentation change, not a product release.
- 2026-05-20 local change set: feature flag tenant-scoping fix passed `.\mvnw.cmd -pl services/foundation-service -am test`. This is not a production release; strict production promotion still requires external evidence and release gates.
- 2026-05-20 local change set: public-edge internal route denies passed route validation, Kustomize render, and Compose config. This is not a production release; strict production promotion still requires external evidence and release gates.
- 2026-05-20 local change set: audience deliverability suppression bulk-check path passed `.\mvnw.cmd -pl services/audience-service,services/deliverability-service -am test`, route validation, Kustomize render, Compose config, repo artifact hygiene, production overlay validation, Codex validation, and `git diff --check`. This is not a production release; strict production promotion still requires external evidence and release gates.
- 2026-05-20 local change set: delivery feedback outbox passed focused delivery tests, `.\mvnw.cmd -pl services/delivery-service -am test`, Codex validation, repo artifact hygiene, and `git diff --check`. This is not a production release; strict production promotion still requires external evidence and release gates.
- 2026-05-20 local change set: campaign send content-reference contract alignment passed focused campaign tests, focused shared Kafka tests, `.\mvnw.cmd -pl services/campaign-service,shared/legent-kafka -am test`, Codex validation, repo artifact hygiene, and `git diff --check`. This is not a production release; strict production promotion still requires external evidence and release gates.
- 2026-05-20 local change set: production egress evidence validator hardening passed `scripts\ops\test-release-evidence-validators.ps1`, expected-fail template validation, local release gate, Codex validation, and `git diff --check`. This is not a production release; strict production promotion still requires real target-environment egress, image, GA, load, restore, CI/security, TLS/admission, and monitoring evidence.
- 2026-05-20 local change set: Kafka DLQ sharding passed focused Kafka config tests, `.\mvnw.cmd -pl shared/legent-kafka -am test`, Compose config, fixed-DLQ drift scan, Codex validation, repo artifact hygiene, and `git diff --check`. This is not a production release; strict production promotion still requires external Kafka topology evidence and the other release evidence gates.
- 2026-05-20 local change set: Journey Builder runtime node contract alignment passed focused automation tests, `.\mvnw.cmd -pl services/automation-service -am test`, frontend lint, frontend production build, targeted Playwright builder spec, Codex validation, and `git diff --check`. This is not a production release; broader journey node families remain draft-only until separately implemented and validated.
- 2026-05-20 local change set: tracking ingress route-limit posture alignment passed route validation, production/global Kustomize renders, production overlay validation, Codex validation, and `git diff --check`. This is not a production release or throughput claim; strict promotion still requires target ingress-controller behavior and downstream tracking ingestion evidence.
- 2026-05-20 local change set: SSO tenant-cookie HTTP-only parity passed focused identity controller tests, `.\mvnw.cmd -pl services/identity-service -am test`, Codex validation, repo artifact hygiene, and `git diff --check`. This is not a production release; strict promotion still requires external evidence and release gates.
- 2026-05-20 local change set: delivery provider workspace isolation passed focused provider isolation tests, focused migration/health/capacity tests, `.\mvnw.cmd -pl services/delivery-service -am test`, Codex validation, repo artifact hygiene, and `git diff --check`. This is not a production release; legacy `workspace-default` backfills and all external release evidence still require target review before promotion.
- 2026-05-20 local change set: production egress policy render proof passed release evidence self-test, production overlay validation, production Kustomize render, local release gate, Codex validation, repo artifact hygiene, and `git diff --check`. This is not a production release; strict promotion still requires real target-environment evidence, but strict egress mode now proves reviewed policy generation/render inclusion when evidence is supplied.

Record future releases with:
- version or change set,
- validation gates,
- evidence artifacts,
- deployment target,
- rollback plan,
- residual risk.

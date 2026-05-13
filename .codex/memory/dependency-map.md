# Dependency Map

Last updated: 2026-05-13.

Source: `Select-String -Path services\*\pom.xml,shared\*\pom.xml -Pattern '<artifactId>legent-'`.

- Most services depend on `legent-common`, `legent-security`, `legent-kafka`, `legent-cache`, and test-scope `legent-test-support`.
- `platform-service` depends on `legent-common`, `legent-security`, `legent-kafka`, and test support; no `legent-cache` dependency found in scan.
- Shared module chain: `legent-security` -> `legent-common`; `legent-kafka` -> `legent-common` + `legent-security`; `legent-cache` -> `legent-common` + `legent-security`; `legent-test-support` -> `legent-common` + `legent-kafka` + `legent-security`.
- Event backbone: all event services use `shared/legent-kafka` `EventEnvelope` and `EventPublisher`.
- Frontend API dependencies are split by domain under `frontend/src/lib/*-api.ts`.

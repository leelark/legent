# Refactor Log

Last updated: 2026-05-13.

- No code refactor performed during orchestration bootstrap.

Refactor candidates:

- Split large workspace template editor and campaign creation route into domain components/hooks.
- Split foundation platform/admin services by capability.
- Extract delivery orchestration sub-services for provider choice, safety, rendering/handoff, feedback handling.
- Replace full audience payload event with chunk manifest/checkpoint flow.

# Refactor Log

Last updated: 2026-05-16.

- 2026-05-16: Dependency-proven dead-code cleanup removed unreachable frontend tracking/content/admin/layout components, obsolete frontend tracking helpers, dead `TenantException`, and an unused delivery fallback method. Search/mobile shell behavior was kept through active components instead of retaining dead alternatives.

Refactor candidates:

- Split large workspace template editor and campaign creation route into domain components/hooks.
- Split foundation platform/admin services by capability.
- Extract delivery orchestration sub-services for provider choice, safety, rendering/handoff, feedback handling.
- Replace full audience payload event with chunk manifest/checkpoint flow.

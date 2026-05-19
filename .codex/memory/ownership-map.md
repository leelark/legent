# Ownership Map

Fresh baseline date: 2026-05-20.

Ownership source: `.codex/agents/agent-catalog.md` and `.codex/agents/routing-matrix.md`.

Default ownership:
- CTO_AGENT: architecture, release posture, safety tradeoffs.
- PROGRAM_MANAGER_AGENT: decomposition, dependencies, agent utilization, checkpoints.
- PRODUCT_MANAGER_AGENT: parity, modes, user value, roadmap.
- SERVICE OWNERS: backend behavior inside service boundaries.
- FRONTEND_OWNER: workspace and public UI behavior.
- SECURITY_ENGINEER: auth, tenant isolation, secrets, public endpoints, signed flows.
- PERFORMANCE_ENGINEER: high-volume send, tracking, imports, Kafka, ClickHouse.
- DEVOPS_ENGINEER and RELEASE_MANAGER: runtime, CI, Kubernetes, release gates.
- DOCUMENTATION_ENGINEER and TECHNICAL_DEBT_STEWARD: memory, docs, drift, debt.

Assign extra partners when work crosses service, security, performance, database, route, or release boundaries.

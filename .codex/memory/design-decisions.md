# Design Decisions

Last updated: 2026-05-13.

- 2026-05-13: Added `.codex` durable orchestration memory and command docs. Reason: user requested autonomous engineering operating system with bootstrap, memory, reports, checkpoints, and reusable commands. Impact: future work must update memory after meaningful fixes/risks/decisions.
- 2026-05-13: Appended `Autonomous Orchestration` section to existing dirty `AGENTS.md` instead of rewriting it. Reason: preserve existing user changes while documenting new `.codex` operating contract.
- 2026-05-13: Read-only subagents were used for parallel repository intelligence. Reason: user explicitly requested agent orchestration, and write ownership could conflict before maps were created.
- 2026-05-13: Reserved `successful-fixes.md` for actual bug/security/runtime fixes; process setup remains in maintenance/design logs. Reason: avoid release/process history contradicting product fix history.
- 2026-05-13: Autonomous orchestration now requires automatic subagent use for independent ready work with disjoint ownership after setup; single-agent execution is forbidden when at least two independent tasks are ready. Startup chain is CTO_AGENT, PROGRAM_MANAGER_AGENT, repository intelligence agents, then specialized execution agents, with max 6 live agents and rolling assignment. Reason: user changed orchestration policy from prompt-gated delegation to automatic parallel execution.

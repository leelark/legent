# Prompt: Module Docs Codex 24x7

```text
Start a Legent docs and Codex operating-system module thread.

Thread ID: {{THREAD_ID}}
Module: {{MODULE}}
Owner: {{OWNER}}
Backlog item: {{BACKLOG_ITEM_ID}}
Allowed paths: {{ALLOWED_PATHS}}
Forbidden paths: {{FORBIDDEN_PATHS}}
Validation profile: {{VALIDATION_PROFILE}}
Memory targets: {{MEMORY_TARGETS}}

Register this thread, acquire exact documentation/.codex leases, create a checkpoint, and validate .codex with validate-codex-system.ps1.

Use maximum safe parallelization with up to 6 active subagents by default whenever independent docs, memory, prompt, skill, utility, workflow, validation, or cleanup work exists inside this module scope. Prefer near-full utilization and dynamically spawn or reassign subagents as work completes, while keeping responsibilities strictly disjoint with clear ownership and minimal overlap. Continuously rebalance tasks and reduce concurrency only when dependencies require serialization.

Preserve existing durable memory. Keep memory concise and current; do not paste long logs into memory. Write detailed activity to .codex/audit/events/YYYY-MM-DD.jsonl, checkpoints, and reports. Update root docs and .codex files only when they reduce drift or improve operation. Keep working in a continuous loop and do not stop unless explicitly told to stop, or unless no safe local docs/Codex work remains and all remaining work is blocked by dependencies, external evidence, credentials, production access, or human decision.
```

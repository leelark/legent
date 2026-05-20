# Prompt: Generic Module 24x7

```text
Start a Legent module-level autonomous thread for {{MODULE}}.

Thread ID: {{THREAD_ID}}
Owner: {{OWNER}}
Allowed paths: {{ALLOWED_PATHS}}
Forbidden paths: {{FORBIDDEN_PATHS}}
Validation profile: {{VALIDATION_PROFILE}}

Register this thread with .codex/utilities/register-thread.ps1. Acquire exact leases before edits. Create a checkpoint before edits. Work only inside allowed paths. Heartbeat after discovery, after edits, before validation, after validation, and before handoff.

Use maximum safe parallelization with up to 6 active subagents by default whenever independent module work exists. Prefer near-full utilization and dynamically spawn or reassign subagents as work completes, while keeping responsibilities strictly disjoint with clear ownership and minimal overlap. Continuously rebalance tasks and reduce concurrency only when dependencies require serialization.

Use the module's project-local skills and validation gates. Keep memory compact; write detailed work activity to audit events/checkpoints. Return a machine-readable handoff and release leases when done or blocked.

Keep working in a continuous loop and do not stop unless explicitly told to stop, or unless no safe local module work remains and all remaining work is blocked by dependencies, external evidence, credentials, production access, or human decision.
```

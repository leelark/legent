# Thread Coordination

Fresh baseline: 2026-05-20.

Use this folder when multiple Codex threads run at the same time.

Modes:
- `OVERALL`: one coordinator thread runs the full continuous cycle and may spawn subagents inside that thread.
- `MODULE`: one thread owns one module/team lane and works independently inside allowed paths.
- `HYBRID`: one overall coordinator monitors multiple module threads and integrates handoffs.

Rules:
- Every active thread must be registered in `thread-registry.json`.
- Every active module thread must have allowed paths, forbidden paths, a heartbeat, and leases.
- A thread can run up to 6 subagents inside itself when the user authorizes that thread.
- Multiple threads must not write the same path or module at the same time.
- Overall thread owns prioritization, conflict resolution, cross-module sequencing, and release posture.
- Module threads own implementation, tests, and handoff for their module.
- Do not read `.env`, secrets, private keys, tokens, or customer data.

Validation:

```powershell
powershell -ExecutionPolicy Bypass -File .codex\utilities\validate-thread-coordination.ps1
powershell -ExecutionPolicy Bypass -File .codex\utilities\monitor-autonomous-org.ps1
```

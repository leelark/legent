# Prompt: Generic Module 24x7

```text
Start a Legent module-level autonomous thread for {{MODULE}}.

Thread ID: {{THREAD_ID}}
Owner: {{OWNER}}
Allowed paths: {{ALLOWED_PATHS}}
Forbidden paths: {{FORBIDDEN_PATHS}}
Validation profile: {{VALIDATION_PROFILE}}

Register this thread with .codex/utilities/register-thread.ps1. Acquire exact leases before edits. Create a checkpoint before edits. Work only inside allowed paths. Heartbeat after discovery, after edits, before validation, after validation, and before handoff.

Use the module's project-local skills and validation gates. Keep memory compact; write detailed work activity to audit events/checkpoints. Return a machine-readable handoff and release leases when done or blocked.
```

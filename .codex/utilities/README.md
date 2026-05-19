# Codex Utilities

Fresh baseline: 2026-05-20.

Utilities make the autonomous organization executable. They must be safe by default:

- no secrets,
- no `.env` reads,
- no commits, pushes, or deploys,
- no destructive cleanup without explicit operator action,
- clear failures when state is invalid.

Common commands:

```powershell
powershell -ExecutionPolicy Bypass -File .codex\utilities\validate-codex-system.ps1
powershell -ExecutionPolicy Bypass -File .codex\utilities\list-active-work.ps1
powershell -ExecutionPolicy Bypass -File .codex\utilities\select-next-work.ps1
powershell -ExecutionPolicy Bypass -File .codex\utilities\new-checkpoint.ps1 -Id example -Objective "Example" -Owner PROGRAM_MANAGER_AGENT -NextAction "Continue"
```

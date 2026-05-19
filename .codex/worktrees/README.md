# Worktree Operating Model

Fresh baseline: 2026-05-20.

Use worktrees only for independent slices that need isolation. Keep normal single-workspace edits in the main worktree when that is safer.

Rules:

- Branch names use `codex/<work-item-id>`.
- Every active worktree needs a registry entry in `.codex/worktrees/worktree-registry.json`.
- Every parallel writer needs a lease in `.codex/worktrees/leases/active-leases.json`.
- A lease should name the smallest safe module, directory, or file scope.
- Do not create overlapping write scopes unless the program manager explicitly coordinates integration.
- Do not read or copy secrets into worktrees.
- Close or archive worktrees when work is merged, abandoned, or superseded.

Validate with:

```powershell
powershell -ExecutionPolicy Bypass -File .codex\utilities\validate-worktree-leases.ps1
powershell -ExecutionPolicy Bypass -File .codex\utilities\validate-codex-system.ps1
```

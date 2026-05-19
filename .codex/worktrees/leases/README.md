# Active Leases

Use leases to avoid parallel agents editing the same module or file set.

Each lease records:
- owner agent,
- work item id,
- scope,
- files or directories in scope,
- expiry or review time,
- status.

Run `.codex/utilities/validate-worktree-leases.ps1` before assigning parallel worker edits.

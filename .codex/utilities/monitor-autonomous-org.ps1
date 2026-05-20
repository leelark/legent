param(
    [string]$OutputPath = ".codex/dashboards/team-dashboard.md",
    [switch]$CheckOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Read-Json($Path) {
    if (-not (Test-Path $Path)) { return $null }
    return Get-Content -Path $Path -Raw | ConvertFrom-Json
}

$threadRegistry = Read-Json ".codex/threads/thread-registry.json"
$queue = Read-Json ".codex/backlog/queue.json"
$leases = Read-Json ".codex/worktrees/leases/active-leases.json"
$worktrees = Read-Json ".codex/worktrees/worktree-registry.json"
$teamRegistry = Read-Json ".codex/teams/module-team-registry.json"
$now = Get-Date
$staleMinutes = if ($threadRegistry -and $threadRegistry.heartbeatStaleMinutes) { [int]$threadRegistry.heartbeatStaleMinutes } else { 60 }

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# Autonomous Team Dashboard")
$lines.Add("")
$lines.Add("Generated: $((Get-Date).ToUniversalTime().ToString("o"))")
$lines.Add("")
$lines.Add("## Summary")
$lines.Add("")
$lines.Add("- Registered module teams: $(@($teamRegistry.teams).Count)")
$lines.Add("- Registered threads: $(@($threadRegistry.threads).Count)")
$lines.Add("- Active threads: $(@($threadRegistry.threads | Where-Object { $_.status -eq 'ACTIVE' }).Count)")
$lines.Add("- Active leases: $(@($leases.leases | Where-Object { -not $_.status -or $_.status -eq 'ACTIVE' }).Count)")
$lines.Add("- Active worktrees: $(@($worktrees.activeWorktrees).Count)")
$lines.Add("- Ready work: $(@($queue.readyWork).Count)")
$lines.Add("- Backlog work: $(@($queue.backlogWork).Count)")
$lines.Add("- Blocked work: $(@($queue.blockedWork).Count)")
$lines.Add("- Done work: $(@($queue.doneWork).Count)")
$lines.Add("")
$lines.Add("## Threads")
$lines.Add("")
$lines.Add("| Thread | Role | Module | Status | Heartbeat | Stale | Next Action |")
$lines.Add("|---|---|---|---|---|---|---|")
foreach ($thread in @($threadRegistry.threads)) {
    $heartbeat = [datetime]::Parse([string]$thread.heartbeatAt)
    $isStale = (($now.ToUniversalTime() - $heartbeat.ToUniversalTime()).TotalMinutes -gt $staleMinutes)
    $lines.Add("| $($thread.threadId) | $($thread.threadRole) | $($thread.module) | $($thread.status) | $($thread.heartbeatAt) | $isStale | $($thread.nextAction) |")
}
$lines.Add("")
$lines.Add("## Next Work")
$lines.Add("")
$next = @(@($queue.readyWork) | Where-Object { @($_.blockers).Count -eq 0 } | Sort-Object -Property @{Expression = { [int]$_.priorityScore }; Descending = $true } | Select-Object -First 5)
if ($next.Count -eq 0) {
    $lines.Add("No unblocked READY work. Run pending-scan, research-pass, and refine-backlog.")
} else {
    foreach ($item in $next) {
        $lines.Add("- `$($item.id)` score=$($item.priorityScore), owner=$($item.owner): $($item.nextAction)")
    }
}
$lines.Add("")
$lines.Add("## Blocked")
$lines.Add("")
foreach ($item in @($queue.blockedWork | Select-Object -First 10)) {
    $lines.Add("- `$($item.id)`: $($item.nextAction)")
}

if ($CheckOnly) {
    Write-Host "Autonomous monitor check passed. Ready=$(@($queue.readyWork).Count) Threads=$(@($threadRegistry.threads).Count)"
} else {
    $dir = Split-Path -Parent $OutputPath
    if ($dir) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    Set-Content -Path $OutputPath -Value $lines -Encoding UTF8
    Write-Host "Wrote autonomous dashboard to $OutputPath"
}

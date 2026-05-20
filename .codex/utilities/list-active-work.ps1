param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$state = Get-Content ".codex/state/team-state.json" -Raw | ConvertFrom-Json
$queue = Get-Content ".codex/backlog/queue.json" -Raw | ConvertFrom-Json
$worktrees = Get-Content ".codex/worktrees/worktree-registry.json" -Raw | ConvertFrom-Json
$leases = Get-Content ".codex/worktrees/leases/active-leases.json" -Raw | ConvertFrom-Json

Write-Host "Active agents: $(@($state.activeAgents).Count)"
Write-Host "Active work: $(@($state.activeWork).Count)"
$readyItems = @($queue.readyWork) | Where-Object { $_.status -eq "READY" }
Write-Host "Ready work: $(@($readyItems).Count)"
Write-Host "Backlog work: $(@($queue.backlogWork).Count)"
Write-Host "Blocked work: $(@($queue.blockedWork).Count)"
Write-Host "Active worktrees: $(@($worktrees.activeWorktrees).Count)"
Write-Host "Active leases: $(@($leases.leases | Where-Object { -not $_.status -or $_.status -eq 'ACTIVE' }).Count)"

$next = $readyItems | Sort-Object -Property @{Expression = { [int]$_.priorityScore }; Descending = $true }, id | Select-Object -First 1
if ($next) {
    Write-Host "Next READY: $($next.id) score=$($next.priorityScore) owner=$($next.owner)"
    Write-Host "Next action: $($next.nextAction)"
} else {
    Write-Host "No READY work. Run pending-scan and refine-backlog."
}

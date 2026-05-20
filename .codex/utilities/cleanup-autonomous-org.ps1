param(
    [int]$StaleMinutes = 120,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$threadPath = ".codex/threads/thread-registry.json"
$leasePath = ".codex/worktrees/leases/active-leases.json"
$registry = Get-Content -Path $threadPath -Raw | ConvertFrom-Json
$leases = Get-Content -Path $leasePath -Raw | ConvertFrom-Json
$now = (Get-Date).ToUniversalTime()

$staleThreads = @()
foreach ($thread in @($registry.threads)) {
    if ($thread.status -eq "ACTIVE" -and $thread.heartbeatAt) {
        $heartbeat = [datetime]::Parse([string]$thread.heartbeatAt).ToUniversalTime()
        if (($now - $heartbeat).TotalMinutes -gt $StaleMinutes) {
            $staleThreads += $thread.threadId
            if (-not $DryRun) {
                $thread.status = "PAUSED"
                $thread.lastUpdated = $now.ToString("o")
                $thread.nextAction = "Heartbeat stale; inspect thread before resuming."
                $thread.activeAgents = @()
                & .codex/utilities/write-audit-event.ps1 -EventType CLEANUP -Actor "PROGRAM_MANAGER_AGENT" -ThreadId $thread.threadId -Module $thread.module -Summary "Paused stale thread after heartbeat timeout." -NextAction "Inspect checkpoint, handoff, and leases before resuming."
            }
        }
    }
}

if (-not $DryRun) {
    $leases.leases = @($leases.leases | Where-Object { $staleThreads -notcontains $_.threadId })
    $registry | ConvertTo-Json -Depth 12 | Set-Content -Path $threadPath -Encoding UTF8
    $leases | ConvertTo-Json -Depth 12 | Set-Content -Path $leasePath -Encoding UTF8
}

Write-Host "Stale threads: $($staleThreads.Count)"
if ($DryRun) { Write-Host "Dry run only; no changes written." }

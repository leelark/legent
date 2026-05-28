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

$expiredLeases = @()
foreach ($lease in @($leases.leases)) {
    $leaseStatus = if ($lease.PSObject.Properties.Name -contains "status") { [string]$lease.status } else { "" }
    if ($leaseStatus -and $leaseStatus -ne "ACTIVE") {
        continue
    }
    if (-not $lease.expiresAt) {
        continue
    }

    $expiresAt = [datetime]::Parse([string]$lease.expiresAt).ToUniversalTime()
    if ($expiresAt -lt $now) {
        $expiredLeases += $lease
        if (-not $DryRun) {
            $lease.status = "EXPIRED"
            $lease.heartbeatAt = $now.ToString("o")
            $thread = @($registry.threads) | Where-Object { $_.threadId -eq $lease.threadId } | Select-Object -First 1
            if ($thread) {
                $thread.leaseIds = @($thread.leaseIds | Where-Object { $_ -ne $lease.leaseId })
                $thread.lastUpdated = $now.ToString("o")
            }
            $leaseModule = "overall"
            if ($thread -and ($thread.PSObject.Properties.Name -contains "module") -and -not [string]::IsNullOrWhiteSpace([string]$thread.module)) {
                $leaseModule = [string]$thread.module
            } elseif (($lease.PSObject.Properties.Name -contains "module") -and -not [string]::IsNullOrWhiteSpace([string]$lease.module)) {
                $leaseModule = [string]$lease.module
            }
            & .codex/utilities/write-audit-event.ps1 -EventType LEASE_EXPIRED -Actor $lease.owner -ThreadId $lease.threadId -WorkItemId $lease.workItemId -Module $leaseModule -Files @($lease.filesInScope) -Summary "Marked expired active lease as EXPIRED during cleanup." -NextAction "Continue with release validation after lease reconciliation."
        }
    }
}

if (-not $DryRun) {
    $leases.leases = @($leases.leases | Where-Object { $staleThreads -notcontains $_.threadId })
    $registry | ConvertTo-Json -Depth 12 | Set-Content -Path $threadPath -Encoding UTF8
    $leases | ConvertTo-Json -Depth 12 | Set-Content -Path $leasePath -Encoding UTF8
}

Write-Host "Stale threads: $($staleThreads.Count)"
Write-Host "Expired active leases: $($expiredLeases.Count)"
if ($DryRun) { Write-Host "Dry run only; no changes written." }

param(
    [Parameter(Mandatory = $true)][string]$LeaseId,
    [ValidateSet("RELEASED", "ABANDONED", "EXPIRED")][string]$Status = "RELEASED",
    [string]$Summary = "Lease released."
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$leasePath = ".codex/worktrees/leases/active-leases.json"
$threadPath = ".codex/threads/thread-registry.json"
$leases = Get-Content -Path $leasePath -Raw | ConvertFrom-Json
$threads = Get-Content -Path $threadPath -Raw | ConvertFrom-Json
$lease = @($leases.leases) | Where-Object { $_.leaseId -eq $LeaseId } | Select-Object -First 1
if (-not $lease) { Write-Error "Lease not found: $LeaseId"; exit 1 }

$lease.status = $Status
$lease.heartbeatAt = (Get-Date).ToUniversalTime().ToString("o")
$thread = @($threads.threads) | Where-Object { $_.threadId -eq $lease.threadId } | Select-Object -First 1
if ($thread) {
    $thread.leaseIds = @($thread.leaseIds | Where-Object { $_ -ne $LeaseId })
    $thread.lastUpdated = (Get-Date).ToUniversalTime().ToString("o")
}

$leases | ConvertTo-Json -Depth 12 | Set-Content -Path $leasePath -Encoding UTF8
$threads | ConvertTo-Json -Depth 12 | Set-Content -Path $threadPath -Encoding UTF8
& .codex/utilities/write-audit-event.ps1 -EventType LEASE_RELEASED -Actor $lease.owner -ThreadId $lease.threadId -WorkItemId $lease.workItemId -Module "unknown" -Files @($lease.filesInScope) -Summary $Summary -NextAction "Continue or close thread."
Write-Host "Lease $LeaseId moved to $Status."

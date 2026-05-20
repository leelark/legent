param(
    [Parameter(Mandatory = $true)][string]$WorkItemId,
    [Parameter(Mandatory = $true)][ValidateSet("DONE", "REVIEW", "BLOCKED", "WONT_DO")][string]$Status,
    [Parameter(Mandatory = $true)][string]$Summary,
    [string[]]$ValidationRun = @(),
    [string[]]$ResidualRisks = @(),
    [string]$NextAction = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$queuePath = ".codex/backlog/queue.json"
$queue = Get-Content -Path $queuePath -Raw | ConvertFrom-Json
$buckets = @("readyWork", "backlogWork", "inProgressWork", "blockedWork", "doneWork")
$item = $null
foreach ($bucket in $buckets) {
    $found = @($queue.$bucket) | Where-Object { $_.id -eq $WorkItemId } | Select-Object -First 1
    if ($found) { $item = $found; break }
}
if (-not $item) { Write-Error "Work item not found: $WorkItemId"; exit 1 }

foreach ($bucket in $buckets) {
    if ($null -ne $queue.$bucket) {
        $queue.$bucket = @($queue.$bucket | Where-Object { $_.id -ne $WorkItemId })
    }
}
$item.status = $Status
$item.completedAt = (Get-Date).ToUniversalTime().ToString("o")
$item.lastUpdated = $item.completedAt
$item.outcome = $Summary
$item.validationRun = @($ValidationRun)
$item.residualRisks = @($ResidualRisks)
if ($NextAction) { $item.nextAction = $NextAction }

if ($Status -eq "DONE") {
    $queue.doneWork = @($queue.doneWork) + $item
} elseif ($Status -eq "BLOCKED") {
    $queue.blockedWork = @($queue.blockedWork) + $item
} else {
    $queue.inProgressWork = @($queue.inProgressWork) + $item
}
$queue.lastUpdated = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd")
$queue | ConvertTo-Json -Depth 20 | Set-Content -Path $queuePath -Encoding UTF8

$leasePath = ".codex/worktrees/leases/active-leases.json"
if (Test-Path $leasePath) {
    $leases = Get-Content -Path $leasePath -Raw | ConvertFrom-Json
    foreach ($lease in @($leases.leases | Where-Object { $_.workItemId -eq $WorkItemId -and ($_.status -eq "ACTIVE" -or -not $_.status) })) {
        $lease.status = if ($Status -eq "WONT_DO") { "ABANDONED" } else { "RELEASED" }
        $lease.heartbeatAt = (Get-Date).ToUniversalTime().ToString("o")
    }
    $leases | ConvertTo-Json -Depth 12 | Set-Content -Path $leasePath -Encoding UTF8
}

$eventType = if ($Status -eq "DONE") { "DONE" } elseif ($Status -eq "BLOCKED") { "BLOCKED" } elseif ($Status -eq "WONT_DO") { "ABANDONED" } else { "READY_FOR_REVIEW" }
& .codex/utilities/write-audit-event.ps1 -EventType $eventType -Actor $item.owner -WorkItemId $WorkItemId -Module "unknown" -Validation $ValidationRun -Summary $Summary -NextAction $NextAction
Write-Host "Work item $WorkItemId moved to $Status."

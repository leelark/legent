param(
    [Parameter(Mandatory = $true)][string]$ThreadId,
    [Parameter(Mandatory = $true)][string]$WorkItemId,
    [Parameter(Mandatory = $true)][string[]]$FilesInScope
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$queuePath = ".codex/backlog/queue.json"
$queue = Get-Content -Path $queuePath -Raw | ConvertFrom-Json
$item = @($queue.readyWork + $queue.backlogWork) | Where-Object { $_.id -eq $WorkItemId } | Select-Object -First 1
if (-not $item) { Write-Error "Work item not found in ready/backlog: $WorkItemId"; exit 1 }
if (@($item.blockers).Count -gt 0) { Write-Error "Work item has blockers: $WorkItemId"; exit 1 }

$item.status = "IN_PROGRESS"
$item.startedAt = (Get-Date).ToUniversalTime().ToString("o")
$item.lastUpdated = $item.startedAt
$queue.readyWork = @($queue.readyWork | Where-Object { $_.id -ne $WorkItemId })
$queue.backlogWork = @($queue.backlogWork | Where-Object { $_.id -ne $WorkItemId })
$queue.inProgressWork = @($queue.inProgressWork) + $item
$queue.lastUpdated = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd")
$queue | ConvertTo-Json -Depth 20 | Set-Content -Path $queuePath -Encoding UTF8

& .codex/utilities/acquire-lease.ps1 -ThreadId $ThreadId -WorkItemId $WorkItemId -FilesInScope $FilesInScope
& .codex/utilities/new-checkpoint.ps1 -Id $WorkItemId -Objective $item.title -Owner $item.owner -FilesInScope $FilesInScope -ValidationPlan @($item.validationCommands) -Agents @($item.partnerAgents) -NextAction $item.nextAction
& .codex/utilities/write-audit-event.ps1 -EventType ASSIGNED -Actor $item.owner -ThreadId $ThreadId -WorkItemId $WorkItemId -Module "unknown" -Files $FilesInScope -Validation @($item.validationCommands) -Summary "Work item started." -NextAction $item.nextAction

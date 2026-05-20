param(
    [Parameter(Mandatory = $true)][string]$WorkItemId
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$queuePath = ".codex/backlog/queue.json"
$queue = Get-Content -Path $queuePath -Raw | ConvertFrom-Json
$item = @($queue.backlogWork) | Where-Object { $_.id -eq $WorkItemId } | Select-Object -First 1
if (-not $item) { Write-Error "Backlog item not found: $WorkItemId"; exit 1 }
if (@($item.blockers).Count -gt 0) { Write-Error "Cannot promote blocked item: $WorkItemId"; exit 1 }
foreach ($field in @("owner", "scope", "acceptanceCriteria", "validationProfile", "validationCommands", "nextAction")) {
    if ($null -eq $item.$field -or @($item.$field).Count -eq 0) {
        Write-Error "Cannot promote $WorkItemId; missing $field"
        exit 1
    }
}

$item.status = "READY"
$item.lastUpdated = (Get-Date).ToUniversalTime().ToString("o")
$queue.backlogWork = @($queue.backlogWork | Where-Object { $_.id -ne $WorkItemId })
$queue.readyWork = @($queue.readyWork) + $item
$queue.lastUpdated = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd")
$queue | ConvertTo-Json -Depth 20 | Set-Content -Path $queuePath -Encoding UTF8
& .codex/utilities/write-audit-event.ps1 -EventType ASSIGNED -Actor "PROJECT_MANAGER_AGENT" -WorkItemId $WorkItemId -Module "overall" -Summary "Promoted refined backlog item to READY." -NextAction $item.nextAction
Write-Host "Promoted $WorkItemId to READY."

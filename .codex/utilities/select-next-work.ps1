param(
    [switch]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$queue = Get-Content ".codex/backlog/queue.json" -Raw | ConvertFrom-Json
$ready = @($queue.readyWork) | Where-Object { $_.status -eq "READY" -and @($_.blockers).Count -eq 0 }
$next = $ready | Sort-Object -Property @{Expression = { [int]$_.priorityScore }; Descending = $true }, id | Select-Object -First 1

if (-not $next) {
    if ($Json) { "{}" } else { Write-Host "No unblocked READY work found." }
    exit 0
}

if ($Json) {
    $next | ConvertTo-Json -Depth 10
} else {
    Write-Host "Selected work item: $($next.id)"
    Write-Host "Title: $($next.title)"
    Write-Host "Priority: $($next.priorityScore)"
    Write-Host "Owner: $($next.owner)"
    Write-Host "Validation: $($next.validationProfile)"
    Write-Host "Next action: $($next.nextAction)"
}

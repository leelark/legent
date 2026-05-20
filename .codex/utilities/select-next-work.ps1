param(
    [switch]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-JsonArray($Object, [string]$Name) {
    if ($null -eq $Object.PSObject.Properties[$Name] -or $null -eq $Object.$Name) {
        return @()
    }
    return @($Object.$Name | Where-Object { $null -ne $_ })
}

function Get-JsonProperty($Object, [string]$Name, $Default = $null) {
    if ($null -eq $Object -or $null -eq $Object.PSObject.Properties[$Name]) {
        return $Default
    }
    return $Object.$Name
}

$queue = Get-Content ".codex/backlog/queue.json" -Raw | ConvertFrom-Json
$ready = Get-JsonArray $queue "readyWork" | Where-Object { (Get-JsonProperty $_ "status") -eq "READY" -and @(Get-JsonProperty $_ "blockers" @()).Count -eq 0 }
$next = $ready | Sort-Object -Property @{Expression = { [int](Get-JsonProperty $_ "priorityScore" 0) }; Descending = $true }, id | Select-Object -First 1

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

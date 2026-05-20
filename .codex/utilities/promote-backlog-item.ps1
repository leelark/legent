param(
    [Parameter(Mandatory = $true)][string]$WorkItemId
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "codex-state.ps1")

function Set-JsonProperty($Object, [string]$Name, $Value) {
    if ($null -eq $Object.PSObject.Properties[$Name]) {
        $Object | Add-Member -NotePropertyName $Name -NotePropertyValue $Value
    } else {
        $Object.$Name = $Value
    }
}

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

function Write-JsonFile($Object, [string]$Path, [int]$Depth = 20) {
    Write-CodexJsonFile -Object $Object -Path $Path -Depth $Depth
}

$result = Invoke-CodexStateMutation -Name "promote-backlog-item" -ScriptBlock {
    $queuePath = ".codex/backlog/queue.json"
    $queue = Get-Content -Path $queuePath -Raw | ConvertFrom-Json
    $item = Get-JsonArray $queue "backlogWork" | Where-Object { (Get-JsonProperty $_ "id") -eq $WorkItemId } | Select-Object -First 1
    if (-not $item) { Write-Error "Backlog item not found: $WorkItemId"; exit 1 }
    if (@(Get-JsonProperty $item "blockers" @()).Count -gt 0) { Write-Error "Cannot promote blocked item: $WorkItemId"; exit 1 }
    foreach ($field in @("owner", "scope", "acceptanceCriteria", "validationProfile", "validationCommands", "nextAction")) {
        if ($null -eq (Get-JsonProperty $item $field) -or @(Get-JsonProperty $item $field).Count -eq 0) {
            Write-Error "Cannot promote $WorkItemId; missing $field"
            exit 1
        }
    }

    $item.status = "READY"
    Set-JsonProperty $item "lastUpdated" (Get-Date).ToUniversalTime().ToString("o")
    Set-JsonProperty $queue "backlogWork" @(Get-JsonArray $queue "backlogWork" | Where-Object { (Get-JsonProperty $_ "id") -ne $WorkItemId })
    $readyItems = @(Get-JsonArray $queue "readyWork")
    $readyItems += $item
    Set-JsonProperty $queue "readyWork" $readyItems
    Set-JsonProperty $queue "lastUpdated" (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd")
    Write-JsonFile $queue $queuePath 20
    [pscustomobject]@{
        NextAction = [string]$item.nextAction
    }
}
& .codex/utilities/write-audit-event.ps1 -EventType ASSIGNED -Actor "PROJECT_MANAGER_AGENT" -WorkItemId $WorkItemId -Module "overall" -Summary "Promoted refined backlog item to READY." -NextAction $result.NextAction
Write-Host "Promoted $WorkItemId to READY."

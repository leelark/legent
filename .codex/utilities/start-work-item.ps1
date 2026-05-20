param(
    [Parameter(Mandatory = $true)][string]$ThreadId,
    [Parameter(Mandatory = $true)][string]$WorkItemId,
    [Parameter(Mandatory = $true)][string[]]$FilesInScope
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

function Normalize-StringArray([string[]]$Values) {
    $normalized = @()
    foreach ($value in @($Values)) {
        if ($null -eq $value) { continue }
        foreach ($part in ($value -split ",")) {
            $trimmed = $part.Trim()
            if ($trimmed) { $normalized += $trimmed }
        }
    }
    return $normalized
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

$normalizedFiles = Normalize-StringArray $FilesInScope

$itemSnapshot = Invoke-CodexStateMutation -Name "start-work-item-read" -ScriptBlock {
    $queuePath = ".codex/backlog/queue.json"
    $queue = Get-Content -Path $queuePath -Raw | ConvertFrom-Json
    $candidateItems = @()
    $candidateItems += @(Get-JsonArray $queue "readyWork")
    $candidateItems += @(Get-JsonArray $queue "backlogWork")
    $item = $candidateItems | Where-Object { (Get-JsonProperty $_ "id") -eq $WorkItemId } | Select-Object -First 1
    if (-not $item) { Write-Error "Work item not found in ready/backlog: $WorkItemId"; exit 1 }
    if (@(Get-JsonProperty $item "blockers" @()).Count -gt 0) { Write-Error "Work item has blockers: $WorkItemId"; exit 1 }
    [pscustomobject]@{
        Title = (Get-JsonProperty $item "title" $WorkItemId)
        Owner = (Get-JsonProperty $item "owner" "UNKNOWN_OWNER")
        ValidationCommands = @(Get-JsonProperty $item "validationCommands" @())
        PartnerAgents = @(Get-JsonProperty $item "partnerAgents" @())
        NextAction = (Get-JsonProperty $item "nextAction" "Continue work item.")
    }
}

& .codex/utilities/acquire-lease.ps1 -ThreadId $ThreadId -WorkItemId $WorkItemId -FilesInScope $normalizedFiles
& .codex/utilities/new-checkpoint.ps1 -Id $WorkItemId -Objective $itemSnapshot.Title -Owner $itemSnapshot.Owner -FilesInScope $normalizedFiles -ValidationPlan @($itemSnapshot.ValidationCommands) -Agents @($itemSnapshot.PartnerAgents) -NextAction $itemSnapshot.NextAction

Invoke-CodexStateMutation -Name "start-work-item-queue" -ScriptBlock {
    $queuePath = ".codex/backlog/queue.json"
    $queue = Get-Content -Path $queuePath -Raw | ConvertFrom-Json
    $candidateItems = @()
    $candidateItems += @(Get-JsonArray $queue "readyWork")
    $candidateItems += @(Get-JsonArray $queue "backlogWork")
    $item = $candidateItems | Where-Object { (Get-JsonProperty $_ "id") -eq $WorkItemId } | Select-Object -First 1
    if (-not $item) { Write-Error "Work item no longer available in ready/backlog after lease acquisition: $WorkItemId"; exit 1 }
    if (@(Get-JsonProperty $item "blockers" @()).Count -gt 0) { Write-Error "Work item has blockers after lease acquisition: $WorkItemId"; exit 1 }
    $item.status = "IN_PROGRESS"
    Set-JsonProperty $item "startedAt" (Get-Date).ToUniversalTime().ToString("o")
    Set-JsonProperty $item "lastUpdated" $item.startedAt
    Set-JsonProperty $queue "readyWork" @(Get-JsonArray $queue "readyWork" | Where-Object { (Get-JsonProperty $_ "id") -ne $WorkItemId })
    Set-JsonProperty $queue "backlogWork" @(Get-JsonArray $queue "backlogWork" | Where-Object { (Get-JsonProperty $_ "id") -ne $WorkItemId })
    $inProgressItems = @(Get-JsonArray $queue "inProgressWork")
    $inProgressItems += $item
    Set-JsonProperty $queue "inProgressWork" $inProgressItems
    Set-JsonProperty $queue "lastUpdated" (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd")
    Write-JsonFile $queue $queuePath 20
}

& .codex/utilities/write-audit-event.ps1 -EventType ASSIGNED -Actor $itemSnapshot.Owner -ThreadId $ThreadId -WorkItemId $WorkItemId -Module "unknown" -Files $normalizedFiles -Validation @($itemSnapshot.ValidationCommands) -Summary "Work item started." -NextAction $itemSnapshot.NextAction

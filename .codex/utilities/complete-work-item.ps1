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

$result = Invoke-CodexStateMutation -Name "complete-work-item" -ScriptBlock {
    $queuePath = ".codex/backlog/queue.json"
    $queue = Get-Content -Path $queuePath -Raw | ConvertFrom-Json
    $buckets = @("readyWork", "backlogWork", "inProgressWork", "blockedWork", "doneWork")
    $item = $null
    foreach ($bucket in $buckets) {
        $found = Get-JsonArray $queue $bucket | Where-Object { (Get-JsonProperty $_ "id") -eq $WorkItemId } | Select-Object -First 1
        if ($found) { $item = $found; break }
    }
    if (-not $item) { Write-Error "Work item not found: $WorkItemId"; exit 1 }

    foreach ($bucket in $buckets) {
        if ($null -ne $queue.PSObject.Properties[$bucket]) {
            Set-JsonProperty $queue $bucket @(Get-JsonArray $queue $bucket | Where-Object { (Get-JsonProperty $_ "id") -ne $WorkItemId })
        }
    }
    $item.status = $Status
    Set-JsonProperty $item "completedAt" (Get-Date).ToUniversalTime().ToString("o")
    Set-JsonProperty $item "lastUpdated" $item.completedAt
    Set-JsonProperty $item "outcome" $Summary
    Set-JsonProperty $item "validationRun" @($ValidationRun)
    Set-JsonProperty $item "residualRisks" @($ResidualRisks)
    if ($NextAction) { Set-JsonProperty $item "nextAction" $NextAction }

    if ($Status -eq "DONE") {
        $doneItems = @(Get-JsonArray $queue "doneWork")
        $doneItems += $item
        Set-JsonProperty $queue "doneWork" $doneItems
    } elseif ($Status -eq "BLOCKED") {
        $blockedItems = @(Get-JsonArray $queue "blockedWork")
        $blockedItems += $item
        Set-JsonProperty $queue "blockedWork" $blockedItems
    } else {
        $inProgressItems = @(Get-JsonArray $queue "inProgressWork")
        $inProgressItems += $item
        Set-JsonProperty $queue "inProgressWork" $inProgressItems
    }
    Set-JsonProperty $queue "lastUpdated" (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd")
    Write-JsonFile $queue $queuePath 20

    $leasePath = ".codex/worktrees/leases/active-leases.json"
    $releasedLeaseIds = @()
    if (Test-Path $leasePath) {
        $leases = Get-Content -Path $leasePath -Raw | ConvertFrom-Json
        foreach ($lease in @(Get-JsonArray $leases "leases" | Where-Object { (Get-JsonProperty $_ "workItemId") -eq $WorkItemId -and ((Get-JsonProperty $_ "status") -eq "ACTIVE" -or -not (Get-JsonProperty $_ "status")) })) {
            Set-JsonProperty $lease "status" $(if ($Status -eq "WONT_DO") { "ABANDONED" } else { "RELEASED" })
            Set-JsonProperty $lease "heartbeatAt" (Get-Date).ToUniversalTime().ToString("o")
            $releasedLeaseIds += [string]$lease.leaseId
        }
        Write-JsonFile $leases $leasePath 12
    }
    $threadPath = ".codex/threads/thread-registry.json"
    if (@($releasedLeaseIds).Count -gt 0 -and (Test-Path $threadPath)) {
        $threads = Get-Content -Path $threadPath -Raw | ConvertFrom-Json
        foreach ($thread in @(Get-JsonArray $threads "threads")) {
            $remainingLeaseIds = @(Get-JsonArray $thread "leaseIds" | Where-Object { $releasedLeaseIds -notcontains [string]$_ })
            Set-JsonProperty $thread "leaseIds" $remainingLeaseIds
            Set-JsonProperty $thread "lastUpdated" (Get-Date).ToUniversalTime().ToString("o")
        }
        Write-JsonFile $threads $threadPath 12
    }

    [pscustomobject]@{
        Actor = [string]$item.owner
    }
}

$eventType = if ($Status -eq "DONE") { "DONE" } elseif ($Status -eq "BLOCKED") { "BLOCKED" } elseif ($Status -eq "WONT_DO") { "ABANDONED" } else { "READY_FOR_REVIEW" }
& .codex/utilities/write-audit-event.ps1 -EventType $eventType -Actor $result.Actor -WorkItemId $WorkItemId -Module "unknown" -Validation $ValidationRun -Summary $Summary -NextAction $NextAction
Write-Host "Work item $WorkItemId moved to $Status."

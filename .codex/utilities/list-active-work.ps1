param()

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

$state = Get-Content ".codex/state/team-state.json" -Raw | ConvertFrom-Json
$queue = Get-Content ".codex/backlog/queue.json" -Raw | ConvertFrom-Json
$worktrees = Get-Content ".codex/worktrees/worktree-registry.json" -Raw | ConvertFrom-Json
$leases = Get-Content ".codex/worktrees/leases/active-leases.json" -Raw | ConvertFrom-Json

$activeAgents = @(Get-JsonArray $state "activeAgents")
$activeWork = @(Get-JsonArray $state "activeWork")
$readyItems = @(Get-JsonArray $queue "readyWork" | Where-Object { (Get-JsonProperty $_ "status") -eq "READY" })
$backlogItems = @(Get-JsonArray $queue "backlogWork")
$blockedItems = @(Get-JsonArray $queue "blockedWork")
$activeWorktrees = @(Get-JsonArray $worktrees "activeWorktrees")
$activeLeases = @(Get-JsonArray $leases "leases" | Where-Object { -not (Get-JsonProperty $_ "status") -or (Get-JsonProperty $_ "status") -eq 'ACTIVE' })

Write-Host "Active agents: $($activeAgents.Count)"
Write-Host "Active work: $($activeWork.Count)"
Write-Host "Ready work: $(@($readyItems).Count)"
Write-Host "Backlog work: $($backlogItems.Count)"
Write-Host "Blocked work: $($blockedItems.Count)"
Write-Host "Active worktrees: $($activeWorktrees.Count)"
Write-Host "Active leases: $($activeLeases.Count)"

$next = $readyItems | Sort-Object -Property @{Expression = { [int](Get-JsonProperty $_ "priorityScore" 0) }; Descending = $true }, id | Select-Object -First 1
if ($next) {
    Write-Host "Next READY: $($next.id) score=$($next.priorityScore) owner=$($next.owner)"
    Write-Host "Next action: $($next.nextAction)"
} else {
    Write-Host "No READY work. Run pending-scan and refine-backlog."
}

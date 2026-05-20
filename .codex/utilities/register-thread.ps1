param(
    [Parameter(Mandatory = $true)][string]$ThreadId,
    [Parameter(Mandatory = $true)][ValidateSet("OVERALL", "MODULE")][string]$ThreadRole,
    [Parameter(Mandatory = $true)][string]$Module,
    [string]$BacklogItemId = "",
    [string]$CheckpointId = "",
    [string]$WorktreeId = "",
    [string]$Branch = "",
    [int]$MaxParallelAgents = 6,
    [switch]$SetCoordinator
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail($Message) {
    Write-Error $Message
    exit 1
}

if ($MaxParallelAgents -lt 1 -or $MaxParallelAgents -gt 6) { Fail "MaxParallelAgents must be 1..6." }

$teamRegistryPath = ".codex/teams/module-team-registry.json"
$threadRegistryPath = ".codex/threads/thread-registry.json"
if (-not (Test-Path $teamRegistryPath)) { Fail "Missing team registry: $teamRegistryPath" }
if (-not (Test-Path $threadRegistryPath)) { Fail "Missing thread registry: $threadRegistryPath" }

$teamRegistry = Get-Content -Path $teamRegistryPath -Raw | ConvertFrom-Json
$threadRegistry = Get-Content -Path $threadRegistryPath -Raw | ConvertFrom-Json
$team = @($teamRegistry.teams) | Where-Object { $_.module -eq $Module } | Select-Object -First 1
if (-not $team) { Fail "Unknown module team: $Module" }
if ([string]$team.threadRole -ne $ThreadRole) { Fail "Module $Module requires thread role $($team.threadRole), not $ThreadRole." }

$existing = @($threadRegistry.threads) | Where-Object { $_.threadId -eq $ThreadId } | Select-Object -First 1
$now = (Get-Date).ToUniversalTime().ToString("o")

if ($existing) {
    $existing.status = "ACTIVE"
    $existing.heartbeatAt = $now
    $existing.lastUpdated = $now
    $existing.nextAction = "Continue registered thread work."
} else {
    $record = [ordered]@{
        threadId = $ThreadId
        threadRole = $ThreadRole
        status = "ACTIVE"
        module = $Module
        owner = $team.owner
        backlogItemId = $BacklogItemId
        checkpointId = $CheckpointId
        worktreeId = $WorktreeId
        branch = $Branch
        allowedPaths = @($team.allowedPaths)
        forbiddenPaths = @($team.forbiddenPaths)
        leaseIds = @()
        maxParallelAgents = $MaxParallelAgents
        activeAgents = @()
        heartbeatAt = $now
        startedAt = $now
        lastUpdated = $now
        handoffPath = ".codex/threads/$ThreadId-handoff.md"
        nextAction = "Start $Module autonomous loop."
        notes = "Registered by .codex/utilities/register-thread.ps1. Do not read secrets."
    }
    $threadRegistry.threads = @($threadRegistry.threads) + $record
}

if ($SetCoordinator -or $ThreadRole -eq "OVERALL") {
    $threadRegistry.coordinatorThreadId = $ThreadId
}

$threadRegistry | ConvertTo-Json -Depth 12 | Set-Content -Path $threadRegistryPath -Encoding UTF8
Write-Host "Registered thread $ThreadId for module $Module as $ThreadRole."

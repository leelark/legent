param(
    [Parameter(Mandatory = $true)][string]$WorkItemId,
    [Parameter(Mandatory = $true)][string]$Owner,
    [Parameter(Mandatory = $true)][string]$Scope,
    [string[]]$FilesInScope = @(),
    [string]$CheckpointId = "",
    [switch]$CreateGitWorktree
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$safeId = ($WorkItemId -replace "[^a-zA-Z0-9._-]", "-").Trim("-")
$branch = "codex/$safeId"
$path = ".codex/worktrees/$safeId"
$registryPath = ".codex/worktrees/worktree-registry.json"
$registry = Get-Content -Path $registryPath -Raw | ConvertFrom-Json

if (@($registry.activeWorktrees | Where-Object { $_.id -eq $safeId }).Count -gt 0) {
    Write-Error "Worktree already registered: $safeId"
    exit 1
}

if ($CreateGitWorktree) {
    git worktree add -b $branch $path
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$record = [ordered]@{
    id = $safeId
    branch = $branch
    path = $path
    owner = $Owner
    status = "ACTIVE"
    scope = $Scope
    checkpointId = $CheckpointId
    filesInScope = @($FilesInScope)
    createdAt = (Get-Date).ToUniversalTime().ToString("o")
    lastSeenAt = (Get-Date).ToUniversalTime().ToString("o")
    notes = "No secrets. Preserve unrelated user changes."
}

$items = @($registry.activeWorktrees)
$items += $record
$registry.activeWorktrees = $items
$registry | ConvertTo-Json -Depth 10 | Set-Content -Path $registryPath -Encoding UTF8
Write-Host "Registered worktree: $safeId"

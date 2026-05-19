param(
    [Parameter(Mandatory = $true)][string]$WorkItemId,
    [string]$Status = "ARCHIVED",
    [switch]$RemoveGitWorktree
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$registryPath = ".codex/worktrees/worktree-registry.json"
$registry = Get-Content -Path $registryPath -Raw | ConvertFrom-Json
$active = @($registry.activeWorktrees)
$record = $active | Where-Object { $_.id -eq $WorkItemId } | Select-Object -First 1
if (-not $record) { Write-Error "No active worktree registered for $WorkItemId"; exit 1 }

if ($RemoveGitWorktree -and (Test-Path $record.path)) {
    git worktree remove $record.path
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$record.status = $Status
$record.lastSeenAt = (Get-Date).ToUniversalTime().ToString("o")
$registry.activeWorktrees = @($active | Where-Object { $_.id -ne $WorkItemId })
$registry.archivedWorktrees = @($registry.archivedWorktrees) + $record
$registry | ConvertTo-Json -Depth 10 | Set-Content -Path $registryPath -Encoding UTF8
Write-Host "Closed worktree record: $WorkItemId"

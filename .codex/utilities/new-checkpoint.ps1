param(
    [Parameter(Mandatory = $true)][string]$Id,
    [Parameter(Mandatory = $true)][string]$Objective,
    [Parameter(Mandatory = $true)][string]$Owner,
    [string[]]$FilesInScope = @(),
    [string[]]$ValidationPlan = @(),
    [string[]]$Agents = @(),
    [string[]]$Blockers = @(),
    [string]$Status = "IN_PROGRESS",
    [string]$RollbackNotes = "Preserve unrelated user changes; revert only this checkpoint scope if explicitly approved.",
    [Parameter(Mandatory = $true)][string]$NextAction
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$safeId = ($Id -replace "[^a-zA-Z0-9._-]", "-").Trim("-")
if (-not $safeId) { Write-Error "Checkpoint id produced an empty safe filename."; exit 1 }
$timestamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$path = Join-Path ".codex/checkpoints" "$timestamp-$safeId.json"

$checkpoint = [ordered]@{
    id = $safeId
    createdAt = (Get-Date).ToUniversalTime().ToString("o")
    objective = $Objective
    status = $Status
    owner = $Owner
    filesInScope = @($FilesInScope)
    agents = @($Agents)
    validationPlan = @($ValidationPlan)
    rollbackNotes = $RollbackNotes
    blockers = @($Blockers)
    nextAction = $NextAction
}

$checkpoint | ConvertTo-Json -Depth 8 | Set-Content -Path $path -Encoding UTF8
Write-Host "Created checkpoint: $path"

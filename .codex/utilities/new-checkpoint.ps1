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
. (Join-Path $PSScriptRoot "codex-state.ps1")

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

function Write-JsonFile($Object, [string]$Path, [int]$Depth = 20) {
    Write-CodexJsonFile -Object $Object -Path $Path -Depth $Depth
}

$path = Invoke-CodexStateMutation -Name "new-checkpoint" -ScriptBlock {
    $safeId = ($Id -replace "[^a-zA-Z0-9._-]", "-").Trim("-")
    if (-not $safeId) { Write-Error "Checkpoint id produced an empty safe filename."; exit 1 }
    $timestamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
    $checkpointPath = Join-Path ".codex/checkpoints" "$timestamp-$safeId.json"

    $normalizedFiles = Normalize-StringArray $FilesInScope
    $normalizedValidation = Normalize-StringArray $ValidationPlan
    $normalizedAgents = Normalize-StringArray $Agents
    $normalizedBlockers = Normalize-StringArray $Blockers

    $checkpoint = [ordered]@{
        id = $safeId
        createdAt = (Get-Date).ToUniversalTime().ToString("o")
        objective = $Objective
        status = $Status
        owner = $Owner
        filesInScope = @($normalizedFiles)
        agents = @($normalizedAgents)
        validationPlan = @($normalizedValidation)
        rollbackNotes = $RollbackNotes
        blockers = @($normalizedBlockers)
        nextAction = $NextAction
    }

    Write-JsonFile $checkpoint $checkpointPath 8
    $checkpointPath
}
Write-Host "Created checkpoint: $path"

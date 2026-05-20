param(
    [Parameter(Mandatory = $true)][string]$Path,
    [string]$Status,
    [string]$NextAction,
    [string[]]$Blockers
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

function Write-JsonFile($Object, [string]$Path, [int]$Depth = 20) {
    Write-CodexJsonFile -Object $Object -Path $Path -Depth $Depth
}

Invoke-CodexStateMutation -Name "update-checkpoint" -ScriptBlock {
    if (-not (Test-Path $Path)) { Write-Error "Checkpoint not found: $Path"; exit 1 }
    $checkpoint = Get-Content -Path $Path -Raw | ConvertFrom-Json
    if ($Status) { Set-JsonProperty $checkpoint "status" $Status }
    if ($NextAction) { Set-JsonProperty $checkpoint "nextAction" $NextAction }
    if ($PSBoundParameters.ContainsKey("Blockers")) { Set-JsonProperty $checkpoint "blockers" @($Blockers) }
    Write-JsonFile $checkpoint $Path 8
}
Write-Host "Updated checkpoint: $Path"

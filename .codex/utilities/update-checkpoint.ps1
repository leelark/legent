param(
    [Parameter(Mandatory = $true)][string]$Path,
    [string]$Status,
    [string]$NextAction,
    [string[]]$Blockers
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not (Test-Path $Path)) { Write-Error "Checkpoint not found: $Path"; exit 1 }
$checkpoint = Get-Content -Path $Path -Raw | ConvertFrom-Json
if ($Status) { $checkpoint.status = $Status }
if ($NextAction) { $checkpoint.nextAction = $NextAction }
if ($PSBoundParameters.ContainsKey("Blockers")) { $checkpoint.blockers = @($Blockers) }
$checkpoint | ConvertTo-Json -Depth 8 | Set-Content -Path $Path -Encoding UTF8
Write-Host "Updated checkpoint: $Path"

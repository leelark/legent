param(
    [Parameter(Mandatory = $true)][string]$Module,
    [string]$ThreadId = "",
    [string]$BacklogItemId = "",
    [switch]$Overall
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$registry = Get-Content ".codex/teams/module-team-registry.json" -Raw | ConvertFrom-Json
$team = @($registry.teams) | Where-Object { $_.module -eq $Module } | Select-Object -First 1
if (-not $team) {
    Write-Error "Unknown module team: $Module"
    exit 1
}
if (-not $ThreadId) {
    $ThreadId = if ($team.threadRole -eq "OVERALL") { "overall-" + (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ") } else { "$Module-" + (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ") }
}

$promptPath = if ($Overall -or $team.threadRole -eq "OVERALL") { ".codex/prompts/overall-24x7.md" } else { [string]$team.promptPath }
if (-not (Test-Path $promptPath)) {
    Write-Error "Prompt file missing: $promptPath"
    exit 1
}

$prompt = Get-Content -Path $promptPath -Raw
$prompt = $prompt.Replace("{{MODULE}}", [string]$team.module)
$prompt = $prompt.Replace("{{THREAD_ID}}", $ThreadId)
$prompt = $prompt.Replace("{{OWNER}}", [string]$team.owner)
$prompt = $prompt.Replace("{{VALIDATION_PROFILE}}", [string]$team.validationProfile)
$prompt = $prompt.Replace("{{BACKLOG_ITEM_ID}}", $BacklogItemId)
$prompt = $prompt.Replace("{{ALLOWED_PATHS}}", (@($team.allowedPaths) -join ", "))
$prompt = $prompt.Replace("{{FORBIDDEN_PATHS}}", (@($team.forbiddenPaths) -join ", "))
$prompt = $prompt.Replace("{{MEMORY_TARGETS}}", (@($team.memoryTargets) -join ", "))

Write-Output $prompt

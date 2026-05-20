param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$registry = Get-Content ".codex/teams/module-team-registry.json" -Raw | ConvertFrom-Json
foreach ($team in @($registry.teams)) {
    Write-Host "$($team.module) [$($team.threadRole)] owner=$($team.owner) validation=$($team.validationProfile)"
}

param(
    [string] $OverlayPath = "infrastructure/kubernetes/overlays/production"
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")

Push-Location $repoRoot
try {
    $rendered = (kubectl kustomize $OverlayPath) -join "`n"
} finally {
    Pop-Location
}

if ($rendered -match "image:\s+legent/[^:\s]+:latest\b") {
    throw "Production overlay renders a legent/*:latest image"
}

if ($rendered -match "\.local\b") {
    throw "Production overlay renders a .local host or URL"
}

if ($rendered -notmatch "CLICKHOUSE_DB:\s+legent_analytics\b") {
    throw "Production overlay must set CLICKHOUSE_DB to legent_analytics"
}

Write-Host "Production overlay validation passed"

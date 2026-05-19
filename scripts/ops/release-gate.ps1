param(
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$SkipCompose,
    [switch]$SkipKustomize,
    [switch]$LocalOnly,
    [switch]$RequireExternalEgressEvidence,
    [string]$ExternalEgressEvidencePath,
    [switch]$RequireGaEvidence,
    [string]$EvidenceDir,
    [switch]$RequireImageDigests,
    [switch]$RequireImageEvidence,
    [string]$ImageEvidenceManifest,
    [string]$ImageEvidenceRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail($Message) {
    Write-Error $Message
    exit 1
}

function Run-Step($Name, [scriptblock]$Step) {
    Write-Host "== $Name =="
    $global:LASTEXITCODE = 0
    & $Step
    $stepSucceeded = $?
    if (-not $stepSucceeded) { Fail "$Name failed." }
    $global:LASTEXITCODE = 0
}

if (-not $LocalOnly -and -not ($RequireExternalEgressEvidence -and $RequireGaEvidence -and $RequireImageEvidence -and $RequireImageDigests)) {
    Fail "release-gate requires strict evidence flags for promotion. Use -LocalOnly only for non-promotional local validation."
}

if ($RequireExternalEgressEvidence) {
    if (-not $ExternalEgressEvidencePath) { Fail "External egress evidence path is required." }
    Run-Step "external egress evidence" { & scripts/ops/validate-production-egress-evidence.ps1 -EvidencePath $ExternalEgressEvidencePath }
}

if ($RequireGaEvidence) {
    if (-not $EvidenceDir) { Fail "GA evidence directory is required." }
    Run-Step "GA evidence" { & scripts/ops/validate-ga-evidence.ps1 -EvidenceDir $EvidenceDir }
}

if ($RequireImageEvidence) {
    if (-not $ImageEvidenceManifest) { Fail "Image evidence manifest is required." }
    if (-not $ImageEvidenceRoot) { $ImageEvidenceRoot = Split-Path -Parent $ImageEvidenceManifest }
    Run-Step "image evidence" { & scripts/ops/validate-image-evidence.ps1 -ManifestPath $ImageEvidenceManifest -EvidenceRoot $ImageEvidenceRoot -RequireDigests:$RequireImageDigests -KustomizationPath "infrastructure/kubernetes/overlays/production/kustomization.yml" }
}

Run-Step "codex autonomous system" { & .codex/utilities/validate-codex-system.ps1 }
Run-Step "route map" { & scripts/ops/validate-route-map.ps1 }
Run-Step "repo artifact hygiene" { & scripts/ops/validate-repo-artifact-hygiene.ps1 }
Run-Step "production overlay" { & scripts/ops/validate-production-overlay.ps1 -RequireImageDigests:$RequireImageDigests }

if (-not $SkipCompose) {
    Run-Step "docker compose config" { docker compose config --quiet }
}
if (-not $SkipKustomize) {
    Run-Step "production kustomize render" { kubectl kustomize infrastructure/kubernetes/overlays/production | Out-Null }
}
if (-not $SkipBackend) {
    Run-Step "backend tests" {
        $isWindowsVariable = Get-Variable -Name IsWindows -ErrorAction SilentlyContinue
        $isWindowsHost = ($env:OS -eq "Windows_NT") -or ($isWindowsVariable -and [bool]$isWindowsVariable.Value)
        if ($isWindowsHost) {
            .\mvnw.cmd test
        } else {
            ./mvnw test
        }
    }
}
if (-not $SkipFrontend) {
    Push-Location frontend
    try {
        Run-Step "frontend lint" { npm run lint }
        Run-Step "frontend build" { npm run build:ci }
        Run-Step "frontend smoke" { npm run test:e2e:smoke }
    } finally {
        Pop-Location
    }
}

Write-Host "Release gate completed. Strict release evidence may still be required by policy."

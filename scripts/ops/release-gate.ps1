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
    [string]$ImageEvidenceRoot,
    [string]$ComposeEnvFile = ".env.example"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$script:PowerShellExecutable = (Get-Process -Id $PID).Path
if ([string]::IsNullOrWhiteSpace($script:PowerShellExecutable)) {
    $script:PowerShellExecutable = if ($PSVersionTable.PSEdition -eq "Core") { "pwsh" } else { "powershell" }
}

function Fail($Message) {
    Write-Error $Message
    exit 1
}

function Run-Step($Name, [scriptblock]$Step) {
    Write-Host "== $Name =="
    $global:LASTEXITCODE = 0
    & $Step
    $stepSucceeded = $?
    $stepExitCode = $global:LASTEXITCODE
    if (-not $stepSucceeded -or $stepExitCode -ne 0) { Fail "$Name failed." }
    $global:LASTEXITCODE = 0
}

if (-not $LocalOnly -and -not ($RequireExternalEgressEvidence -and $RequireGaEvidence -and $RequireImageEvidence -and $RequireImageDigests)) {
    Fail "release-gate requires strict evidence flags for promotion. Use -LocalOnly only for non-promotional local validation."
}

$skipFlags = @()
if ($SkipBackend) { $skipFlags += "-SkipBackend" }
if ($SkipFrontend) { $skipFlags += "-SkipFrontend" }
if ($SkipCompose) { $skipFlags += "-SkipCompose" }
if ($SkipKustomize) { $skipFlags += "-SkipKustomize" }
if (-not $LocalOnly -and @($skipFlags).Count -gt 0) {
    Fail "Strict release promotion cannot use local gate skip flags: $($skipFlags -join ', '). Use -LocalOnly for non-promotional local validation."
}

if ($RequireExternalEgressEvidence) {
    if (-not $ExternalEgressEvidencePath) { Fail "External egress evidence path is required." }
    Run-Step "external egress evidence and render proof" { & $script:PowerShellExecutable -ExecutionPolicy Bypass -File scripts/ops/validate-production-egress-policy-render.ps1 -EvidencePath $ExternalEgressEvidencePath }
}

if ($RequireGaEvidence) {
    if (-not $EvidenceDir) { Fail "GA evidence directory is required." }
    Run-Step "GA evidence" { & $script:PowerShellExecutable -ExecutionPolicy Bypass -File scripts/ops/validate-ga-evidence.ps1 -EvidenceDir $EvidenceDir }
}

if ($RequireImageEvidence) {
    if (-not $ImageEvidenceManifest) { Fail "Image evidence manifest is required." }
    if (-not $ImageEvidenceRoot) { $ImageEvidenceRoot = Split-Path -Parent $ImageEvidenceManifest }
    Run-Step "image evidence" {
        if ($RequireImageDigests) {
            & $script:PowerShellExecutable -ExecutionPolicy Bypass -File scripts/ops/validate-image-evidence.ps1 -ManifestPath $ImageEvidenceManifest -EvidenceRoot $ImageEvidenceRoot -RequireDigests -KustomizationPath "infrastructure/kubernetes/overlays/production/kustomization.yml"
        } else {
            & $script:PowerShellExecutable -ExecutionPolicy Bypass -File scripts/ops/validate-image-evidence.ps1 -ManifestPath $ImageEvidenceManifest -EvidenceRoot $ImageEvidenceRoot -KustomizationPath "infrastructure/kubernetes/overlays/production/kustomization.yml"
        }
    }
}

Run-Step "codex autonomous system" { & $script:PowerShellExecutable -ExecutionPolicy Bypass -File .codex/utilities/validate-codex-system.ps1 }
Run-Step "route map" { & $script:PowerShellExecutable -ExecutionPolicy Bypass -File scripts/ops/validate-route-map.ps1 }
Run-Step "repo artifact hygiene" { & $script:PowerShellExecutable -ExecutionPolicy Bypass -File scripts/ops/validate-repo-artifact-hygiene.ps1 }
Run-Step "production overlay" {
    if ($RequireImageDigests) {
        & $script:PowerShellExecutable -ExecutionPolicy Bypass -File scripts/ops/validate-production-overlay.ps1 -RequireImageDigests
    } else {
        & $script:PowerShellExecutable -ExecutionPolicy Bypass -File scripts/ops/validate-production-overlay.ps1
    }
}

if (-not $SkipCompose) {
    if (-not (Test-Path $ComposeEnvFile)) { Fail "Compose env file is required for reproducible local validation: $ComposeEnvFile" }
    Run-Step "docker compose safety" { & $script:PowerShellExecutable -ExecutionPolicy Bypass -File scripts/ops/validate-compose-safety.ps1 -ComposeEnvFile $ComposeEnvFile }
    Run-Step "docker compose config" { docker compose --env-file $ComposeEnvFile config --quiet }
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
        Run-Step "frontend visual smoke" { npm run test:e2e:visual }
        Run-Step "frontend full Chromium" { npm run test:e2e:chromium }
        Run-Step "frontend smoke" { npm run test:e2e:smoke }
    } finally {
        Pop-Location
    }
}

if ($LocalOnly) {
    Write-Host "Release gate completed in LOCAL-ONLY mode. This is not production promotion evidence."
} else {
    Write-Host "Release gate completed with strict evidence flags. Production promotion still depends on all required target evidence remaining current and reviewed."
}

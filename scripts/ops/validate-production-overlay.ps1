param(
    [string]$OverlayPath = "infrastructure/kubernetes/overlays/production",
    [switch]$RequireImageDigests
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail($Message) {
    Write-Error $Message
    exit 1
}

$kustomization = Join-Path $OverlayPath "kustomization.yml"
$networkPolicy = Join-Path $OverlayPath "network-policy.yml"
$externalSecrets = Join-Path $OverlayPath "external-secrets.yml"

if (-not (Test-Path $kustomization)) { Fail "Missing production kustomization: $kustomization" }
if (-not (Test-Path $networkPolicy)) { Fail "Missing production network policy: $networkPolicy" }
if (-not (Test-Path $externalSecrets)) { Fail "Missing production external secrets: $externalSecrets" }

$k = Get-Content -Path $kustomization -Raw
$np = Get-Content -Path $networkPolicy -Raw
$es = Get-Content -Path $externalSecrets -Raw
$errors = New-Object System.Collections.Generic.List[string]

foreach ($required in @(
    "external-secrets.yml",
    "network-policy.yml",
    "delete-base-secret.yml",
    "delete-nonprod-stateful.yml",
    "delete-base-egress-policy.yml",
    "deployment-security-patch.yml",
    "deployment-rollout-patch.yml"
)) {
    if ($k -notmatch [regex]::Escape($required)) {
        $errors.Add("Production kustomization missing $required")
    }
}

if ($np -notmatch "production-default-deny") {
    $errors.Add("Production default-deny NetworkPolicy is missing")
}
if ($np -match "0\.0\.0\.0/0") {
    $errors.Add("Production network policy contains broad 0.0.0.0/0 egress")
}
if ($RequireImageDigests -and $k -notmatch "digest:\s*sha256:[a-fA-F0-9]{64}") {
    $errors.Add("Strict image digest mode requires sha256 digests in production kustomization")
}
if ($RequireImageDigests) {
    $currentImage = $null
    $imageDigests = @{}
    foreach ($line in (Get-Content -Path $kustomization)) {
        if ($line -match '^\s*-\s+name:\s*"?([^"\s]+)"?\s*$') {
            $currentImage = $Matches[1]
            $imageDigests[$currentImage] = $null
            continue
        }
        if ($currentImage -and $line -match '^\s*digest:\s*"?([^"\s]+)"?\s*$') {
            $imageDigests[$currentImage] = $Matches[1]
            continue
        }
    }
    foreach ($imageName in $imageDigests.Keys) {
        if ([string]$imageDigests[$imageName] -notmatch '^sha256:[a-fA-F0-9]{64}$') {
            $errors.Add("Production image $imageName must be digest-pinned in strict mode")
        }
    }
}
if (-not $RequireImageDigests -and $k -notmatch 'newTag:\s*"1\.0\.2"') {
    $errors.Add("Production images should currently be pinned to release tag 1.0.2 or a reviewed digest update")
}
if ($es -match "replace_with|changeme|minioadmin|password") {
    Write-Warning "External secret template contains placeholder-like text; verify this is not rendered as a real Secret."
}
if ($k -match "postgres\.yml|redis\.yml|kafka\.yml|minio\.yml|opensearch\.yml|clickhouse\.yml|mailhog\.yml") {
    $errors.Add("Production overlay appears to include local stateful resources directly")
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Host "Production overlay validation passed."

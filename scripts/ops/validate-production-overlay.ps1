param(
    [string]$OverlayPath = "infrastructure/kubernetes/overlays/production",
    [switch]$RequireImageDigests,
    [string]$PrometheusAlertsPath = "infrastructure/kubernetes/observability/prometheus-alerts.yml"
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
$prometheusAlerts = $PrometheusAlertsPath

if (-not (Test-Path $kustomization)) { Fail "Missing production kustomization: $kustomization" }
if (-not (Test-Path $networkPolicy)) { Fail "Missing production network policy: $networkPolicy" }
if (-not (Test-Path $externalSecrets)) { Fail "Missing production external secrets: $externalSecrets" }

$k = Get-Content -Path $kustomization -Raw
$np = Get-Content -Path $networkPolicy -Raw
$es = Get-Content -Path $externalSecrets -Raw
$errors = New-Object System.Collections.Generic.List[string]

function ConvertTo-MarkdownAnchor([string]$Heading) {
    $anchor = $Heading.Trim().ToLowerInvariant()
    $anchor = $anchor -replace '`([^`]+)`', '$1'
    $anchor = $anchor -replace '\[([^\]]+)\]\([^)]+\)', '$1'
    $anchor = $anchor -replace '[^a-z0-9\s-]', ''
    $anchor = $anchor -replace '\s+', '-'
    $anchor = $anchor -replace '-+', '-'
    return $anchor.Trim('-')
}

function Get-MarkdownAnchors([string]$Path) {
    $anchors = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($line in (Get-Content -Path $Path)) {
        if ($line -match '^\s{0,3}#{1,6}\s+(.+?)\s*#*\s*$') {
            $anchor = ConvertTo-MarkdownAnchor $Matches[1]
            if ($anchor) { [void]$anchors.Add($anchor) }
        }
        if ($line -match '<a\s+[^>]*(?:id|name)=["'']([^"'']+)["'']') {
            [void]$anchors.Add($Matches[1])
        }
    }
    return $anchors
}

function Test-RunbookTarget([string]$RunbookUrl) {
    if ($RunbookUrl -match '^[a-z][a-z0-9+.-]*://') { return }
    $parts = $RunbookUrl -split '#', 2
    $runbookPath = $parts[0]
    if ([string]::IsNullOrWhiteSpace($runbookPath)) {
        $errors.Add("Alert runbook_url missing local path: $RunbookUrl")
        return
    }
    if ([System.IO.Path]::IsPathRooted($runbookPath)) {
        $errors.Add("Alert runbook_url must be repository-relative: $RunbookUrl")
        return
    }
    $resolvedRunbookPath = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $runbookPath))
    $repoRoot = [System.IO.Path]::GetFullPath((Get-Location).Path).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $resolvedRunbookPath.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        $errors.Add("Alert runbook_url escapes repository root: $RunbookUrl")
        return
    }
    if (-not (Test-Path $resolvedRunbookPath)) {
        $errors.Add("Alert runbook_url target is missing: $RunbookUrl")
        return
    }
    if ($parts.Count -eq 2 -and -not [string]::IsNullOrWhiteSpace($parts[1])) {
        $anchors = Get-MarkdownAnchors $resolvedRunbookPath
        if (-not $anchors.Contains($parts[1])) {
            $errors.Add("Alert runbook_url anchor is missing: $RunbookUrl")
        }
    }
}

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
if (Test-Path $prometheusAlerts) {
    $alertRules = Get-Content -Path $prometheusAlerts -Raw
    foreach ($match in [regex]::Matches($alertRules, '(?ms)^\s*-\s*alert:\s*(?<name>[^\r\n]+)(?<body>.*?)(?=^\s*-\s*alert:|\z)')) {
        $alertName = $match.Groups["name"].Value.Trim().Trim('"', "'")
        $body = $match.Groups["body"].Value
        $runbookMatch = [regex]::Match($body, 'runbook_url:\s*["'']([^"'']+)["'']')
        if (-not $runbookMatch.Success) {
            $errors.Add("Alert $alertName is missing runbook_url")
            continue
        }
        Test-RunbookTarget $runbookMatch.Groups[1].Value
    }
} else {
    $errors.Add("Missing production alert rules: $prometheusAlerts")
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Host "Production overlay validation passed."

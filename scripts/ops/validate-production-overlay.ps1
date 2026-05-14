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

$requiredSecretKeys = @(
    "DB_USER",
    "DB_PASSWORD",
    "SPRING_DATASOURCE_PASSWORD",
    "REDIS_PASSWORD",
    "LEGENT_SECURITY_JWT_SECRET",
    "LEGENT_TRACKING_SIGNING_KEY",
    "LEGENT_DELIVERY_CREDENTIAL_KEY",
    "LEGENT_DELIVERY_ENCRYPTION_SALT",
    "CLICKHOUSE_PASSWORD",
    "MINIO_ACCESS_KEY",
    "MINIO_SECRET_KEY",
    "MAIL_USERNAME",
    "MAIL_PASSWORD",
    "CORS_ALLOWED_ORIGINS",
    "LEGENT_INTERNAL_API_TOKEN"
)

foreach ($secretKey in $requiredSecretKeys) {
    if ($rendered -notmatch "(?m)^\s*secretKey:\s+$([regex]::Escape($secretKey))\s*$") {
        throw "Production ExternalSecret must provide required runtime key $secretKey"
    }
}

$documents = $rendered -split "(?m)^---\s*$"
$egressFindings = @()
foreach ($document in $documents) {
    if ($document -notmatch "(?m)^kind:\s+NetworkPolicy\s*$") {
        continue
    }

    if ($document -match "(?m)^\s+name:\s+allow-legent-egress\s*$") {
        $egressFindings += "Production overlay must not inherit broad base NetworkPolicy allow-legent-egress"
    }

    if (
        $document -match "(?ms)^\s*-\s*ipBlock:\s*\r?\n(?:\s+.*\r?\n)*?\s+cidr:\s+0\.0\.0\.0/0\s*$" -or
        $document -match "(?ms)^\s*ipBlock:\s*\r?\n(?:\s+.*\r?\n)*?\s+cidr:\s+0\.0\.0\.0/0\s*$"
    ) {
        $egressFindings += "Production overlay must not render NetworkPolicy egress ipBlock cidr 0.0.0.0/0"
    }
}

if ($egressFindings.Count -gt 0) {
    throw (($egressFindings + "Define reviewed production-specific egress before release.") -join "; ")
}

Write-Host "Production overlay validation passed"

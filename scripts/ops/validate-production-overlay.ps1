param(
    [string] $OverlayPath = "infrastructure/kubernetes/overlays/production",
    [switch] $RequireImageDigests,
    [switch] $RequireImageEvidence,
    [string] $ImageEvidenceManifest = $env:LEGENT_IMAGE_EVIDENCE_MANIFEST
)

$ErrorActionPreference = "Stop"

function Test-EnvFlag {
    param([string] $Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $false
    }

    $normalized = $Value.Trim().ToLowerInvariant()
    return @("1", "true", "yes", "y", "on", "required") -contains $normalized
}

function Get-ObjectPropertyValue {
    param(
        $InputObject,
        [Parameter(Mandatory = $true)] [string[]] $Names
    )

    if ($null -eq $InputObject) {
        return $null
    }

    foreach ($name in $Names) {
        $property = $InputObject.PSObject.Properties[$name]
        if ($null -ne $property) {
            return $property.Value
        }
    }

    return $null
}

function Test-HasText {
    param($Value)

    if ($null -eq $Value) {
        return $false
    }

    if ($Value -is [string]) {
        return -not [string]::IsNullOrWhiteSpace($Value)
    }

    return $false
}

function Test-Verified {
    param($Value)

    if ($Value -is [bool]) {
        return $Value
    }

    if ($Value -is [string]) {
        return @("true", "verified", "pass", "passed", "success") -contains $Value.Trim().ToLowerInvariant()
    }

    return $false
}

function Get-ImageDigest {
    param([string] $Image)

    if ($Image -match "@(?<digest>sha256:[a-f0-9]{64})$") {
        return $Matches["digest"]
    }

    return $null
}

function Get-EvidenceReference {
    param($Evidence)

    if ($Evidence -is [string]) {
        if (Test-HasText $Evidence) {
            return $Evidence
        }

        return $null
    }

    foreach ($name in @("transcript", "transcriptPath", "uri", "url", "path", "artifact", "artifactUri", "log", "command", "summary")) {
        $value = Get-ObjectPropertyValue $Evidence @($name)
        if (Test-HasText $value) {
            return $value
        }
    }

    return $null
}

function Assert-SbomEvidence {
    param(
        [Parameter(Mandatory = $true)] $Entry,
        [Parameter(Mandatory = $true)] [string] $Image
    )

    $sbom = Get-ObjectPropertyValue $Entry @("sbom", "sbomEvidence")
    if ($null -eq $sbom) {
        throw "Image evidence manifest entry for $Image is missing sbom evidence"
    }

    if ($null -eq (Get-EvidenceReference $sbom)) {
        throw "Image evidence manifest entry for $Image must include an SBOM uri/path/artifact reference"
    }

    $sbomDigest = Get-ObjectPropertyValue $sbom @("digest", "sbomDigest")
    if ((Test-HasText $sbomDigest) -and $sbomDigest -notmatch "^sha256:[a-f0-9]{64}$") {
        throw "Image evidence manifest entry for $Image has invalid SBOM digest $sbomDigest"
    }
}

function Assert-VerificationEvidence {
    param(
        [Parameter(Mandatory = $true)] $Entry,
        [Parameter(Mandatory = $true)] [string] $Image,
        [Parameter(Mandatory = $true)] [string] $Kind,
        [Parameter(Mandatory = $true)] [string[]] $PropertyNames
    )

    $evidence = Get-ObjectPropertyValue $Entry $PropertyNames
    if ($null -eq $evidence) {
        throw "Image evidence manifest entry for $Image is missing $Kind verification evidence"
    }

    $verified = Get-ObjectPropertyValue $evidence @("verified", "verificationPassed", "passed")
    if (-not (Test-Verified $verified)) {
        throw "Image evidence manifest entry for $Image must mark $Kind verification as verified"
    }

    if ($null -eq (Get-EvidenceReference $evidence)) {
        throw "Image evidence manifest entry for $Image must include a $Kind verification transcript/path/command reference"
    }
}

function Assert-ImageEvidenceManifest {
    param(
        [Parameter(Mandatory = $true)] [string] $ManifestPath,
        [Parameter(Mandatory = $true)] [string[]] $RenderedImages
    )

    if (-not (Test-Path -LiteralPath $ManifestPath)) {
        throw "Image evidence manifest not found: $ManifestPath"
    }

    $manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
    $manifestImages = Get-ObjectPropertyValue $manifest @("images")
    if ($null -eq $manifestImages) {
        throw "Image evidence manifest must contain an images array"
    }

    $entries = @($manifestImages)
    if ($entries.Count -eq 0) {
        throw "Image evidence manifest must contain an images array"
    }

    $entryByImage = @{}
    foreach ($entry in $entries) {
        $image = Get-ObjectPropertyValue $entry @("image", "renderedImage", "imageReference")
        if (-not (Test-HasText $image)) {
            throw "Image evidence manifest contains an entry without an image reference"
        }

        if ($entryByImage.ContainsKey($image)) {
            throw "Image evidence manifest contains duplicate entry for $image"
        }

        $entryByImage[$image] = $entry
    }

    $renderedSet = @{}
    foreach ($image in $RenderedImages) {
        $renderedSet[$image] = $true
    }

    $missing = @($RenderedImages | Where-Object { -not $entryByImage.ContainsKey($_) })
    if ($missing.Count -gt 0) {
        throw "Image evidence manifest is missing rendered legent images: $($missing -join ', ')"
    }

    $extra = @($entryByImage.Keys | Where-Object { -not $renderedSet.ContainsKey($_) } | Sort-Object)
    if ($extra.Count -gt 0) {
        throw "Image evidence manifest contains stale/non-rendered legent images: $($extra -join ', ')"
    }

    foreach ($image in $RenderedImages) {
        $entry = $entryByImage[$image]
        $renderedDigest = Get-ImageDigest $image
        if (-not (Test-HasText $renderedDigest)) {
            throw "Image evidence requires digest-pinned rendered images; tag-only image found: $image"
        }

        $manifestDigest = Get-ObjectPropertyValue $entry @("digest", "imageDigest")
        if (-not (Test-HasText $manifestDigest)) {
            throw "Image evidence manifest entry for $image is missing digest"
        }

        if ($manifestDigest -notmatch "^sha256:[a-f0-9]{64}$") {
            throw "Image evidence manifest entry for $image has invalid digest $manifestDigest"
        }

        if ($manifestDigest -ne $renderedDigest) {
            throw "Image evidence manifest digest for $image does not match rendered image digest"
        }

        Assert-SbomEvidence -Entry $entry -Image $image
        Assert-VerificationEvidence -Entry $entry -Image $image -Kind "signature" -PropertyNames @("signature", "signatureVerification")
        Assert-VerificationEvidence -Entry $entry -Image $image -Kind "provenance" -PropertyNames @("provenance", "provenanceVerification")
    }

    Write-Host "Image evidence manifest validation passed for $($RenderedImages.Count) legent images"
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$requireImageDigestsStrict = $RequireImageDigests -or (Test-EnvFlag $env:LEGENT_REQUIRE_IMAGE_DIGESTS)
$requireImageEvidenceStrict = $RequireImageEvidence -or (Test-EnvFlag $env:LEGENT_REQUIRE_IMAGE_EVIDENCE) -or (-not [string]::IsNullOrWhiteSpace($ImageEvidenceManifest))

Push-Location $repoRoot
try {
    $rendered = (kubectl kustomize $OverlayPath) -join "`n"
    if ($LASTEXITCODE -ne 0) {
        throw "kubectl kustomize $OverlayPath failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

if ($rendered -match "image:\s+legent/[^:\s]+:latest\b") {
    throw "Production overlay renders a legent/*:latest image"
}

$legentImageLines = @([regex]::Matches($rendered, "(?m)^\s*image:\s+(legent/[^\s]+)\s*$") | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
if ($requireImageDigestsStrict -or $requireImageEvidenceStrict) {
    $missingDigests = @($legentImageLines | Where-Object { $_ -notmatch "@sha256:[a-f0-9]{64}$" })
    if ($missingDigests.Count -gt 0) {
        throw "Production image digest evidence is required but these legent images are not digest-pinned: $($missingDigests -join ', ')"
    }
} elseif ($legentImageLines | Where-Object { $_ -notmatch "@sha256:[a-f0-9]{64}$" }) {
    Write-Warning "Production legent images are version-tagged but not digest-pinned. Attach registry digest/signature/provenance evidence before promotion, or rerun with -RequireImageDigests after pinning digests."
}

if ($requireImageEvidenceStrict) {
    if ([string]::IsNullOrWhiteSpace($ImageEvidenceManifest)) {
        throw "Production image evidence manifest is required. Provide -ImageEvidenceManifest or LEGENT_IMAGE_EVIDENCE_MANIFEST."
    }

    $manifestPath = if ([System.IO.Path]::IsPathRooted($ImageEvidenceManifest)) {
        $ImageEvidenceManifest
    } else {
        Join-Path $repoRoot $ImageEvidenceManifest
    }
    $manifestPath = (Resolve-Path -LiteralPath $manifestPath).Path
    Assert-ImageEvidenceManifest -ManifestPath $manifestPath -RenderedImages $legentImageLines
}

if ($rendered -match "\.local\b") {
    throw "Production overlay renders a .local host or URL"
}

if ($rendered -notmatch "CLICKHOUSE_DB:\s+legent_analytics\b") {
    throw "Production overlay must set CLICKHOUSE_DB to legent_analytics"
}

$requiredRuntimeFlags = @{
    "SPRING_JPA_HIBERNATE_DDL_AUTO" = "validate"
    "SPRING_FLYWAY_BASELINE_ON_MIGRATE" = "false"
    "SPRING_FLYWAY_VALIDATE_ON_MIGRATE" = "true"
    "SPRING_FLYWAY_OUT_OF_ORDER" = "false"
}

foreach ($flag in $requiredRuntimeFlags.GetEnumerator()) {
    $pattern = "(?m)^\s*$([regex]::Escape($flag.Key)):\s+`"?$([regex]::Escape($flag.Value))`"?\s*$"
    if ($rendered -notmatch $pattern) {
        throw "Production overlay must set $($flag.Key) to $($flag.Value)"
    }
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

$namespaceDocument = @($documents | Where-Object {
        $_ -match "(?m)^kind:\s+Namespace\s*$" -and
        $_ -match "(?m)^\s+name:\s+legent\s*$"
    })

if ($namespaceDocument.Count -ne 1) {
    throw "Production overlay must render exactly one legent Namespace"
}

foreach ($label in @("enforce", "audit", "warn")) {
    if ($namespaceDocument[0] -notmatch "(?m)^\s+pod-security\.kubernetes\.io/${label}:\s+restricted\s*$") {
        throw "Production Namespace must set pod-security.kubernetes.io/$label to restricted"
    }
}

$ingressDocuments = @($documents | Where-Object { $_ -match "(?m)^kind:\s+Ingress\s*$" })
$requiredTlsHosts = @("api.legent.com", "app.legent.com")
foreach ($tlsHost in $requiredTlsHosts) {
    $tlsCovered = @($ingressDocuments | Where-Object {
            $_ -match "(?ms)^\s*tls:\s*\r?\n(?:\s+.*\r?\n)*?\s+-\s+$([regex]::Escape($tlsHost))\s*$" -and
            $_ -match "(?m)^\s+secretName:\s+legent-public-tls\s*$"
        })
    if ($tlsCovered.Count -lt 1) {
        throw "Production ingress TLS must cover $tlsHost with secret legent-public-tls"
    }
}

$deploymentDocuments = @($documents | Where-Object { $_ -match "(?m)^kind:\s+Deployment\s*$" })
if ($deploymentDocuments.Count -eq 0) {
    throw "Production overlay must render application Deployments"
}

foreach ($deployment in $deploymentDocuments) {
    $deploymentName = if ($deployment -match "(?m)^\s+name:\s+([A-Za-z0-9_.-]+)\s*$") { $Matches[1] } else { "<unknown>" }
    if ($deployment -notmatch "(?m)^\s+runAsNonRoot:\s+true\s*$") {
        throw "Deployment $deploymentName must set pod securityContext.runAsNonRoot=true"
    }
    if ($deployment -notmatch "(?m)^\s+seccompProfile:\s*$" -or $deployment -notmatch "(?m)^\s+type:\s+RuntimeDefault\s*$") {
        throw "Deployment $deploymentName must set seccompProfile RuntimeDefault"
    }
    if ($deployment -notmatch "(?m)^\s+allowPrivilegeEscalation:\s+false\s*$") {
        throw "Deployment $deploymentName must set container securityContext.allowPrivilegeEscalation=false"
    }
    if ($deployment -notmatch "(?ms)^\s+capabilities:\s*\r?\n\s+drop:\s*\r?\n\s+-\s+ALL\s*$") {
        throw "Deployment $deploymentName must drop all Linux capabilities"
    }
}

$requiredAlerts = @(
    "LegentServiceHighErrorRate",
    "LegentKafkaConsumerLagHigh",
    "LegentPodRestarting",
    "LegentExecutorSaturationCritical",
    "LegentOtlpCollectorDown"
)

$alertRulesConfigMap = @($documents | Where-Object {
        $_ -match "(?m)^kind:\s+ConfigMap\s*$" -and
        $_ -match "(?m)^\s+name:\s+prometheus-legent-alert-rules\s*$" -and
        $_ -match "(?m)^\s+prometheus-alerts\.yml:\s+\|"
    })

if ($alertRulesConfigMap.Count -ne 1) {
    throw "Production overlay must render prometheus-legent-alert-rules ConfigMap from observability/prometheus-alerts.yml"
}

foreach ($alert in $requiredAlerts) {
    if ($rendered -notmatch "(?m)^\s*-\s*alert:\s+$([regex]::Escape($alert))\s*$") {
        throw "Production overlay must render required observability alert $alert"
    }
}

$egressFindings = @()
$hasProductionDefaultDeny = $false
$hasProductionInternalEgress = $false
foreach ($document in $documents) {
    if ($document -notmatch "(?m)^kind:\s+NetworkPolicy\s*$") {
        continue
    }

    if ($document -match "(?m)^\s+name:\s+production-default-deny\s*$") {
        $hasProductionDefaultDeny = $true
    }

    if ($document -match "(?m)^\s+name:\s+production-internal-egress\s*$") {
        $hasProductionInternalEgress = $true
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

if (-not $hasProductionDefaultDeny) {
    throw "Production overlay must render production-default-deny NetworkPolicy"
}

if (-not $hasProductionInternalEgress) {
    throw "Production overlay must render production-internal-egress for same-namespace and DNS egress"
}

Write-Host "Production overlay validation passed"

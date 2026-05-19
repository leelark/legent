param(
    [string]$OutputPath = "docs/operations/ga-evidence-manifest.template.json"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$dir = Split-Path -Parent $OutputPath
if ($dir) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }

$manifest = [ordered]@{
    schemaVersion = 1
    syntheticSmoke = "replace-with-synthetic-smoke-transcript"
    liveLoad = "replace-with-live-load-report"
    restoreDrill = "replace-with-restore-drill-report"
    ciSecurityTranscript = "replace-with-ci-security-transcript"
    filesystemSbom = "replace-with-filesystem-sbom"
    monitoringHandoff = "replace-with-monitoring-handoff"
    tlsCertificate = "replace-with-tls-certificate-evidence"
    restrictedAdmission = "replace-with-restricted-admission-evidence"
    registryImageEvidence = "replace-with-image-evidence-manifest"
}

$manifest | ConvertTo-Json -Depth 4 | Set-Content -Path $OutputPath -Encoding UTF8
Write-Host "Wrote GA evidence manifest template to $OutputPath"

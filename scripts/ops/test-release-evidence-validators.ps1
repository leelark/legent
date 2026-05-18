param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
)

$ErrorActionPreference = "Stop"

function New-TempDirectory {
    $path = Join-Path ([System.IO.Path]::GetTempPath()) ("legent-validator-selftest-" + [guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Path $path -Force | Out-Null
    return $path
}

function Write-TextFile {
    param(
        [Parameter(Mandatory = $true)] [string] $Path,
        [Parameter(Mandatory = $true)] [string] $Content
    )

    $directory = Split-Path -Parent $Path
    if (-not [string]::IsNullOrWhiteSpace($directory)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }
    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

function Write-FakeKubectl {
    param(
        [Parameter(Mandatory = $true)] [string] $Directory,
        [Parameter(Mandatory = $true)] [string] $RenderedYaml
    )

    $escaped = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($RenderedYaml))
    $ps1Path = Join-Path $Directory "kubectl.ps1"
    Write-TextFile -Path $ps1Path -Content @"
if (`$args.Count -ge 1 -and `$args[0] -eq "kustomize") {
    Write-Output ([System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String("$escaped")))
    exit 0
}
Write-Error "fake kubectl only supports kustomize"
exit 1
"@

    $cmdPath = Join-Path $Directory "kubectl.cmd"
    Write-TextFile -Path $cmdPath -Content "@echo off`r`npwsh -NoProfile -ExecutionPolicy Bypass -File ""%~dp0kubectl.ps1"" %*`r`n"

    $shPath = Join-Path $Directory "kubectl"
    Write-TextFile -Path $shPath -Content "#!/usr/bin/env sh`npwsh -NoProfile -ExecutionPolicy Bypass -File ""`$(dirname ""`$0"")/kubectl.ps1"" ""`$@""`n"
    if ($PSVersionTable.PSVersion.Major -ge 6 -and $PSVersionTable.Platform -eq "Unix") {
        & chmod +x $shPath
    }
}

function Invoke-ExpectFailure {
    param(
        [Parameter(Mandatory = $true)] [string] $Name,
        [Parameter(Mandatory = $true)] [scriptblock] $Command,
        [Parameter(Mandatory = $true)] [string] $ExpectedPattern
    )

    try {
        & $Command 2>&1 | Out-String | Out-Null
    } catch {
        $message = $_.Exception.Message
        if ($message -notmatch $ExpectedPattern) {
            throw "$Name failed closed with an unexpected message: $message"
        }
        Write-Host "PASS: $Name"
        return
    }

    throw "$Name did not fail closed"
}

$tempRoot = New-TempDirectory
$previousPath = $env:PATH

try {
    $fakeBin = Join-Path $tempRoot "bin"
    New-Item -ItemType Directory -Path $fakeBin -Force | Out-Null
    $env:PATH = "$fakeBin$([System.IO.Path]::PathSeparator)$previousPath"

    $overlayValidator = Join-Path $RepoRoot "scripts\ops\validate-production-overlay.ps1"
    $egressValidator = Join-Path $RepoRoot "scripts\ops\validate-production-egress-evidence.ps1"
    $gaValidator = Join-Path $RepoRoot "scripts\ops\validate-ga-evidence.ps1"
    $loadHarness = Join-Path $RepoRoot "scripts\load\phase3-high-volume-load.ps1"

    Write-FakeKubectl -Directory $fakeBin -RenderedYaml @"
apiVersion: apps/v1
kind: Deployment
metadata:
  name: identity-service
spec:
  template:
    spec:
      containers:
      - name: identity-service
        image: legent/identity-service:1.0.2
"@
    Invoke-ExpectFailure "strict image digest requirement rejects tag-only images" {
        & $overlayValidator -OverlayPath "unused" -RequireImageDigests
    } "not digest-pinned|digest evidence"

    Write-FakeKubectl -Directory $fakeBin -RenderedYaml @"
apiVersion: apps/v1
kind: Deployment
metadata:
  name: identity-service
spec:
  template:
    spec:
      containers:
      - name: identity-service
        image: legent/identity-service@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
"@
    Invoke-ExpectFailure "image evidence mode requires manifest" {
        & $overlayValidator -OverlayPath "unused" -RequireImageEvidence
    } "image evidence manifest is required|ImageEvidenceManifest"

    $egressSpec = Join-Path $tempRoot "production-egress-placeholder.json"
    Write-TextFile -Path $egressSpec -Content @'
{
  "schemaVersion": "legent.production-egress.v1",
  "review": {
    "owner": "TBD",
    "reviewDate": "2099-01-01",
    "changeTicket": "<ticket>"
  },
  "dependencies": [
    {
      "name": "example",
      "owner": "TBD",
      "reviewDate": "2099-01-01",
      "purpose": "TODO",
      "evidence": "placeholder",
      "scope": "external",
      "ports": [{ "protocol": "TCP", "port": 443 }],
      "destinations": [{ "type": "fqdn", "value": "api.example.com" }]
    }
  ],
  "fqdnPolicy": {
    "approved": false,
    "cni": "TBD",
    "policyReference": "TODO",
    "note": "placeholder"
  }
}
'@
    Invoke-ExpectFailure "external egress evidence rejects placeholders/template values" {
        & $egressValidator -SpecPath $egressSpec
    } "placeholder|required|future|approved"

    Invoke-ExpectFailure "live load RequireLive rejects missing token/input" {
        & $loadHarness -RequireLive -Imports 0 -Segments 0 -Sends 0 -TrackingEvents 0 -Reports 0 -Token "" -DataProfileName "synthetic"
    } "requires -Token|requires -DataProfileName"

    Invoke-ExpectFailure "live load RequireLive rejects missing live evidence" {
        & $loadHarness -RequireLive -Imports 0 -Segments 0 -Sends 0 -TrackingEvents 0 -Reports 0 -Token "self-test-token" -DataProfileName "enterprise-self-test"
    } "Live evidence validation failed|LiveEvidencePath"

    $emptyEvidenceDir = Join-Path $tempRoot "missing-ga-manifest"
    New-Item -ItemType Directory -Path $emptyEvidenceDir -Force | Out-Null
    Invoke-ExpectFailure "GA evidence rejects missing manifest" {
        & $gaValidator -EvidenceDir $emptyEvidenceDir
    } "GA evidence manifest not found"

    $invalidManifest = Join-Path $tempRoot "invalid-ga-manifest.json"
    Write-TextFile -Path $invalidManifest -Content '{"evidence":{"synthetic-smoke":{"status":"TODO","path":"missing.txt"}}}'
    Invoke-ExpectFailure "GA evidence rejects invalid manifest" {
        & $gaValidator -ManifestPath $invalidManifest
    } "placeholder|missing required artifact|missing evidence file"

    Write-Host "Release evidence validator self-test passed"
} finally {
    $env:PATH = $previousPath
    Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
}

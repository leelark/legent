param(
    [Parameter(Mandatory = $true)][string]$EvidencePath,
    [string]$OverlayPath = "infrastructure/kubernetes/overlays/production",
    [string]$GeneratedPolicyPath,
    [switch]$UseExistingGeneratedPolicy
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail($Message) {
    Write-Error $Message
    exit 1
}

function Copy-KubernetesTree([string]$TempRoot) {
    $sourceRoot = "infrastructure/kubernetes"
    if (-not (Test-Path $sourceRoot)) { Fail "Missing Kubernetes manifests root: $sourceRoot" }
    $destinationRoot = Join-Path $TempRoot $sourceRoot
    New-Item -ItemType Directory -Force -Path $destinationRoot | Out-Null
    Copy-Item -Path (Join-Path $sourceRoot "*") -Destination $destinationRoot -Recurse -Force
}

function Add-GeneratedResource([string]$KustomizationPath, [string]$ResourceName) {
    $content = Get-Content -Path $KustomizationPath -Raw
    if ($content -match "(?m)^\s*-\s+$([regex]::Escape($ResourceName))\s*$") {
        return
    }
    $lines = New-Object System.Collections.Generic.List[string]
    $inserted = $false
    foreach ($line in (Get-Content -Path $KustomizationPath)) {
        $lines.Add($line)
        if (-not $inserted -and $line -match '^\s*-\s+network-policy\.yml\s*$') {
            $lines.Add("  - $ResourceName")
            $inserted = $true
        }
    }
    if (-not $inserted) {
        Fail "Production kustomization must list network-policy.yml before reviewed egress policy can be appended."
    }
    Set-Content -Path $KustomizationPath -Value $lines -Encoding UTF8
}

function Assert-RenderedContains([string]$Rendered, [string]$Needle, [string]$Message) {
    if ($Rendered -notmatch [regex]::Escape($Needle)) {
        Fail $Message
    }
}

function Assert-EvidenceHash([string]$Rendered, [string]$Hash, [string]$Message) {
    $pattern = 'legent\.com/egress-evidence-sha256:\s*"?' + [regex]::Escape($Hash) + '"?'
    if ($Rendered -notmatch $pattern) {
        Fail $Message
    }
}

function Assert-AppExistsSelector([string]$PolicyYaml) {
    foreach ($fragment in @("podSelector:", "matchExpressions:", "- key: app", "operator: Exists")) {
        if ($PolicyYaml -notmatch [regex]::Escape($fragment)) {
            Fail "Reviewed egress policy must use the production app label selector."
        }
    }
}

function Assert-DeploymentPodsCoveredByAppSelector([string]$Rendered) {
    $documents = [regex]::Split($Rendered, "(?m)^---\s*$")
    $deploymentCount = 0
    $missing = New-Object System.Collections.Generic.List[string]

    foreach ($document in $documents) {
        if ($document -notmatch "(?m)^kind:\s*Deployment\s*$") {
            continue
        }

        $deploymentCount++
        $lines = @($document -split "`r?`n")
        $name = "(unknown)"
        for ($i = 0; $i -lt $lines.Count; $i++) {
            if ($lines[$i] -match "^metadata:\s*$") {
                for ($j = $i + 1; $j -lt $lines.Count; $j++) {
                    if ($lines[$j] -match "^\S") { break }
                    if ($lines[$j] -match "^\s{2}name:\s*(.+?)\s*$") {
                        $name = $Matches[1]
                        break
                    }
                }
                break
            }
        }

        $hasAppLabel = $false
        for ($i = 0; $i -lt $lines.Count; $i++) {
            if ($lines[$i] -notmatch "^(\s*)template:\s*$") {
                continue
            }
            $templateIndent = $Matches[1].Length
            for ($j = $i + 1; $j -lt $lines.Count; $j++) {
                if ($lines[$j] -match "^\s*\S") {
                    $currentIndent = ($Matches[0].Length - ($Matches[0].TrimStart()).Length)
                    if ($currentIndent -le $templateIndent) {
                        break
                    }
                }
                if ($lines[$j] -notmatch "^(\s*)labels:\s*$") {
                    continue
                }
                $labelsIndent = $Matches[1].Length
                if ($labelsIndent -le $templateIndent) {
                    continue
                }
                for ($k = $j + 1; $k -lt $lines.Count; $k++) {
                    if ($lines[$k] -match "^\s*\S") {
                        $currentIndent = ($Matches[0].Length - ($Matches[0].TrimStart()).Length)
                        if ($currentIndent -le $labelsIndent) {
                            break
                        }
                    }
                    if ($lines[$k] -match "^\s*app:\s*\S+\s*$") {
                        $hasAppLabel = $true
                        break
                    }
                }
                if ($hasAppLabel) {
                    break
                }
            }
            if ($hasAppLabel) { break }
        }

        if (-not $hasAppLabel) {
            $missing.Add($name)
        }
    }

    if ($deploymentCount -eq 0) {
        Fail "Rendered production overlay does not contain any Deployment resources."
    }
    if ($missing.Count -gt 0) {
        Fail "Reviewed egress app selector does not cover Deployment pod templates missing app labels: $($missing -join ', ')"
    }
}

& scripts/ops/validate-production-egress-evidence.ps1 -EvidencePath $EvidencePath
if (-not $?) { exit 1 }

$evidence = Get-Content -Path $EvidencePath -Raw | ConvertFrom-Json
$evidenceHash = (Get-FileHash -Path $EvidencePath -Algorithm SHA256).Hash.ToLowerInvariant()
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("legent-egress-render-" + [System.Guid]::NewGuid().ToString("N"))
$resourceName = "reviewed-external-egress.generated.yml"

try {
    Copy-KubernetesTree $tempRoot
    $tempOverlay = Join-Path $tempRoot $OverlayPath
    if (-not (Test-Path $tempOverlay)) { Fail "Missing copied production overlay path: $tempOverlay" }
    $tempGenerated = Join-Path $tempOverlay $resourceName

    if ($UseExistingGeneratedPolicy) {
        if (-not $GeneratedPolicyPath) {
            Fail "GeneratedPolicyPath is required when UseExistingGeneratedPolicy is set."
        }
        if (-not (Test-Path $GeneratedPolicyPath)) {
            Fail "Missing generated reviewed egress policy: $GeneratedPolicyPath"
        }
        Copy-Item -LiteralPath $GeneratedPolicyPath -Destination $tempGenerated -Force
    } else {
        & scripts/ops/write-production-egress-policy.ps1 -EvidencePath $EvidencePath -OutputPath $tempGenerated
        if (-not $?) { exit 1 }
    }

    $generated = Get-Content -Path $tempGenerated -Raw
    Assert-EvidenceHash $generated $evidenceHash "Generated egress policy hash does not match supplied evidence."
    Assert-RenderedContains $generated "name: reviewed-external-egress" "Generated reviewed egress NetworkPolicy is missing."
    Assert-AppExistsSelector $generated

    Add-GeneratedResource (Join-Path $tempOverlay "kustomization.yml") $resourceName
    $rendered = (kubectl kustomize $tempOverlay) -join "`n"
    if ($LASTEXITCODE -ne 0) { Fail "Kustomize render failed for reviewed egress policy proof." }

    Assert-RenderedContains $rendered "kind: NetworkPolicy" "Rendered output is missing NetworkPolicy resources."
    Assert-RenderedContains $rendered "name: reviewed-external-egress" "Rendered output is missing reviewed-external-egress NetworkPolicy."
    Assert-EvidenceHash $rendered $evidenceHash "Rendered reviewed egress policy hash does not match supplied evidence."
    Assert-DeploymentPodsCoveredByAppSelector $rendered

    foreach ($rule in @($evidence.egressRules)) {
        $cidrs = @()
        if ($rule.PSObject.Properties.Name -contains "cidrs") { $cidrs = @($rule.cidrs) }
        foreach ($cidr in $cidrs) {
            Assert-RenderedContains $rendered "cidr: $cidr" "Rendered reviewed egress policy missing CIDR $cidr."
        }
        foreach ($port in @($rule.ports)) {
            $protocol = if ($port.protocol) { [string]$port.protocol } else { "TCP" }
            Assert-RenderedContains $rendered "protocol: $protocol" "Rendered reviewed egress policy missing protocol $protocol."
            Assert-RenderedContains $rendered "port: $($port.port)" "Rendered reviewed egress policy missing port $($port.port)."
        }
    }
} finally {
    if (Test-Path $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}

Write-Host "Production reviewed egress policy render validation passed."

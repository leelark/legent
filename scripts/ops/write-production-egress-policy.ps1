param(
    [Parameter(Mandatory = $true)][string]$EvidencePath,
    [string]$OutputPath = "infrastructure/kubernetes/overlays/production/reviewed-external-egress.generated.yml"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail($Message) {
    Write-Error $Message
    exit 1
}

function Quote-YamlString([string]$Value) {
    $escaped = ($Value -replace "\\", "\\") -replace '"', '\"'
    return '"' + $escaped + '"'
}

& scripts/ops/validate-production-egress-evidence.ps1 -EvidencePath $EvidencePath
if (-not $?) { exit 1 }

$evidence = Get-Content -Path $EvidencePath -Raw | ConvertFrom-Json
$evidenceHash = (Get-FileHash -Path $EvidencePath -Algorithm SHA256).Hash.ToLowerInvariant()
$dir = Split-Path -Parent $OutputPath
if ($dir) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("apiVersion: networking.k8s.io/v1")
$lines.Add("kind: NetworkPolicy")
$lines.Add("metadata:")
$lines.Add("  name: reviewed-external-egress")
$lines.Add("  namespace: legent")
$lines.Add("  annotations:")
$lines.Add("    legent.com/egress-evidence-sha256: $(Quote-YamlString $evidenceHash)")
$lines.Add("    legent.com/egress-evidence-reviewed-at: $(Quote-YamlString $evidence.reviewedAt)")
$lines.Add("    legent.com/egress-evidence-reviewed-by: $(Quote-YamlString $evidence.reviewedBy)")
$lines.Add("spec:")
$lines.Add("  podSelector:")
$lines.Add("    matchExpressions:")
$lines.Add("      - key: app")
$lines.Add("        operator: Exists")
$lines.Add("  policyTypes:")
$lines.Add("    - Egress")
$lines.Add("  egress:")
foreach ($rule in @($evidence.egressRules)) {
    $cidrs = @()
    $fqdns = @()
    if ($rule.PSObject.Properties.Name -contains "cidrs") { $cidrs = @($rule.cidrs) }
    if ($rule.PSObject.Properties.Name -contains "fqdns") { $fqdns = @($rule.fqdns) }
    if ($fqdns.Count -gt 0) {
        Fail "Cannot render FQDN egress rule $($rule.name) as Kubernetes NetworkPolicy."
    }
    foreach ($cidr in $cidrs) {
        $lines.Add("    - to:")
        $lines.Add("        - ipBlock:")
        $lines.Add("            cidr: $cidr")
        $lines.Add("      ports:")
        foreach ($port in @($rule.ports)) {
            $protocol = if ($port.protocol) { $port.protocol } else { "TCP" }
            $lines.Add("        - protocol: $protocol")
            $lines.Add("          port: $($port.port)")
        }
    }
}

Set-Content -Path $OutputPath -Value $lines -Encoding UTF8
Write-Host "Wrote reviewed egress policy to $OutputPath"

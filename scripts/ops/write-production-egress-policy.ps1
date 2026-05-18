param(
    [string] $SpecPath = "docs/operations/production-egress-evidence.json",
    [string] $OutputDirectory = "docs/operations/production-egress-rendered",
    [string] $Namespace = "legent",
    [string] $PolicyName = "production-reviewed-external-egress",
    [switch] $IncludeInternalDependencies,
    [switch] $RequireConcreteFqdnPolicy
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$validatorPath = Join-Path $PSScriptRoot "validate-production-egress-evidence.ps1"

function Resolve-RepoPath {
    param([string] $Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }

    return [System.IO.Path]::GetFullPath((Join-Path $repoRoot $Path))
}

function Get-JsonProperty {
    param(
        [object] $Object,
        [string] $Name
    )

    if ($null -eq $Object) {
        return $null
    }

    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }

    return $property.Value
}

function ConvertTo-SafeName {
    param([string] $Value)

    $safe = ($Value.ToLowerInvariant() -replace "[^a-z0-9-]+", "-").Trim("-")
    if ([string]::IsNullOrWhiteSpace($safe)) {
        return "dependency"
    }

    if ($safe.Length -gt 48) {
        return $safe.Substring(0, 48).Trim("-")
    }

    return $safe
}

function Write-TextFile {
    param(
        [Parameter(Mandatory = $true)] [string] $Path,
        [Parameter(Mandatory = $true)] [string] $Content
    )

    $directory = Split-Path -Parent $Path
    if ($directory -and -not (Test-Path -LiteralPath $directory -PathType Container)) {
        New-Item -ItemType Directory -Path $directory | Out-Null
    }

    Set-Content -LiteralPath $Path -Value $Content -Encoding UTF8
}

function Get-DependencyPorts {
    param([object] $Dependency)

    return @((Get-JsonProperty $Dependency "ports") | ForEach-Object {
            [pscustomobject]@{
                Protocol = "$((Get-JsonProperty $_ "protocol"))".Trim().ToUpperInvariant()
                Port = [int] (Get-JsonProperty $_ "port")
            }
        })
}

function Get-DependencyDestinations {
    param(
        [object] $Dependency,
        [string] $Type
    )

    return @((Get-JsonProperty $Dependency "destinations") | Where-Object {
            "$((Get-JsonProperty $_ "type"))".Trim().ToLowerInvariant() -eq $Type
        } | ForEach-Object {
            "$((Get-JsonProperty $_ "value"))".Trim()
        })
}

function Add-NetworkPolicyRule {
    param(
        [System.Collections.Generic.List[string]] $Lines,
        [string[]] $Cidrs,
        [object[]] $Ports
    )

    if ($Cidrs.Count -eq 0) {
        return
    }

    $Lines.Add("    - to:")
    foreach ($cidr in $Cidrs) {
        $Lines.Add("        - ipBlock:")
        $Lines.Add("            cidr: $cidr")
    }

    if ($Ports.Count -gt 0) {
        $Lines.Add("      ports:")
        foreach ($port in $Ports) {
            $Lines.Add("        - protocol: $($port.Protocol)")
            $Lines.Add("          port: $($port.Port)")
        }
    }
}

function Add-CiliumFqdnRule {
    param(
        [System.Collections.Generic.List[string]] $Lines,
        [string[]] $Fqdns,
        [object[]] $Ports
    )

    if ($Fqdns.Count -eq 0) {
        return
    }

    $Lines.Add("  - toFQDNs:")
    foreach ($fqdn in $Fqdns) {
        $Lines.Add("      - matchName: $($fqdn.TrimEnd(".").ToLowerInvariant())")
    }

    if ($Ports.Count -gt 0) {
        $Lines.Add("    toPorts:")
        $Lines.Add("      - ports:")
        foreach ($port in $Ports) {
            $Lines.Add("          - port: ""$($port.Port)""")
            $Lines.Add("            protocol: $($port.Protocol)")
        }
    }
}

$resolvedSpecPath = Resolve-RepoPath $SpecPath
$resolvedOutputDirectory = Resolve-RepoPath $OutputDirectory

& $validatorPath -SpecPath $resolvedSpecPath

$spec = Get-Content -LiteralPath $resolvedSpecPath -Raw | ConvertFrom-Json
$dependencies = @((Get-JsonProperty $spec "dependencies") | Where-Object {
        $scope = "$((Get-JsonProperty $_ "scope"))".Trim().ToLowerInvariant()
        $scope -eq "external" -or ($IncludeInternalDependencies -and $scope -eq "internal")
    })

$cidrRules = New-Object System.Collections.Generic.List[object]
$fqdnRules = New-Object System.Collections.Generic.List[object]

foreach ($dependency in $dependencies) {
    $name = "$((Get-JsonProperty $dependency "name"))".Trim()
    $ports = @(Get-DependencyPorts $dependency)
    $cidrs = @(Get-DependencyDestinations $dependency "cidr")
    $fqdns = @(Get-DependencyDestinations $dependency "fqdn")

    if ($cidrs.Count -gt 0) {
        $cidrRules.Add([pscustomobject]@{
                Name = $name
                Cidrs = $cidrs
                Ports = $ports
            })
    }

    if ($fqdns.Count -gt 0) {
        $fqdnRules.Add([pscustomobject]@{
                Name = $name
                Fqdns = $fqdns
                Ports = $ports
            })
    }
}

$generatedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$review = Get-JsonProperty $spec "review"

if ($cidrRules.Count -gt 0) {
    $cidrLines = New-Object System.Collections.Generic.List[string]
    @(
        "apiVersion: networking.k8s.io/v1",
        "kind: NetworkPolicy",
        "metadata:",
        "  name: $PolicyName",
        "  namespace: $Namespace",
        "  labels:",
        "    app.kubernetes.io/part-of: legent",
        "    legent.com/policy-source: reviewed-egress-evidence",
        "  annotations:",
        "    legent.com/generated-at: ""$generatedAt""",
        "    legent.com/review-owner: ""$((Get-JsonProperty $review "owner"))""",
        "    legent.com/review-date: ""$((Get-JsonProperty $review "reviewDate"))""",
        "    legent.com/change-ticket: ""$((Get-JsonProperty $review "changeTicket"))""",
        "spec:",
        "  podSelector:",
        "    matchExpressions:",
        "      - key: app",
        "        operator: Exists",
        "  policyTypes:",
        "    - Egress",
        "  egress:"
    ) | ForEach-Object { $cidrLines.Add($_) }

    foreach ($rule in $cidrRules) {
        $cidrLines.Add("    # $($rule.Name)")
        Add-NetworkPolicyRule -Lines $cidrLines -Cidrs $rule.Cidrs -Ports $rule.Ports
    }

    $cidrOutputPath = Join-Path $resolvedOutputDirectory "production-egress-cidr-network-policy.yml"
    Write-TextFile -Path $cidrOutputPath -Content ($cidrLines -join "`n")
    Write-Host "Wrote reviewed CIDR NetworkPolicy artifact to $cidrOutputPath"
} else {
    Write-Host "No CIDR destinations found in reviewed production egress evidence."
}

if ($fqdnRules.Count -gt 0) {
    $fqdnPolicy = Get-JsonProperty $spec "fqdnPolicy"
    $cni = "$((Get-JsonProperty $fqdnPolicy "cni"))".Trim().ToLowerInvariant()

    if ($cni -eq "cilium") {
        $fqdnLines = New-Object System.Collections.Generic.List[string]
        @(
            "apiVersion: cilium.io/v2",
            "kind: CiliumNetworkPolicy",
            "metadata:",
            "  name: $PolicyName-fqdn",
            "  namespace: $Namespace",
            "  labels:",
            "    app.kubernetes.io/part-of: legent",
            "    legent.com/policy-source: reviewed-egress-evidence",
            "  annotations:",
            "    legent.com/generated-at: ""$generatedAt""",
            "    legent.com/review-owner: ""$((Get-JsonProperty $review "owner"))""",
            "    legent.com/review-date: ""$((Get-JsonProperty $review "reviewDate"))""",
            "    legent.com/change-ticket: ""$((Get-JsonProperty $review "changeTicket"))""",
            "spec:",
            "  endpointSelector:",
            "    matchExpressions:",
            "      - key: app",
            "        operator: Exists",
            "  egress:"
        ) | ForEach-Object { $fqdnLines.Add($_) }

        foreach ($rule in $fqdnRules) {
            $fqdnLines.Add("  # $($rule.Name)")
            Add-CiliumFqdnRule -Lines $fqdnLines -Fqdns $rule.Fqdns -Ports $rule.Ports
        }

        $fqdnOutputPath = Join-Path $resolvedOutputDirectory "production-egress-cilium-fqdn-policy.yml"
        Write-TextFile -Path $fqdnOutputPath -Content ($fqdnLines -join "`n")
        Write-Host "Wrote reviewed Cilium FQDN policy artifact to $fqdnOutputPath"
    } else {
        if ($RequireConcreteFqdnPolicy) {
            throw "FQDN destinations require a concrete CNI policy renderer, but fqdnPolicy.cni is '$cni'. Supported concrete renderer: cilium."
        }

        $reviewArtifact = [ordered]@{
            schemaVersion = "legent.production-egress-fqdn-review.v1"
            generatedAt = $generatedAt
            cni = Get-JsonProperty $fqdnPolicy "cni"
            policyReference = Get-JsonProperty $fqdnPolicy "policyReference"
            note = Get-JsonProperty $fqdnPolicy "note"
            dependencies = @($fqdnRules | ForEach-Object {
                    [ordered]@{
                        name = $_.Name
                        fqdns = $_.Fqdns
                        ports = @($_.Ports | ForEach-Object {
                                [ordered]@{
                                    protocol = $_.Protocol
                                    port = $_.Port
                                }
                            })
                    }
                })
        }

        $fqdnReviewPath = Join-Path $resolvedOutputDirectory "production-egress-fqdn-policy.review.json"
        Write-TextFile -Path $fqdnReviewPath -Content ($reviewArtifact | ConvertTo-Json -Depth 8)
        Write-Host "Wrote explicit FQDN review artifact to $fqdnReviewPath. Materialize this through the approved CNI policy referenced by the evidence."
    }
}

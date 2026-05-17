param(
    [string] $SpecPath = "docs/operations/production-egress-evidence.json"
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$resolvedSpecPath = if ([System.IO.Path]::IsPathRooted($SpecPath)) {
    $SpecPath
} else {
    Join-Path $repoRoot $SpecPath
}

if (-not (Test-Path -LiteralPath $resolvedSpecPath -PathType Leaf)) {
    throw "Production external egress evidence spec not found: $resolvedSpecPath. Copy docs/operations/production-egress-evidence.template.json, replace placeholders with reviewed target-environment evidence, then rerun with -SpecPath."
}

$validationErrors = [System.Collections.Generic.List[string]]::new()

function Add-ValidationError {
    param([string] $Message)

    $validationErrors.Add($Message)
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

function Test-PlaceholderValue {
    param([object] $Value)

    if ($null -eq $Value) {
        return $true
    }

    $text = "$Value".Trim()
    if ($text.Length -eq 0) {
        return $true
    }

    return (
        $text -match "[<>]" -or
        $text -match "\.\.\." -or
        $text -match "(?i)^(todo|tbd|n/a|na|none|null|unknown|changeme|change_me|replace_me|placeholder|sample|example|dummy)$" -or
        $text -match "(?i)(^|[-_])todo($|[-_])" -or
        $text -match "(?i)(^|[-_])tbd($|[-_])" -or
        $text -match "(?i)your[-_]" -or
        $text -match "(?i)example\.(com|net|org)$" -or
        $text -match "(?i)https?://example\.(com|net|org)\b"
    )
}

function Assert-RequiredText {
    param(
        [string] $Path,
        [object] $Value
    )

    if (Test-PlaceholderValue $Value) {
        Add-ValidationError "$Path is required and must not be empty or a placeholder"
        return $false
    }

    return $true
}

function Test-ReviewDate {
    param(
        [string] $Path,
        [object] $Value
    )

    if (-not (Assert-RequiredText $Path $Value)) {
        return
    }

    $text = "$Value".Trim()
    try {
        $parsed = [datetime]::ParseExact(
            $text,
            "yyyy-MM-dd",
            [System.Globalization.CultureInfo]::InvariantCulture,
            [System.Globalization.DateTimeStyles]::None
        )
    } catch {
        Add-ValidationError "$Path must use yyyy-MM-dd format"
        return
    }

    if ($parsed.Date -gt (Get-Date).Date) {
        Add-ValidationError "$Path must not be in the future"
    }
}

function ConvertTo-Ipv4Number {
    param([string] $Address)

    $parsedAddress = [System.Net.IPAddress]::Parse($Address)
    $bytes = $parsedAddress.GetAddressBytes()
    if ($bytes.Count -ne 4) {
        throw "IPv6 CIDRs are not accepted by this validator yet"
    }

    return [uint64](
        ([uint64] $bytes[0] * 16777216) +
        ([uint64] $bytes[1] * 65536) +
        ([uint64] $bytes[2] * 256) +
        [uint64] $bytes[3]
    )
}

function ConvertFrom-Cidr {
    param(
        [string] $Path,
        [string] $Cidr
    )

    $text = "$Cidr".Trim()
    if ($text -match ":") {
        throw "$Path uses IPv6 CIDR '$text'. Extend this validator before approving IPv6 external egress."
    }

    if ($text -notmatch "^([0-9]{1,3}(?:\.[0-9]{1,3}){3})/([0-9]{1,2})$") {
        throw "$Path must be an IPv4 CIDR, got '$text'"
    }

    $address = $Matches[1]
    $prefixLength = [int] $Matches[2]
    if ($prefixLength -lt 0 -or $prefixLength -gt 32) {
        throw "$Path has invalid IPv4 prefix length $prefixLength"
    }

    $ipNumber = ConvertTo-Ipv4Number $address
    $mask = if ($prefixLength -eq 0) {
        [uint64] 0
    } else {
        ([uint64] 4294967295 -shl (32 - $prefixLength)) -band [uint64] 4294967295
    }

    $networkStart = $ipNumber -band $mask
    $networkEnd = $networkStart + ([uint64] 4294967295 - $mask)

    return [pscustomobject]@{
        Cidr = $text
        PrefixLength = $prefixLength
        Start = $networkStart
        End = $networkEnd
    }
}

function Test-CidrOverlap {
    param(
        [object] $Left,
        [object] $Right
    )

    return ($Left.Start -le $Right.End -and $Right.Start -le $Left.End)
}

function Test-ExternalFqdn {
    param(
        [string] $Path,
        [object] $Value,
        [bool] $IsExternal
    )

    if (-not (Assert-RequiredText "$Path.value" $Value)) {
        return
    }

    $fqdn = "$Value".Trim().TrimEnd(".").ToLowerInvariant()
    if ($fqdn -match "[*/:\\\s]" -or $fqdn -notmatch "^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z0-9][a-z0-9-]{1,62}$") {
        Add-ValidationError "$Path.value must be an exact FQDN without wildcards, schemes, paths, or ports"
        return
    }

    if ($IsExternal -and ($fqdn -match "\.(local|internal|svc|cluster\.local)$")) {
        Add-ValidationError "$Path.value points to an internal-looking domain but dependency scope is external"
    }
}

$reservedCidrs = @(
    "0.0.0.0/8",
    "10.0.0.0/8",
    "100.64.0.0/10",
    "127.0.0.0/8",
    "169.254.0.0/16",
    "172.16.0.0/12",
    "192.0.0.0/24",
    "192.0.2.0/24",
    "192.88.99.0/24",
    "192.168.0.0/16",
    "198.18.0.0/15",
    "198.51.100.0/24",
    "203.0.113.0/24",
    "224.0.0.0/4",
    "240.0.0.0/4",
    "255.255.255.255/32"
) | ForEach-Object { ConvertFrom-Cidr "reserved CIDR list" $_ }

try {
    $spec = Get-Content -LiteralPath $resolvedSpecPath -Raw | ConvertFrom-Json
} catch {
    throw "Failed to parse production external egress evidence spec $resolvedSpecPath as JSON: $($_.Exception.Message)"
}

$schemaVersion = Get-JsonProperty $spec "schemaVersion"
if ($schemaVersion -ne "legent.production-egress.v1") {
    Add-ValidationError "schemaVersion must be legent.production-egress.v1"
}

$review = Get-JsonProperty $spec "review"
if ($null -eq $review) {
    Add-ValidationError "review is required"
} else {
    Assert-RequiredText "review.owner" (Get-JsonProperty $review "owner") | Out-Null
    Test-ReviewDate "review.reviewDate" (Get-JsonProperty $review "reviewDate")
    Assert-RequiredText "review.changeTicket" (Get-JsonProperty $review "changeTicket") | Out-Null
}

$dependenciesValue = Get-JsonProperty $spec "dependencies"
if ($null -eq $dependenciesValue) {
    Add-ValidationError "dependencies is required and must contain reviewed egress entries"
    $dependencies = @()
} else {
    $dependencies = @($dependenciesValue)
    if ($dependencies.Count -eq 0) {
        Add-ValidationError "dependencies must contain at least one reviewed egress entry"
    }
}

$fqdnDestinationsSeen = $false
$externalDependenciesSeen = $false

for ($dependencyIndex = 0; $dependencyIndex -lt $dependencies.Count; $dependencyIndex++) {
    $dependency = $dependencies[$dependencyIndex]
    $dependencyPath = "dependencies[$dependencyIndex]"
    Assert-RequiredText "$dependencyPath.name" (Get-JsonProperty $dependency "name") | Out-Null
    Assert-RequiredText "$dependencyPath.owner" (Get-JsonProperty $dependency "owner") | Out-Null
    Test-ReviewDate "$dependencyPath.reviewDate" (Get-JsonProperty $dependency "reviewDate")
    Assert-RequiredText "$dependencyPath.purpose" (Get-JsonProperty $dependency "purpose") | Out-Null
    Assert-RequiredText "$dependencyPath.evidence" (Get-JsonProperty $dependency "evidence") | Out-Null

    $scope = Get-JsonProperty $dependency "scope"
    if (Test-PlaceholderValue $scope) {
        Add-ValidationError "$dependencyPath.scope is required and must be external or internal"
        $isExternal = $true
    } else {
        $scopeText = "$scope".Trim().ToLowerInvariant()
        if ($scopeText -notin @("external", "internal")) {
            Add-ValidationError "$dependencyPath.scope must be external or internal"
        }
        $isExternal = ($scopeText -eq "external")
        if ($isExternal) {
            $externalDependenciesSeen = $true
        }
    }

    $portsValue = Get-JsonProperty $dependency "ports"
    if ($null -eq $portsValue) {
        Add-ValidationError "$dependencyPath.ports is required"
        $ports = @()
    } else {
        $ports = @($portsValue)
        if ($ports.Count -eq 0) {
            Add-ValidationError "$dependencyPath.ports must include at least one protocol/port entry"
        }
    }

    for ($portIndex = 0; $portIndex -lt $ports.Count; $portIndex++) {
        $portEntry = $ports[$portIndex]
        $portPath = "$dependencyPath.ports[$portIndex]"
        $protocol = Get-JsonProperty $portEntry "protocol"
        $port = Get-JsonProperty $portEntry "port"

        if (Test-PlaceholderValue $protocol) {
            Add-ValidationError "$portPath.protocol is required"
        } else {
            $protocolText = "$protocol".Trim().ToUpperInvariant()
            if ($protocolText -notin @("TCP", "UDP", "SCTP")) {
                Add-ValidationError "$portPath.protocol must be TCP, UDP, or SCTP"
            }
        }

        if ($null -eq $port -or "$port".Trim() -notmatch "^[0-9]+$") {
            Add-ValidationError "$portPath.port is required and must be an integer"
        } else {
            $portNumber = [int] $port
            if ($portNumber -lt 1 -or $portNumber -gt 65535) {
                Add-ValidationError "$portPath.port must be between 1 and 65535"
            }
        }
    }

    $destinationsValue = Get-JsonProperty $dependency "destinations"
    if ($null -eq $destinationsValue) {
        Add-ValidationError "$dependencyPath.destinations is required"
        $destinations = @()
    } else {
        $destinations = @($destinationsValue)
        if ($destinations.Count -eq 0) {
            Add-ValidationError "$dependencyPath.destinations must include at least one CIDR or FQDN"
        }
    }

    for ($destinationIndex = 0; $destinationIndex -lt $destinations.Count; $destinationIndex++) {
        $destination = $destinations[$destinationIndex]
        $destinationPath = "$dependencyPath.destinations[$destinationIndex]"
        $destinationType = Get-JsonProperty $destination "type"
        $destinationValue = Get-JsonProperty $destination "value"

        if (Test-PlaceholderValue $destinationType) {
            Add-ValidationError "$destinationPath.type is required and must be cidr or fqdn"
            continue
        }

        $destinationTypeText = "$destinationType".Trim().ToLowerInvariant()
        if ($destinationTypeText -notin @("cidr", "fqdn")) {
            Add-ValidationError "$destinationPath.type must be cidr or fqdn"
            continue
        }

        if ($destinationTypeText -eq "fqdn") {
            $fqdnDestinationsSeen = $true
            Test-ExternalFqdn $destinationPath $destinationValue $isExternal
            continue
        }

        if (-not (Assert-RequiredText "$destinationPath.value" $destinationValue)) {
            continue
        }

        try {
            $parsedCidr = ConvertFrom-Cidr "$destinationPath.value" "$destinationValue"
        } catch {
            Add-ValidationError $_.Exception.Message
            continue
        }

        if ($parsedCidr.PrefixLength -eq 0 -and $parsedCidr.Start -eq 0) {
            Add-ValidationError "$destinationPath.value must not be broad 0.0.0.0/0"
            continue
        }

        if ($isExternal) {
            foreach ($reservedCidr in $reservedCidrs) {
                if (Test-CidrOverlap $parsedCidr $reservedCidr) {
                    Add-ValidationError "$destinationPath.value '$($parsedCidr.Cidr)' overlaps private/reserved CIDR $($reservedCidr.Cidr); mark the dependency scope internal only when this is a reviewed private endpoint"
                    break
                }
            }
        }
    }
}

if (-not $externalDependenciesSeen) {
    Add-ValidationError "dependencies must include at least one entry with scope external when external egress evidence is required"
}

if ($fqdnDestinationsSeen) {
    $fqdnPolicy = Get-JsonProperty $spec "fqdnPolicy"
    if ($null -eq $fqdnPolicy) {
        Add-ValidationError "fqdnPolicy is required when any destination.type is fqdn"
    } else {
        $approved = Get-JsonProperty $fqdnPolicy "approved"
        if ($approved -ne $true) {
            Add-ValidationError "fqdnPolicy.approved must be true when any destination.type is fqdn"
        }
        Assert-RequiredText "fqdnPolicy.cni" (Get-JsonProperty $fqdnPolicy "cni") | Out-Null
        Assert-RequiredText "fqdnPolicy.policyReference" (Get-JsonProperty $fqdnPolicy "policyReference") | Out-Null
        Assert-RequiredText "fqdnPolicy.note" (Get-JsonProperty $fqdnPolicy "note") | Out-Null
    }
}

if ($validationErrors.Count -gt 0) {
    throw "Production external egress evidence validation failed for $resolvedSpecPath`n - $($validationErrors -join "`n - ")"
}

Write-Host "Production external egress evidence validation passed: $resolvedSpecPath"

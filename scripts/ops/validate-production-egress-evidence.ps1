param(
    [Parameter(Mandatory = $true)][string]$EvidencePath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail($Message) {
    Write-Error $Message
    exit 1
}

if (-not (Test-Path $EvidencePath)) { Fail "Missing production egress evidence: $EvidencePath" }
$evidenceFileName = [System.IO.Path]::GetFileName($EvidencePath)
if ($evidenceFileName -match "(?i)(^|[._-])(template|example|sample)([._-]|$)") {
    Fail "Production egress evidence must be a reviewed environment-specific file, not a template/example file: $EvidencePath"
}
$evidence = Get-Content -Path $EvidencePath -Raw | ConvertFrom-Json
if ([int]$evidence.schemaVersion -ne 1) {
    Fail "Egress evidence schemaVersion must be 1."
}

function Test-ReviewedText([string]$Value, [string]$FieldName) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        Fail "Egress evidence requires $FieldName."
    }
    $trimmed = $Value.Trim()
    if ($trimmed -match "(?i)(replace[-_ ]?with|placeholder|TODO|TBD|TBA|sample|dummy|changeme|your[-_ ]|example|unknown|n/a|to[-_ ]?be[-_ ]?determined|<[^>]+>)") {
        Fail "Egress evidence $FieldName must be real reviewed evidence, not a placeholder: $Value"
    }
    if ($trimmed -match "(?i)^none$|^na$|(^|[\s._-])test([\s._-]|$)") {
        Fail "Egress evidence $FieldName must be real reviewed evidence, not a placeholder: $Value"
    }
}

function Get-IPv4Number([System.Net.IPAddress]$Ip) {
    $bytes = $Ip.GetAddressBytes()
    if ([BitConverter]::IsLittleEndian) { [array]::Reverse($bytes) }
    return [BitConverter]::ToUInt32($bytes, 0)
}

function Test-IPv4Range([System.Net.IPAddress]$Ip, [string]$Start, [string]$End) {
    $address = Get-IPv4Number $Ip
    $startNumber = Get-IPv4Number ([System.Net.IPAddress]::Parse($Start))
    $endNumber = Get-IPv4Number ([System.Net.IPAddress]::Parse($End))
    return $address -ge $startNumber -and $address -le $endNumber
}

function Test-ReservedCidr([System.Net.IPAddress]$Ip, [string]$Value, [string]$RuleName) {
    if ($Ip.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetwork) {
        $reservedRanges = @(
            @{ Name = "current network"; Start = "0.0.0.0"; End = "0.255.255.255" },
            @{ Name = "loopback"; Start = "127.0.0.0"; End = "127.255.255.255" },
            @{ Name = "link-local"; Start = "169.254.0.0"; End = "169.254.255.255" },
            @{ Name = "IETF protocol assignment"; Start = "192.0.0.0"; End = "192.0.0.255" },
            @{ Name = "RFC 5737 TEST-NET-1"; Start = "192.0.2.0"; End = "192.0.2.255" },
            @{ Name = "benchmarking"; Start = "198.18.0.0"; End = "198.19.255.255" },
            @{ Name = "RFC 5737 TEST-NET-2"; Start = "198.51.100.0"; End = "198.51.100.255" },
            @{ Name = "RFC 5737 TEST-NET-3"; Start = "203.0.113.0"; End = "203.0.113.255" },
            @{ Name = "multicast"; Start = "224.0.0.0"; End = "239.255.255.255" },
            @{ Name = "reserved"; Start = "240.0.0.0"; End = "255.255.255.255" }
        )
        foreach ($range in $reservedRanges) {
            if (Test-IPv4Range $Ip $range.Start $range.End) {
                Fail "Reserved or documentation CIDR $Value ($($range.Name)) is forbidden in rule $RuleName."
            }
        }
    } else {
        $canonical = $Ip.ToString().ToLowerInvariant()
        if ($canonical -eq "::" -or
            $canonical -eq "::1" -or
            $canonical -match "^2001:db8(:|$)" -or
            $canonical -match "^fe[89ab][0-9a-f]:" -or
            $canonical -match "^ff[0-9a-f]{2}:") {
            Fail "Reserved or documentation IPv6 CIDR $Value is forbidden in rule $RuleName."
        }
    }
}

function Test-CanonicalIPv4Cidr([System.Net.IPAddress]$Ip, [int]$Prefix, [string]$Value, [string]$RuleName) {
    if ($Ip.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork) { return }
    $address = Get-IPv4Number $Ip
    $blockSize = [math]::Pow(2, 32 - $Prefix)
    if (($address % $blockSize) -ne 0) {
        Fail "IPv4 CIDR must use the network base address in rule ${RuleName}: $Value"
    }
}

function Test-Cidr([string]$Value, [string]$RuleName) {
    Test-ReviewedText $Value "CIDR in rule $RuleName"
    $parts = $Value.Split("/")
    if ($parts.Count -ne 2) { Fail "Invalid CIDR in rule ${RuleName}: $Value" }
    if ($parts[0] -notmatch ":" -and $parts[0] -notmatch '^((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9][0-9]?|0)\.){3}(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9][0-9]?|0)$') {
        Fail "Invalid or non-canonical IPv4 CIDR in rule ${RuleName}: $Value"
    }
    $ip = $null
    if (-not [System.Net.IPAddress]::TryParse($parts[0], [ref]$ip)) {
        Fail "Invalid CIDR IP in rule ${RuleName}: $Value"
    }
    $prefix = 0
    if (-not [int]::TryParse($parts[1], [ref]$prefix)) {
        Fail "Invalid CIDR prefix in rule ${RuleName}: $Value"
    }
    $maxPrefix = if ($ip.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetwork) { 32 } else { 128 }
    if ($prefix -lt 1 -or $prefix -gt $maxPrefix) {
        Fail "CIDR prefix must be 1..$maxPrefix in rule ${RuleName}: $Value"
    }
    if ($ip.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetwork -and $prefix -lt 16) {
        Fail "Over-broad IPv4 CIDR prefix is forbidden in rule ${RuleName}: $Value"
    }
    if ($ip.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetworkV6 -and $prefix -lt 32) {
        Fail "Over-broad IPv6 CIDR prefix is forbidden in rule ${RuleName}: $Value"
    }
    Test-CanonicalIPv4Cidr $ip $prefix $Value $RuleName
    Test-ReservedCidr $ip $Value $RuleName
}

function Test-Fqdn([string]$Value, [string]$RuleName) {
    Test-ReviewedText $Value "FQDN in rule $RuleName"
    if ($Value -notmatch '^(?=.{1,253}$)([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[A-Za-z]{2,63}$') {
        Fail "Invalid FQDN in rule ${RuleName}: $Value"
    }
}

Test-ReviewedText ([string]$evidence.reviewedBy) "reviewedBy"
Test-ReviewedText ([string]$evidence.reviewedAt) "reviewedAt"
if ([string]$evidence.reviewedAt -match "YYYY") {
    Fail "Egress evidence reviewedAt must not be a placeholder date."
}
$reviewedAtDate = [datetime]::MinValue
if (-not [datetime]::TryParse([string]$evidence.reviewedAt, [ref]$reviewedAtDate)) {
    Fail "Egress evidence reviewedAt must be a parseable review date."
}
if ($reviewedAtDate.Date -gt (Get-Date).Date) {
    Fail "Egress evidence reviewedAt must not be in the future."
}

$rules = @($evidence.egressRules)
if ($rules.Count -eq 0) { Fail "Egress evidence has no egressRules." }

foreach ($rule in $rules) {
    Test-ReviewedText ([string]$rule.name) "rule name"
    foreach ($field in @("purpose", "provider")) {
        $value = [string]$rule.$field
        Test-ReviewedText $value "$field in rule $($rule.name)"
    }
    if (-not $rule.ports) { Fail "Egress rule $($rule.name) missing ports." }
    $cidrs = @()
    $fqdns = @()
    if ($rule.PSObject.Properties.Name -contains "cidrs") { $cidrs = @($rule.cidrs) }
    if ($rule.PSObject.Properties.Name -contains "fqdns") { $fqdns = @($rule.fqdns) }
    if ($cidrs.Count -eq 0 -and $fqdns.Count -eq 0) {
        Fail "Egress rule $($rule.name) requires cidrs or fqdns."
    }
    if ($fqdns.Count -gt 0) {
        Fail "FQDN egress evidence is not supported by the current Kubernetes NetworkPolicy generator. Provide reviewed CIDRs or implement a supported CNI FQDN policy generator."
    }
    foreach ($port in @($rule.ports)) {
        $protocol = [string]$port.protocol
        if (-not $protocol) { $protocol = "TCP" }
        if ($protocol -notin @("TCP", "UDP")) {
            Fail "Egress rule $($rule.name) has invalid protocol: $protocol"
        }
        $portNumber = 0
        if (-not [int]::TryParse([string]$port.port, [ref]$portNumber) -or $portNumber -lt 1 -or $portNumber -gt 65535) {
            Fail "Egress rule $($rule.name) has invalid port: $($port.port)"
        }
    }
    foreach ($cidr in $cidrs) {
        if ([string]$cidr -eq "0.0.0.0/0" -or [string]$cidr -eq "::/0") {
            Fail "Broad egress CIDR is forbidden in rule $($rule.name)."
        }
        Test-Cidr ([string]$cidr) ([string]$rule.name)
    }
    foreach ($fqdn in $fqdns) {
        Test-Fqdn ([string]$fqdn) ([string]$rule.name)
    }
}

Write-Host "Production egress evidence validation passed for $($rules.Count) rules."

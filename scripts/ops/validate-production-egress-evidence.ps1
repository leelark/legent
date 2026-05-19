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
$evidence = Get-Content -Path $EvidencePath -Raw | ConvertFrom-Json

if (-not $evidence.reviewedBy -or -not $evidence.reviewedAt) {
    Fail "Egress evidence requires reviewedBy and reviewedAt."
}
if ([string]$evidence.reviewedBy -match "replace-with|placeholder|TODO" -or [string]$evidence.reviewedAt -match "YYYY|replace-with|placeholder|TODO") {
    Fail "Egress evidence reviewer fields must not be placeholders."
}

function Test-Cidr([string]$Value, [string]$RuleName) {
    $parts = $Value.Split("/")
    if ($parts.Count -ne 2) { Fail "Invalid CIDR in rule ${RuleName}: $Value" }
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
}

function Test-Fqdn([string]$Value, [string]$RuleName) {
    if ($Value -notmatch '^(?=.{1,253}$)([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[A-Za-z]{2,63}$') {
        Fail "Invalid FQDN in rule ${RuleName}: $Value"
    }
}

$rules = @($evidence.egressRules)
if ($rules.Count -eq 0) { Fail "Egress evidence has no egressRules." }

foreach ($rule in $rules) {
    if (-not $rule.name) { Fail "Egress rule missing name." }
    foreach ($field in @("purpose", "provider")) {
        $value = [string]$rule.$field
        if (-not $value -or $value -match "replace-with|placeholder|TODO") {
            Fail "Egress rule $($rule.name) missing non-placeholder $field."
        }
    }
    if (-not $rule.ports) { Fail "Egress rule $($rule.name) missing ports." }
    $cidrs = @()
    $fqdns = @()
    if ($rule.PSObject.Properties.Name -contains "cidrs") { $cidrs = @($rule.cidrs) }
    if ($rule.PSObject.Properties.Name -contains "fqdns") { $fqdns = @($rule.fqdns) }
    if ($cidrs.Count -eq 0 -and $fqdns.Count -eq 0) {
        Fail "Egress rule $($rule.name) requires cidrs or fqdns."
    }
    if ($fqdns.Count -gt 0 -and $cidrs.Count -eq 0) {
        Fail "FQDN-only egress evidence is not supported by the Kubernetes NetworkPolicy generator. Provide CIDRs or implement a supported CNI FQDN policy generator."
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

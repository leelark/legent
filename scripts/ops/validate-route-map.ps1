param(
    [string]$RouteMapPath = "config/gateway/route-map.json",
    [string]$NginxPath = "config/nginx/nginx.conf",
    [string]$IngressPath = "infrastructure/kubernetes/ingress/ingress.yml"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail($Message) {
    Write-Error $Message
    exit 1
}

if (-not (Test-Path $RouteMapPath)) { Fail "Missing route map: $RouteMapPath" }
if (-not (Test-Path $NginxPath)) { Fail "Missing Nginx config: $NginxPath" }

$routeMap = Get-Content -Path $RouteMapPath -Raw | ConvertFrom-Json
if (-not $routeMap.routes) { Fail "Route map has no routes array." }

$routes = @($routeMap.routes)
$duplicates = $routes | Group-Object prefix | Where-Object { $_.Count -gt 1 }
if ($duplicates) {
    Fail "Duplicate route prefixes: $($duplicates.Name -join ', ')"
}

$nginx = Get-Content -Path $NginxPath -Raw
$errors = New-Object System.Collections.Generic.List[string]

function Get-LocationBody([string]$Config, [string]$Prefix) {
    $pattern = "location\s+\^~\s+$([regex]::Escape($Prefix))\s*\{(?<body>[\s\S]*?)\n\s*\}"
    $match = [regex]::Match($Config, $pattern)
    if ($match.Success) { return $match.Groups["body"].Value }
    return $null
}

foreach ($route in $routes) {
    if (-not $route.prefix -or -not $route.service -or -not $route.nginxUpstream) {
        $errors.Add("Route entry missing prefix/service/nginxUpstream: $($route | ConvertTo-Json -Compress)")
        continue
    }

    $locationBody = Get-LocationBody $nginx $route.prefix
    if (-not $locationBody) {
        $errors.Add("Nginx location missing for $($route.prefix)")
    }

    $upstreamPattern = "upstream\s+$([regex]::Escape($route.nginxUpstream))\s*\{"
    if ($nginx -notmatch $upstreamPattern) {
        $errors.Add("Nginx upstream missing for $($route.nginxUpstream)")
    }

    $proxyPattern = "proxy_pass\s+http://$([regex]::Escape($route.nginxUpstream))\s*;"
    if ($locationBody -and $locationBody -notmatch $proxyPattern) {
        $errors.Add("Nginx proxy_pass missing for $($route.prefix) -> $($route.nginxUpstream)")
    }
}

if ($routes.prefix -contains "/api/v1/track") {
    $errors.Add("/api/v1/track must remain an Nginx-only tombstone and must not be in route-map.json")
}
if ($nginx -notmatch "location\s+\^~\s+/api/v1/track\s*\{[\s\S]*?return\s+410\s*;") {
    $errors.Add("Nginx tombstone for /api/v1/track -> 410 is missing")
}

if (Test-Path $IngressPath) {
    $ingress = Get-Content -Path $IngressPath -Raw
    if ($ingress -match "/api/v1/track") {
        $errors.Add("Ingress must not route /api/v1/track")
    }
    foreach ($service in ($routes.service | Sort-Object -Unique)) {
        if ($ingress -notmatch [regex]::Escape($service)) {
            Write-Warning "Ingress text does not mention $service directly; verify regex/backend routing if this service owns public paths."
        }
    }
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Host "Route map validation passed for $($routes.Count) routes."

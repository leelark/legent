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
    $pattern = "location\s+\^~\s+$([regex]::Escape($Prefix))\s*\{"
    $match = [regex]::Match($Config, $pattern)
    if ($match.Success) {
        $start = $match.Index + $match.Length
        $depth = 1
        for ($i = $start; $i -lt $Config.Length; $i++) {
            $char = $Config[$i]
            if ($char -eq "{") {
                $depth++
            } elseif ($char -eq "}") {
                $depth--
                if ($depth -eq 0) {
                    return $Config.Substring($start, $i - $start)
                }
            }
        }
    }
    return $null
}

function Get-YamlDocumentByMetadataName([string]$Yaml, [string]$Name) {
    $escapedName = [regex]::Escape($Name)
    $documents = [regex]::Split($Yaml, "(?m)^---\s*$")
    foreach ($document in $documents) {
        if ($document -match "metadata:\s*[\s\S]*?name:\s+$escapedName(\s|`r|`n)") {
            return $document
        }
    }
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
if ($nginx -notmatch 'limit_req_zone\s+\$binary_remote_addr\s+zone=tracking_limit:10m\s+rate=200r/s;') {
    $errors.Add("Nginx tracking_limit zone must remain 200r/s for /api/v1/tracking")
}
$trackingLocationBody = Get-LocationBody $nginx "/api/v1/tracking"
if ($trackingLocationBody -and $trackingLocationBody -notmatch "limit_req\s+zone=tracking_limit\s+burst=50\s+nodelay\s*;") {
    $errors.Add("Nginx /api/v1/tracking must use tracking_limit burst=50 nodelay")
}

$requiredNginxExactInternalDenyPaths = @(
    "/api/v1/imports/internal/start",
    "/api/v1/data-extensions/query-activities/internal",
    "/api/v1/deliverability/suppressions/internal"
)
foreach ($path in $requiredNginxExactInternalDenyPaths) {
    $denyPattern = "location\s+=\s+$([regex]::Escape($path))\s*\{[\s\S]*?return\s+404\s*;"
    if ($nginx -notmatch $denyPattern) {
        $errors.Add("Nginx public-edge deny for internal route $path -> 404 is missing")
    }
}
foreach ($path in @(
    "/api/v1/deliverability/suppressions/internal/"
)) {
    $denyPattern = "location\s+\^~\s+$([regex]::Escape($path))\s*\{[\s\S]*?return\s+404\s*;"
    if ($nginx -notmatch $denyPattern) {
        $errors.Add("Nginx public-edge deny for internal route prefix $path -> 404 is missing")
    }
}
$uriToken = [regex]::Escape('$uri')
$contentSnapshotDenyPattern = "if\s*\(\s*$uriToken\s+=\s+$([regex]::Escape('/api/v1/content/rendered-content/internal'))\s*\)\s*\{[\s\S]*?return\s+404\s*;"
if ($nginx -notmatch $contentSnapshotDenyPattern) {
    $errors.Add("Nginx public-edge deny for internal route /api/v1/content/rendered-content/internal -> 404 is missing")
}
foreach ($pathRegex in @(
    "^/api/v1/content/[^/]+/render/internal$",
    "^/api/v1/content/rendered-content/[^/]+/internal$",
    "^/api/v1/content/send-governance-policies/[^/]+/internal$"
)) {
    $denyPattern = "if\s*\(\s*$uriToken\s+~\s+$([regex]::Escape($pathRegex))\s*\)\s*\{[\s\S]*?return\s+404\s*;"
    if ($nginx -notmatch $denyPattern) {
        $errors.Add("Nginx public-edge deny for internal route pattern $pathRegex -> 404 is missing")
    }
}

if (Test-Path $IngressPath) {
    $ingress = Get-Content -Path $IngressPath -Raw
    if ($ingress -match '/api/v1/track(?!ing)(\s|/|\(|\?|$)') {
        $errors.Add("Ingress must not route /api/v1/track")
    }
    $trackingIngress = Get-YamlDocumentByMetadataName $ingress "legent-tracking-ingress"
    if (-not $trackingIngress) {
        $errors.Add("Kubernetes legent-tracking-ingress is missing")
    } else {
        if ($trackingIngress -notmatch 'nginx\.ingress\.kubernetes\.io/limit-rps:\s*"200"') {
            $errors.Add("Kubernetes tracking ingress must declare reviewed ingress-nginx limit-rps 200")
        }
        if ($trackingIngress -notmatch 'path:\s+/api/v1/tracking\(/\.\*\)\?') {
            $errors.Add("Kubernetes tracking ingress must route /api/v1/tracking only")
        }
        if ($trackingIngress -match '/api/v1/analytics' -or $trackingIngress -match '/ws/analytics') {
            $errors.Add("Kubernetes high-throughput tracking ingress must not include analytics or websocket paths")
        }
    }
    $analyticsIngress = Get-YamlDocumentByMetadataName $ingress "legent-tracking-analytics-ingress"
    if (-not $analyticsIngress) {
        $errors.Add("Kubernetes legent-tracking-analytics-ingress is missing")
    } else {
        if ($analyticsIngress -notmatch 'nginx\.ingress\.kubernetes\.io/limit-rps:\s*"100"') {
            $errors.Add("Kubernetes tracking analytics ingress must keep normal API ingress-nginx limit-rps 100")
        }
        if ($analyticsIngress -notmatch 'path:\s+/api/v1/analytics\(/\.\*\)\?' -or $analyticsIngress -notmatch 'path:\s+/ws/analytics\(/\.\*\)\?') {
            $errors.Add("Kubernetes tracking analytics ingress must route analytics API and websocket paths")
        }
    }
    foreach ($path in @(
        "/api/v1/imports/internal/start",
        "/api/v1/data-extensions/query-activities/internal",
        "/api/v1/content/rendered-content/internal",
        "/api/v1/deliverability/suppressions/internal"
    )) {
        $denyPattern = "location\s+=\s+$([regex]::Escape($path))\s*\{[\s\S]*?return\s+404\s*;"
        if ($ingress -notmatch $denyPattern) {
            $errors.Add("Ingress public-edge deny for internal route $path -> 404 is missing")
        }
    }
    foreach ($path in @(
        "/api/v1/deliverability/suppressions/internal/"
    )) {
        $denyPattern = "location\s+\^~\s+$([regex]::Escape($path))\s*\{[\s\S]*?return\s+404\s*;"
        if ($ingress -notmatch $denyPattern) {
            $errors.Add("Ingress public-edge deny for internal route prefix $path -> 404 is missing")
        }
    }
    foreach ($pathRegex in @(
        "^/api/v1/content/[^/]+/render/internal$",
        "^/api/v1/content/rendered-content/[^/]+/internal$",
        "^/api/v1/content/send-governance-policies/[^/]+/internal$"
    )) {
        $denyPattern = "location\s+~\s+$([regex]::Escape($pathRegex))\s*\{[\s\S]*?return\s+404\s*;"
        if ($ingress -notmatch $denyPattern) {
            $errors.Add("Ingress public-edge deny for internal route pattern $pathRegex -> 404 is missing")
        }
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

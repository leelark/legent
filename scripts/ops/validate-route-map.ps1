param(
    [string] $RouteMapPath = "config/gateway/route-map.json",
    [string] $NginxConfigPath = "config/nginx/nginx.conf",
    [string] $IngressPath = "infrastructure/kubernetes/ingress/ingress.yml"
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$routeMap = Get-Content -Raw (Join-Path $repoRoot $RouteMapPath) | ConvertFrom-Json
$nginx = Get-Content -Raw (Join-Path $repoRoot $NginxConfigPath)
$ingressLines = Get-Content (Join-Path $repoRoot $IngressPath)

function Assert-NginxRoute($Route) {
    $prefix = [regex]::Escape($Route.prefix)
    $upstream = [regex]::Escape($Route.nginxUpstream)
    $pattern = "location\s+\^~\s+$prefix\s*\{(?s:.*?)proxy_pass\s+http://$upstream;"
    if ($nginx -notmatch $pattern) {
        throw "Nginx route mismatch: $($Route.prefix) should proxy to $($Route.nginxUpstream)"
    }
}

function Test-IngressPathMatch([string] $PathPattern, [string] $Prefix) {
    if ($PathPattern -eq "$Prefix(/.*)?") {
        return $true
    }

    $match = [regex]::Match($PathPattern, "^(?<base>.*?)\((?<options>[^)]+)\)(?<tail>.*)$")
    if (-not $match.Success) {
        return $false
    }

    $base = $match.Groups["base"].Value
    if (-not $Prefix.StartsWith($base)) {
        return $false
    }

    $remaining = $Prefix.Substring($base.Length).TrimStart("/")
    if (-not $remaining) {
        return $false
    }

    $segment = $remaining.Split("/")[0]
    return ($match.Groups["options"].Value.Split("|") -contains $segment)
}

function Find-IngressService([string] $Prefix) {
    for ($i = 0; $i -lt $ingressLines.Count; $i++) {
        if ($ingressLines[$i] -match "^\s*-\s+path:\s+(.+?)\s*$") {
            $pathPattern = $Matches[1].Trim()
            if (-not (Test-IngressPathMatch $pathPattern $Prefix)) {
                continue
            }
            $blockEnd = [Math]::Min($i + 24, $ingressLines.Count - 1)
            for ($j = $i; $j -le $blockEnd; $j++) {
                if ($ingressLines[$j] -match "^\s*name:\s+(\S+)\s*$") {
                    return $Matches[1].Trim()
                }
            }
        }
    }
    return $null
}

foreach ($route in $routeMap.routes) {
    Assert-NginxRoute $route
    $service = Find-IngressService $route.prefix
    if ($service -ne $route.service) {
        throw "Ingress route mismatch: $($route.prefix) should route to $($route.service), found $service"
    }
}

Write-Host "Gateway route map validation passed for $($routeMap.routes.Count) routes"

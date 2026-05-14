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

function Assert-NoRouteMapShadowing {
    for ($i = 0; $i -lt $routeMap.routes.Count; $i++) {
        $parent = $routeMap.routes[$i]
        for ($j = $i + 1; $j -lt $routeMap.routes.Count; $j++) {
            $child = $routeMap.routes[$j]
            if ($child.prefix.StartsWith("$($parent.prefix)/")) {
                throw "Route map precedence error: parent prefix $($parent.prefix) at position $($i + 1) shadows later child prefix $($child.prefix) at position $($j + 1). Move the child route before the parent."
            }
        }
    }
}

function Assert-NginxRoute($Route) {
    $prefix = [regex]::Escape($Route.prefix)
    $upstream = [regex]::Escape($Route.nginxUpstream)
    $pattern = "location\s+\^~\s+$prefix\s*\{(?s:.*?)proxy_pass\s+http://$upstream;"
    if ($nginx -notmatch $pattern) {
        throw "Nginx route mismatch: $($Route.prefix) should proxy to $($Route.nginxUpstream)"
    }
}

function Test-IngressPathMatch([string] $PathPattern, [string] $Prefix) {
    try {
        return $Prefix -match "^$PathPattern"
    }
    catch {
        throw "Invalid ingress regex path '$PathPattern': $($_.Exception.Message)"
    }
}

function Read-IngressRoutes {
    $routes = @()
    for ($i = 0; $i -lt $ingressLines.Count; $i++) {
        if ($ingressLines[$i] -match "^\s*-\s+path:\s+(.+?)\s*$") {
            $pathPattern = $Matches[1].Trim()
            $blockEnd = [Math]::Min($i + 24, $ingressLines.Count - 1)
            for ($j = $i; $j -le $blockEnd; $j++) {
                if ($ingressLines[$j] -match "^\s*name:\s+(\S+)\s*$") {
                    $routes += [pscustomobject]@{
                        Path    = $pathPattern
                        Service = $Matches[1].Trim()
                        Order   = $routes.Count
                        Line    = $i + 1
                    }
                    break
                }
            }
        }
    }
    return $routes
}

Assert-NoRouteMapShadowing

$ingressRoutes = Read-IngressRoutes
# ingress-nginx renders regex paths in descending path length before original order.
$renderedIngressRoutes = @($ingressRoutes | Sort-Object -Property @{ Expression = { $_.Path.Length }; Descending = $true }, @{ Expression = { $_.Order } })

function Find-RenderedIngressRoute([string] $Prefix) {
    foreach ($route in $renderedIngressRoutes) {
        if (Test-IngressPathMatch $route.Path $Prefix) {
            return $route
        }
    }
    return $null
}

foreach ($route in $routeMap.routes) {
    Assert-NginxRoute $route
    $ingressRoute = Find-RenderedIngressRoute $route.prefix
    $service = if ($null -eq $ingressRoute) { $null } else { $ingressRoute.Service }
    if ($service -ne $route.service) {
        $path = if ($null -eq $ingressRoute) { "<none>" } else { "$($ingressRoute.Path) on line $($ingressRoute.Line)" }
        throw "Ingress route mismatch after rendered precedence: $($route.prefix) should route to $($route.service), found $service via $path"
    }
}

$criticalIngressExpectations = @(
    @{ Prefix = "/api/v1/public/landing-pages"; Service = "content-service" },
    @{ Prefix = "/api/v1/admin/webhooks"; Service = "platform-service" },
    @{ Prefix = "/api/v1/admin/search"; Service = "platform-service" }
)

foreach ($expectation in $criticalIngressExpectations) {
    $ingressRoute = Find-RenderedIngressRoute $expectation.Prefix
    if ($null -eq $ingressRoute -or $ingressRoute.Service -ne $expectation.Service) {
        $actual = if ($null -eq $ingressRoute) { "<none>" } else { "$($ingressRoute.Service) via $($ingressRoute.Path) on line $($ingressRoute.Line)" }
        throw "Critical ingress precedence mismatch: $($expectation.Prefix) should route to $($expectation.Service), found $actual"
    }
}

function Find-RouteForControllerRoot([string] $Root) {
    $matches = @($routeMap.routes | Where-Object {
            $Root -eq $_.prefix -or $Root.StartsWith("$($_.prefix)/")
        } | Sort-Object { $_.prefix.Length } -Descending)

    if ($matches.Count -gt 0) {
        return $matches[0]
    }

    return $null
}

$controllerFiles = Get-ChildItem (Join-Path $repoRoot "services") -Recurse -Filter "*.java" |
    Where-Object { $_.FullName -match "\\src\\main\\java\\.*\\controller\\" }

foreach ($file in $controllerFiles) {
    $relativePath = Resolve-Path -Relative $file.FullName
    if ($relativePath -notmatch "^\.\\services\\([^\\]+)\\") {
        continue
    }

    $serviceName = $Matches[1]
    $content = Get-Content -Raw $file.FullName
    $requestMappings = [regex]::Matches($content, '@RequestMapping\s*\((?<args>[^)]*)\)', [System.Text.RegularExpressions.RegexOptions]::Singleline)
    foreach ($requestMapping in $requestMappings) {
        $literalRoots = [regex]::Matches($requestMapping.Groups["args"].Value, '"(?<root>/api/v1[^"]*)"')
        foreach ($literalRoot in $literalRoots) {
            $root = $literalRoot.Groups["root"].Value.TrimEnd("/")
        if ($root -eq "/api/v1") {
            continue
        }

        $route = Find-RouteForControllerRoot $root
        if ($null -eq $route) {
            throw "Controller route missing from route map: $root in $relativePath should route to $serviceName"
        }

        if ($route.service -ne $serviceName) {
            throw "Controller route ownership mismatch: $root in $relativePath should route to $serviceName, found $($route.service)"
        }
        }
    }
}

Write-Host "Gateway route map validation passed for $($routeMap.routes.Count) routes, rendered ingress precedence, and literal controller @RequestMapping roots"

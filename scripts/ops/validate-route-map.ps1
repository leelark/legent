param(
    [string]$RouteMapPath = "config/gateway/route-map.json",
    [string]$NginxPath = "config/nginx/nginx.conf",
    [string]$IngressPath = "infrastructure/kubernetes/ingress/ingress.yml",
    [string]$SourceRoot = "services",
    [string]$AppConstantsPath = "shared/legent-common/src/main/java/com/legent/common/constant/AppConstants.java"
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

function Test-NginxUpstreamTarget([string]$Config, [string]$UpstreamName) {
    $upstreamPattern = "upstream\s+$([regex]::Escape($UpstreamName))\s*\{"
    $dynamicVariable = '$' + $UpstreamName + "_url"
    $dynamicTargetPattern = "set\s+$([regex]::Escape($dynamicVariable))\s+http://[^;]+;"
    return ($Config -match $upstreamPattern -or $Config -match $dynamicTargetPattern)
}

function Test-NginxProxyPassTarget([string]$LocationBody, [string]$UpstreamName) {
    if (-not $LocationBody) {
        return $false
    }
    $staticProxyPattern = "proxy_pass\s+http://$([regex]::Escape($UpstreamName))\s*;"
    $dynamicVariable = '$' + $UpstreamName + "_url"
    $dynamicProxyPattern = "proxy_pass\s+$([regex]::Escape($dynamicVariable))\s*;"
    return ($LocationBody -match $staticProxyPattern -or $LocationBody -match $dynamicProxyPattern)
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

function Normalize-RoutePath([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path) -or $Path.Trim() -eq "/") {
        return ""
    }
    $normalized = $Path.Trim().Replace("\", "/")
    if (-not $normalized.StartsWith("/")) {
        $normalized = "/$normalized"
    }
    while ($normalized.Contains("//")) {
        $normalized = $normalized.Replace("//", "/")
    }
    if ($normalized.Length -gt 1) {
        $normalized = $normalized.TrimEnd("/")
    }
    return $normalized
}

function Read-YamlScalar([string]$Value) {
    if ($null -eq $Value) {
        return ""
    }
    $trimmed = $Value.Trim()
    $commentIndex = $trimmed.IndexOf(" #", [System.StringComparison]::Ordinal)
    if ($commentIndex -ge 0) {
        $trimmed = $trimmed.Substring(0, $commentIndex).Trim()
    }
    if ($trimmed.Length -ge 2) {
        $first = $trimmed[0]
        $last = $trimmed[$trimmed.Length - 1]
        if (($first -eq '"' -and $last -eq '"') -or ($first -eq "'" -and $last -eq "'")) {
            return $trimmed.Substring(1, $trimmed.Length - 2)
        }
    }
    return $trimmed
}

function Get-IndentLength([string]$Line) {
    return ([regex]::Match($Line, "^\s*")).Length
}

function Get-IngressPathBackends([string]$Yaml) {
    $records = New-Object System.Collections.Generic.List[object]
    $lines = $Yaml -split "`r?`n"
    $pendingPath = $null
    $pendingPathIndent = -1
    $insideBackend = $false
    $insideService = $false
    $order = 0

    foreach ($line in $lines) {
        if ($line -match '^(?<indent>\s*)-\s+path:\s+(?<path>.+?)\s*$') {
            $pendingPath = Read-YamlScalar $Matches["path"]
            $pendingPathIndent = ([string]$Matches["indent"]).Length
            $insideBackend = $false
            $insideService = $false
            continue
        }

        if (-not $pendingPath) {
            continue
        }

        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) {
            continue
        }

        $indent = Get-IndentLength $line
        if ($indent -le $pendingPathIndent) {
            $pendingPath = $null
            $pendingPathIndent = -1
            $insideBackend = $false
            $insideService = $false
            continue
        }

        if ($line -match '^\s*backend:\s*$') {
            $insideBackend = $true
            continue
        }
        if ($insideBackend -and $line -match '^\s*service:\s*$') {
            $insideService = $true
            continue
        }
        if ($insideBackend -and $insideService -and $line -match '^\s*name:\s+(?<name>.+?)\s*$') {
            $records.Add([pscustomobject]@{
                Order = $order
                Path = $pendingPath
                ServiceName = Read-YamlScalar $Matches["name"]
            })
            $order++
            $pendingPath = $null
            $pendingPathIndent = -1
            $insideBackend = $false
            $insideService = $false
        }
    }

    return $records.ToArray()
}

function Test-IngressPathCoversRoutePrefix([string]$IngressPath, [string]$RoutePrefix) {
    if ([string]::IsNullOrWhiteSpace($IngressPath) -or [string]::IsNullOrWhiteSpace($RoutePrefix)) {
        return $false
    }
    $normalizedPrefix = Normalize-RoutePath $RoutePrefix
    $pattern = "^$IngressPath$"
    try {
        return ([regex]::IsMatch($normalizedPrefix, $pattern) -and [regex]::IsMatch("$normalizedPrefix/__route_probe__", $pattern))
    } catch {
        Fail "Invalid Kubernetes ingress path regex [$IngressPath]: $($_.Exception.Message)"
    }
}

function Find-IngressRouteMatch($IngressPathBackends, [string]$RoutePrefix) {
    foreach ($backend in @($IngressPathBackends | Sort-Object Order)) {
        if (Test-IngressPathCoversRoutePrefix $backend.Path $RoutePrefix) {
            return $backend
        }
    }
    return $null
}

function Join-RoutePath([string]$BasePath, [string]$ChildPath) {
    $base = Normalize-RoutePath $BasePath
    $child = Normalize-RoutePath $ChildPath
    if (-not $base) { return $child }
    if (-not $child) { return $base }
    return Normalize-RoutePath "$($base.TrimEnd('/'))/$($child.TrimStart('/'))"
}

function Get-AppConstantsApiBasePath([string]$Path) {
    if (-not (Test-Path $Path)) {
        Fail "Missing AppConstants source: $Path"
    }
    $content = Get-Content -Path $Path -Raw
    $match = [regex]::Match($content, 'API_BASE_PATH\s*=\s*"(?<path>[^"]+)"')
    if (-not $match.Success) {
        Fail "Could not discover API_BASE_PATH in $Path"
    }
    return Normalize-RoutePath $match.Groups["path"].Value
}

function Get-MappingPathsFromExpression([string]$Expression) {
    if ([string]::IsNullOrWhiteSpace($Expression)) {
        return @("")
    }

    $expanded = $Expression -replace "AppConstants\.API_BASE_PATH", "`"$script:ApiBasePath`""
    $pathValues = New-Object System.Collections.Generic.List[string]
    foreach ($match in [regex]::Matches($expanded, '"(?<value>[^"]*)"')) {
        $value = [string]$match.Groups["value"].Value
        if ($value.StartsWith("/") -or $value -eq "") {
            $pathValues.Add($value)
        }
    }

    if ($pathValues.Count -eq 0) {
        return @("")
    }
    if ($expanded -match "\+") {
        return @(Normalize-RoutePath ($pathValues -join ""))
    }
    return @($pathValues | ForEach-Object { Normalize-RoutePath $_ } | Sort-Object -Unique)
}

function Convert-RouteTemplateToRegex([string]$Template) {
    $placeholder = "LEGENT_PATH_VARIABLE"
    $withPlaceholders = [regex]::Replace($Template, "\{[^}/]+\}", $placeholder)
    return "^$(([regex]::Escape($withPlaceholders)).Replace($placeholder, '[^/]+'))$"
}

function Get-InternalPrefixDenyPath([string]$Path) {
    $marker = "/internal/"
    $index = $Path.IndexOf($marker, [System.StringComparison]::OrdinalIgnoreCase)
    if ($index -lt 0) {
        return $null
    }
    return $Path.Substring(0, $index + $marker.Length)
}

function Test-ExactInternalDeny([string]$Config, [string]$Path) {
    $escapedPath = [regex]::Escape($Path)
    $uriToken = [regex]::Escape('$uri')
    $locationPattern = "location\s+=\s+$escapedPath\s*\{[\s\S]*?return\s+404\s*;"
    $uriPattern = "if\s*\(\s*$uriToken\s+=\s+$escapedPath\s*\)\s*\{[\s\S]*?return\s+404\s*;"
    return ($Config -match $locationPattern -or $Config -match $uriPattern)
}

function Test-PrefixInternalDeny([string]$Config, [string]$Path) {
    $prefix = Get-InternalPrefixDenyPath $Path
    if (-not $prefix) {
        return $false
    }
    $prefixPattern = "location\s+\^~\s+$([regex]::Escape($prefix))\s*\{[\s\S]*?return\s+404\s*;"
    return ($Config -match $prefixPattern)
}

function Test-StaticInternalDeny([string]$Config, [string]$Path) {
    return ((Test-ExactInternalDeny $Config $Path) -or (Test-PrefixInternalDeny $Config $Path))
}

function Test-RegexInternalDeny([string]$Config, [string]$RouteRegex) {
    $escapedRegex = [regex]::Escape($RouteRegex)
    $uriToken = [regex]::Escape('$uri')
    $uriPattern = "if\s*\(\s*$uriToken\s+~\s+$escapedRegex\s*\)\s*\{[\s\S]*?return\s+404\s*;"
    $locationPattern = "location\s+~\*?\s+$escapedRegex\s*\{[\s\S]*?return\s+404\s*;"
    return ($Config -match $uriPattern -or $Config -match $locationPattern)
}

function Get-InternalControllerRoutes([string]$RootPath) {
    if (-not (Test-Path $RootPath)) {
        return @()
    }

    $routes = New-Object System.Collections.Generic.List[object]
    $singleline = [System.Text.RegularExpressions.RegexOptions]::Singleline
    $classPattern = '@RequestMapping\s*(?:\((?<expr>[^)]*)\))?[\s\S]{0,800}?\b(?:class|record)\s+\w+'
    $methodPattern = '@(?:GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping|RequestMapping)\s*(?:\((?<expr>[^)]*)\))?'

    foreach ($file in Get-ChildItem -Path $RootPath -Recurse -Filter *.java) {
        if ($file.FullName -notmatch "\\src\\main\\java\\") {
            continue
        }
        $content = Get-Content -Path $file.FullName -Raw
        if ($content -notmatch "@RestController" -and $content -notmatch "@Controller") {
            continue
        }

        $classBasePaths = @("")
        $classMatch = [regex]::Match($content, $classPattern, $singleline)
        $methodContent = $content
        if ($classMatch.Success) {
            $classBasePaths = @(Get-MappingPathsFromExpression $classMatch.Groups["expr"].Value)
            $methodContent = $content.Substring($classMatch.Index + $classMatch.Length)
        }

        foreach ($methodMatch in [regex]::Matches($methodContent, $methodPattern, $singleline)) {
            $methodPaths = @(Get-MappingPathsFromExpression $methodMatch.Groups["expr"].Value)
            foreach ($basePath in $classBasePaths) {
                foreach ($methodPath in $methodPaths) {
                    $template = Join-RoutePath $basePath $methodPath
                    if ($template -notmatch "/internal($|/)") {
                        continue
                    }
                    $source = $file.FullName
                    try {
                        $source = Resolve-Path -Path $file.FullName -Relative
                    } catch {
                        $source = $file.FullName
                    }
                    $routes.Add([pscustomobject]@{
                        Template = $template
                        RouteRegex = Convert-RouteTemplateToRegex $template
                        IsTemplated = ($template -match "\{[^}/]+\}")
                        Source = $source
                    })
                }
            }
        }
    }

    return @($routes | Sort-Object Template, Source | Group-Object Template | ForEach-Object { $_.Group[0] })
}

$script:ApiBasePath = Get-AppConstantsApiBasePath $AppConstantsPath
$sourceInternalRoutes = @(Get-InternalControllerRoutes $SourceRoot)
if ($sourceInternalRoutes.Count -eq 0) {
    $errors.Add("Source scan found no Spring controller internal routes under $SourceRoot; route validation cannot prove public-edge denial coverage.")
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

    if (-not (Test-NginxUpstreamTarget $nginx $route.nginxUpstream)) {
        $errors.Add("Nginx upstream or dynamic target missing for $($route.nginxUpstream)")
    }

    if ($locationBody -and -not (Test-NginxProxyPassTarget $locationBody $route.nginxUpstream)) {
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

foreach ($internalRoute in $sourceInternalRoutes) {
    if ($internalRoute.IsTemplated) {
        if (-not (Test-RegexInternalDeny $nginx $internalRoute.RouteRegex)) {
            $errors.Add("Nginx public-edge deny for source-discovered internal route pattern $($internalRoute.RouteRegex) from $($internalRoute.Source) -> 404 is missing")
        }
    } elseif (-not (Test-StaticInternalDeny $nginx $internalRoute.Template)) {
        $errors.Add("Nginx public-edge deny for source-discovered internal route $($internalRoute.Template) from $($internalRoute.Source) -> 404 is missing")
    }
}

if (Test-Path $IngressPath) {
    $ingress = Get-Content -Path $IngressPath -Raw
    $ingressPathBackends = @(Get-IngressPathBackends $ingress)
    if ($ingressPathBackends.Count -eq 0) {
        $errors.Add("Kubernetes ingress path/backend scan found no routable paths.")
    }
    foreach ($route in $routes) {
        $matchingIngress = Find-IngressRouteMatch $ingressPathBackends $route.prefix
        if (-not $matchingIngress) {
            $errors.Add("Kubernetes ingress path missing for route-map prefix $($route.prefix) -> $($route.service)")
        } elseif ([string]$matchingIngress.ServiceName -ne [string]$route.service) {
            $errors.Add("Kubernetes ingress first matching path [$($matchingIngress.Path)] for route-map prefix $($route.prefix) routes to $($matchingIngress.ServiceName), expected $($route.service)")
        }
    }
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
    foreach ($internalRoute in $sourceInternalRoutes) {
        if ($internalRoute.IsTemplated) {
            if (-not (Test-RegexInternalDeny $ingress $internalRoute.RouteRegex)) {
                $errors.Add("Ingress public-edge deny for source-discovered internal route pattern $($internalRoute.RouteRegex) from $($internalRoute.Source) -> 404 is missing")
            }
        } elseif (-not (Test-StaticInternalDeny $ingress $internalRoute.Template)) {
            $errors.Add("Ingress public-edge deny for source-discovered internal route $($internalRoute.Template) from $($internalRoute.Source) -> 404 is missing")
        }
    }
    foreach ($service in ($routes.service | Sort-Object -Unique)) {
        if ($ingress -notmatch [regex]::Escape($service)) {
            Write-Warning "Ingress text does not mention $service directly; verify regex/backend routing if this service owns public paths."
        }
    }
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Error $_ -ErrorAction Continue }
    exit 1
}

Write-Host "Route map validation passed for $($routes.Count) routes and $($sourceInternalRoutes.Count) source-discovered internal routes."

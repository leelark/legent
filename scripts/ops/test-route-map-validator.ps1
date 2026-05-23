param(
    [ValidateRange(1, 3600)][int]$ChildProcessTimeoutSeconds = 60,
    [string[]]$CaseName = @(),
    [switch]$KeepFixtures
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$script:ChildProcessTimeoutSeconds = $ChildProcessTimeoutSeconds
$script:RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$script:ValidatorPath = "scripts/ops/validate-route-map.ps1"
$script:PowerShellExecutable = (Get-Process -Id $PID).Path
if ([string]::IsNullOrWhiteSpace($script:PowerShellExecutable)) {
    $script:PowerShellExecutable = if ($PSVersionTable.PSEdition -eq "Core") { "pwsh" } else { "powershell" }
}

function Fail($Message) {
    Write-Error $Message
    exit 1
}

function Write-TextFile([string]$Path, [string]$Value) {
    $directory = Split-Path -Parent $Path
    if ($directory) {
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
    }
    Set-Content -Path $Path -Value $Value -Encoding UTF8
}

function Get-FileExcerpt([string]$Path) {
    if (-not (Test-Path $Path)) { return "" }
    $content = Get-Content -Path $Path -Raw -ErrorAction SilentlyContinue
    if (-not $content) { return "" }
    $trimmed = $content.Trim()
    if ($trimmed.Length -le 3000) { return $trimmed }
    return $trimmed.Substring(0, 3000) + "... [truncated]"
}

function Stop-ProcessTree([int]$ProcessId) {
    $children = @(Get-CimInstance Win32_Process -Filter "ParentProcessId = $ProcessId" -ErrorAction SilentlyContinue)
    foreach ($child in $children) {
        Stop-ProcessTree -ProcessId ([int]$child.ProcessId)
    }
    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

function New-RouteMapValidatorFixtureContent {
    $routeMap = @"
{
  "routes": [
    {
      "prefix": "/api/v1/foundation",
      "service": "foundation-service",
      "nginxUpstream": "foundation_service"
    },
    {
      "prefix": "/api/v1/tracking",
      "service": "tracking-service",
      "nginxUpstream": "tracking_service"
    }
  ]
}
"@

    $nginx = @'
events {}
http {
    limit_req_zone $binary_remote_addr zone=tracking_limit:10m rate=200r/s;

    upstream foundation_service {
        server foundation-service:8080;
    }

    upstream tracking_service {
        server tracking-service:8080;
    }

    server {
        listen 8080;

        location ^~ /api/v1/foundation/internal/ {
            return 404;
        }

        if ($uri ~ ^/api/v1/foundation/internal/jobs/[^/]+$) {
            return 404;
        }

        location ^~ /api/v1/foundation {
            proxy_pass http://foundation_service;
        }

        location ^~ /api/v1/tracking {
            limit_req zone=tracking_limit burst=50 nodelay;
            proxy_pass http://tracking_service;
        }

        location ^~ /api/v1/track {
            return 410;
        }
    }
}
'@

    $ingress = @'
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: legent-public-ingress
  annotations:
    nginx.ingress.kubernetes.io/server-snippet: |
      location ^~ /api/v1/foundation/internal/ {
        return 404;
      }
      if ($uri ~ ^/api/v1/foundation/internal/jobs/[^/]+$) {
        return 404;
      }
spec:
  rules:
    - http:
        paths:
          - path: /api/v1/foundation(/.*)?
            pathType: ImplementationSpecific
            backend:
              service:
                name: foundation-service
                port:
                  number: 8080
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: legent-tracking-ingress
  annotations:
    nginx.ingress.kubernetes.io/limit-rps: "200"
spec:
  rules:
    - http:
        paths:
          - path: /api/v1/tracking(/.*)?
            pathType: ImplementationSpecific
            backend:
              service:
                name: tracking-service
                port:
                  number: 8080
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: legent-tracking-analytics-ingress
  annotations:
    nginx.ingress.kubernetes.io/limit-rps: "100"
spec:
  rules:
    - http:
        paths:
          - path: /api/v1/analytics(/.*)?
            pathType: ImplementationSpecific
            backend:
              service:
                name: tracking-service
                port:
                  number: 8080
          - path: /ws/analytics(/.*)?
            pathType: ImplementationSpecific
            backend:
              service:
                name: tracking-service
                port:
                  number: 8080
'@

    $appConstants = @'
package com.legent.common.constant;

public final class AppConstants {
    public static final String API_BASE_PATH = "/api/v1";

    private AppConstants() {
    }
}
'@

    $controller = @'
package com.legent.foundation.web;

import com.legent.common.constant.AppConstants;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AppConstants.API_BASE_PATH + "/foundation")
class FixtureInternalController {
    @GetMapping("/internal/status")
    String status() {
        return "ok";
    }

    @GetMapping("/internal/jobs/{jobId}")
    String job(String jobId) {
        return jobId;
    }
}
'@

    return [pscustomobject]@{
        RouteMap = $routeMap
        Nginx = $nginx
        Ingress = $ingress
        AppConstants = $appConstants
        Controller = $controller
    }
}

function Write-RouteMapValidatorFixture([string]$Root, $Fixture) {
    Write-TextFile (Join-Path $Root "config/gateway/route-map.json") $Fixture.RouteMap
    Write-TextFile (Join-Path $Root "config/nginx/nginx.conf") $Fixture.Nginx
    Write-TextFile (Join-Path $Root "infrastructure/kubernetes/ingress/ingress.yml") $Fixture.Ingress
    Write-TextFile (Join-Path $Root "shared/legent-common/src/main/java/com/legent/common/constant/AppConstants.java") $Fixture.AppConstants
    Write-TextFile (Join-Path $Root "services/foundation-service/src/main/java/com/legent/foundation/web/FixtureInternalController.java") $Fixture.Controller
}

function New-RouteMapValidatorFixture([string]$Name, [scriptblock]$Mutate) {
    $fixtureRoot = Join-Path $script:TempRoot $Name
    $fixture = New-RouteMapValidatorFixtureContent
    if ($Mutate) {
        & $Mutate $fixture
    }
    Write-RouteMapValidatorFixture $fixtureRoot $fixture
    return $fixtureRoot
}

function Quote-ProcessArgument([string]$Argument) {
    return '"' + $Argument.Replace('"', '\"') + '"'
}

function Invoke-ValidatorProcess([string]$Name, [string]$FixtureRoot) {
    $outPath = Join-Path $script:TempRoot "$Name.out"
    $errPath = Join-Path $script:TempRoot "$Name.err"
    $arguments = @(
        "-ExecutionPolicy", "Bypass",
        "-File", $script:ValidatorPath,
        "-RouteMapPath", (Join-Path $FixtureRoot "config/gateway/route-map.json"),
        "-NginxPath", (Join-Path $FixtureRoot "config/nginx/nginx.conf"),
        "-IngressPath", (Join-Path $FixtureRoot "infrastructure/kubernetes/ingress/ingress.yml"),
        "-SourceRoot", (Join-Path $FixtureRoot "services"),
        "-AppConstantsPath", (Join-Path $FixtureRoot "shared/legent-common/src/main/java/com/legent/common/constant/AppConstants.java")
    )

    $processInfo = New-Object System.Diagnostics.ProcessStartInfo
    $processInfo.FileName = $script:PowerShellExecutable
    $processInfo.Arguments = (($arguments | ForEach-Object { Quote-ProcessArgument $_ }) -join " ")
    $processInfo.WorkingDirectory = $script:RepoRoot
    $processInfo.UseShellExecute = $false
    $processInfo.CreateNoWindow = $true
    $processInfo.RedirectStandardOutput = $true
    $processInfo.RedirectStandardError = $true

    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo = $processInfo
    [void]$proc.Start()
    $stdoutTask = $proc.StandardOutput.ReadToEndAsync()
    $stderrTask = $proc.StandardError.ReadToEndAsync()
    $completed = $proc.WaitForExit($script:ChildProcessTimeoutSeconds * 1000)
    if (-not $completed) {
        Stop-ProcessTree -ProcessId $proc.Id
        $proc.WaitForExit(5000) | Out-Null
        $stdout = $stdoutTask.Result
        $stderr = $stderrTask.Result
        Write-TextFile $outPath $stdout
        Write-TextFile $errPath $stderr
        Fail "Route-map validator fixture '$Name' exceeded ${script:ChildProcessTimeoutSeconds}s. stdout=[$stdout] stderr=[$stderr]"
    }
    $proc.WaitForExit()
    Write-TextFile $outPath $stdoutTask.Result
    Write-TextFile $errPath $stderrTask.Result
    return [pscustomobject]@{
        ExitCode = $proc.ExitCode
        StdoutPath = $outPath
        StderrPath = $errPath
    }
}

function Get-ValidatorCombinedOutput($Process) {
    return (Get-FileExcerpt $Process.StdoutPath) + "`n" + (Get-FileExcerpt $Process.StderrPath)
}

function Assert-RouteMapPasses([string]$Name, [scriptblock]$Mutate = $null) {
    $fixtureRoot = New-RouteMapValidatorFixture $Name $Mutate
    $proc = Invoke-ValidatorProcess $Name $fixtureRoot
    if ($proc.ExitCode -ne 0) {
        Fail "Route-map validator fixture '$Name' should pass. stdout/stderr=[$(Get-ValidatorCombinedOutput $proc)]"
    }
}

function Assert-RouteMapFails([string]$Name, [scriptblock]$Mutate, [string]$ExpectedMessage) {
    $fixtureRoot = New-RouteMapValidatorFixture $Name $Mutate
    $proc = Invoke-ValidatorProcess $Name $fixtureRoot
    if ($proc.ExitCode -eq 0) {
        Fail "Route-map validator fixture '$Name' should fail."
    }
    $combinedOutput = Get-ValidatorCombinedOutput $proc
    $normalizedOutput = $combinedOutput -replace "\s+", " "
    $normalizedExpected = $ExpectedMessage -replace "\s+", " "
    if (-not $normalizedOutput.Contains($normalizedExpected)) {
        Fail "Route-map validator fixture '$Name' failed for an unexpected reason. Expected [$ExpectedMessage]. stdout/stderr=[$combinedOutput]"
    }
    $global:LASTEXITCODE = 0
}

function Remove-TextBlock([string]$Content, [string]$Block) {
    $updated = $Content.Replace($Block, "")
    if ($updated -eq $Content) {
        Fail "Fixture mutation could not remove expected block."
    }
    return $updated
}

function Replace-TextBlock([string]$Content, [string]$OldValue, [string]$NewValue) {
    $updated = $Content.Replace($OldValue, $NewValue)
    if ($updated -eq $Content) {
        Fail "Fixture mutation could not replace expected block."
    }
    return $updated
}

function Add-RouteMapRoute([string]$RouteMapJson, [string]$Prefix, [string]$Service, [string]$NginxUpstream) {
    $routeMap = $RouteMapJson | ConvertFrom-Json
    $routeMap.routes = @($routeMap.routes) + [pscustomobject]@{
        prefix = $Prefix
        service = $Service
        nginxUpstream = $NginxUpstream
    }
    return ($routeMap | ConvertTo-Json -Depth 8)
}

$validatorFullPath = Join-Path $script:RepoRoot $script:ValidatorPath
if (-not (Test-Path $validatorFullPath)) {
    Fail "Missing route-map validator: $validatorFullPath"
}
$tokens = $null
$parseErrors = $null
[System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path $validatorFullPath), [ref]$tokens, [ref]$parseErrors) | Out-Null
if ($parseErrors.Count -gt 0) {
    Fail "PowerShell parser errors in $script:ValidatorPath"
}

$allCases = @(
    [pscustomobject]@{
        Name = "missing-nginx-static-internal-deny"
        ExpectedMessage = "Nginx public-edge deny for source-discovered internal route /api/v1/foundation/internal/status"
        Mutate = {
            param($Fixture)
            $Fixture.Nginx = Remove-TextBlock $Fixture.Nginx @'
        location ^~ /api/v1/foundation/internal/ {
            return 404;
        }

'@
        }
    },
    [pscustomobject]@{
        Name = "missing-nginx-templated-internal-deny"
        ExpectedMessage = "Nginx public-edge deny for source-discovered internal route pattern ^/api/v1/foundation/internal/jobs/[^/]+$"
        Mutate = {
            param($Fixture)
            $Fixture.Nginx = Remove-TextBlock $Fixture.Nginx @'
        if ($uri ~ ^/api/v1/foundation/internal/jobs/[^/]+$) {
            return 404;
        }

'@
        }
    },
    [pscustomobject]@{
        Name = "missing-ingress-static-internal-deny"
        ExpectedMessage = "Ingress public-edge deny for source-discovered internal route /api/v1/foundation/internal/status"
        Mutate = {
            param($Fixture)
            $Fixture.Ingress = Remove-TextBlock $Fixture.Ingress @'
      location ^~ /api/v1/foundation/internal/ {
        return 404;
      }
'@
        }
    },
    [pscustomobject]@{
        Name = "missing-ingress-templated-internal-deny"
        ExpectedMessage = "Ingress public-edge deny for source-discovered internal route pattern ^/api/v1/foundation/internal/jobs/[^/]+$"
        Mutate = {
            param($Fixture)
            $Fixture.Ingress = Remove-TextBlock $Fixture.Ingress @'
      if ($uri ~ ^/api/v1/foundation/internal/jobs/[^/]+$) {
        return 404;
      }
'@
        }
    },
    [pscustomobject]@{
        Name = "nginx-route-prefix-upstream-mismatch"
        ExpectedMessage = "Nginx proxy_pass missing for /api/v1/foundation -> foundation_service"
        Mutate = {
            param($Fixture)
            $Fixture.Nginx = $Fixture.Nginx.Replace("proxy_pass http://foundation_service;", "proxy_pass http://tracking_service;")
        }
    },
    [pscustomobject]@{
        Name = "ingress-route-prefix-owner-mismatch"
        ExpectedMessage = "Kubernetes ingress first matching path [/api/v1/foundation(/.*)?] for route-map prefix /api/v1/foundation routes to tracking-service, expected foundation-service"
        Mutate = {
            param($Fixture)
            $Fixture.Ingress = $Fixture.Ingress.Replace("name: foundation-service", "name: tracking-service")
        }
    },
    [pscustomobject]@{
        Name = "tracking-legacy-route-map-route-reintroduced"
        ExpectedMessage = "/api/v1/track must remain an Nginx-only tombstone and must not be in route-map.json"
        Mutate = {
            param($Fixture)
            $Fixture.RouteMap = Add-RouteMapRoute $Fixture.RouteMap "/api/v1/track" "tracking-service" "tracking_service"
        }
    },
    [pscustomobject]@{
        Name = "tracking-legacy-ingress-route-reintroduced"
        ExpectedMessage = "Ingress must not route /api/v1/track"
        Mutate = {
            param($Fixture)
            $Fixture.Ingress = Replace-TextBlock $Fixture.Ingress @'
          - path: /api/v1/tracking(/.*)?
            pathType: ImplementationSpecific
            backend:
              service:
                name: tracking-service
                port:
                  number: 8080
'@ @'
          - path: /api/v1/track(/.*)?
            pathType: ImplementationSpecific
            backend:
              service:
                name: tracking-service
                port:
                  number: 8080
          - path: /api/v1/tracking(/.*)?
            pathType: ImplementationSpecific
            backend:
              service:
                name: tracking-service
                port:
                  number: 8080
'@
        }
    },
    [pscustomobject]@{
        Name = "nginx-tracking-limit-zone-drift"
        ExpectedMessage = "Nginx tracking_limit zone must remain 200r/s for /api/v1/tracking"
        Mutate = {
            param($Fixture)
            $Fixture.Nginx = Replace-TextBlock $Fixture.Nginx `
                'limit_req_zone $binary_remote_addr zone=tracking_limit:10m rate=200r/s;' `
                'limit_req_zone $binary_remote_addr zone=tracking_limit:10m rate=100r/s;'
        }
    },
    [pscustomobject]@{
        Name = "nginx-tracking-location-limit-drift"
        ExpectedMessage = "Nginx /api/v1/tracking must use tracking_limit burst=50 nodelay"
        Mutate = {
            param($Fixture)
            $Fixture.Nginx = Replace-TextBlock $Fixture.Nginx `
                'limit_req zone=tracking_limit burst=50 nodelay;' `
                'limit_req zone=tracking_limit burst=20 nodelay;'
        }
    },
    [pscustomobject]@{
        Name = "ingress-tracking-limit-drift"
        ExpectedMessage = "Kubernetes tracking ingress must declare reviewed ingress-nginx limit-rps 200"
        Mutate = {
            param($Fixture)
            $Fixture.Ingress = Replace-TextBlock $Fixture.Ingress `
                'nginx.ingress.kubernetes.io/limit-rps: "200"' `
                'nginx.ingress.kubernetes.io/limit-rps: "100"'
        }
    },
    [pscustomobject]@{
        Name = "missing-tracking-ingress-prefix-coverage"
        ExpectedMessage = "Kubernetes ingress path missing for route-map prefix /api/v1/tracking -> tracking-service"
        Mutate = {
            param($Fixture)
            $Fixture.Ingress = Remove-TextBlock $Fixture.Ingress @'
          - path: /api/v1/tracking(/.*)?
            pathType: ImplementationSpecific
            backend:
              service:
                name: tracking-service
                port:
                  number: 8080
'@
        }
    }
)

$selectedCases = @($allCases)
if ($CaseName.Count -gt 0) {
    $selectedCases = @($allCases | Where-Object { $CaseName -contains $_.Name })
    $missingCases = @($CaseName | Where-Object { $allCases.Name -notcontains $_ })
    if ($missingCases.Count -gt 0) {
        Fail "Unknown route-map validator fixture case(s): $($missingCases -join ', ')"
    }
}

$script:TempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("legent-route-map-validator-fixtures-" + [System.Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $script:TempRoot | Out-Null
try {
    Assert-RouteMapPasses "baseline-route-map-fixture"
    foreach ($case in $selectedCases) {
        Assert-RouteMapFails $case.Name $case.Mutate $case.ExpectedMessage
    }
} finally {
    if ($KeepFixtures) {
        Write-Host "Kept route-map validator fixtures at $script:TempRoot"
    } elseif (Test-Path $script:TempRoot) {
        Remove-Item -LiteralPath $script:TempRoot -Recurse -Force
    }
}

Write-Host "Route map validator fixture harness passed for $($selectedCases.Count) negative cases."

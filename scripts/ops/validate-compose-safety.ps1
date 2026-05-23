param(
    [string]$ComposeEnvFile = ".env.example",
    [string[]]$ComposeFile = @(),
    [switch]$SelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$script:PowerShellExecutable = (Get-Process -Id $PID).Path
if ([string]::IsNullOrWhiteSpace($script:PowerShellExecutable)) {
    $script:PowerShellExecutable = if ($PSVersionTable.PSEdition -eq "Core") { "pwsh" } else { "powershell" }
}

function Fail($Message) {
    Write-Error $Message
    exit 1
}

function Add-Failure([System.Collections.Generic.List[string]]$Failures, [string]$Message) {
    [void]$Failures.Add($Message)
}

function Test-ImageHasStableReference([string]$Image) {
    if ([string]::IsNullOrWhiteSpace($Image)) { return $true }
    if ($Image -match '@sha256:[a-fA-F0-9]{64}$') { return $true }

    $lastSlash = $Image.LastIndexOf("/")
    $lastColon = $Image.LastIndexOf(":")
    if ($lastColon -le $lastSlash) { return $false }

    $tag = $Image.Substring($lastColon + 1)
    return -not [string]::IsNullOrWhiteSpace($tag) -and $tag -ine "latest"
}

function Test-IsTruthy($Value) {
    if ($null -eq $Value) { return $false }
    if ($Value -is [bool]) { return [bool]$Value }

    $text = ([string]$Value).Trim()
    return $text -match '^(?i:true|yes|1)$'
}

function Normalize-RuntimePath([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) { return "" }
    return (($Path.Trim() -replace "\\", "/").TrimEnd("/")).ToLowerInvariant()
}

function Test-IsDockerSocketPath([string]$Path) {
    $normalized = Normalize-RuntimePath $Path
    if ([string]::IsNullOrWhiteSpace($normalized)) { return $false }

    return $normalized -in @(
        "/var/run/docker.sock",
        "/run/docker.sock",
        "/var/run/docker.sock.raw",
        "//./pipe/docker_engine"
    )
}

function Normalize-CapabilityName([string]$Capability) {
    if ([string]::IsNullOrWhiteSpace($Capability)) { return "" }
    $normalized = $Capability.Trim().ToUpperInvariant()
    if ($normalized.StartsWith("CAP_")) {
        $normalized = $normalized.Substring(4)
    }
    return $normalized
}

function Test-IsBroadCapabilityAddition([string]$Capability) {
    $normalized = Normalize-CapabilityName $Capability
    if ([string]::IsNullOrWhiteSpace($normalized)) { return $false }

    $broadCapabilities = @(
        "ALL",
        "AUDIT_CONTROL",
        "AUDIT_READ",
        "BLOCK_SUSPEND",
        "BPF",
        "CHECKPOINT_RESTORE",
        "DAC_READ_SEARCH",
        "IPC_LOCK",
        "IPC_OWNER",
        "LEASE",
        "LINUX_IMMUTABLE",
        "MAC_ADMIN",
        "MAC_OVERRIDE",
        "NET_ADMIN",
        "NET_BROADCAST",
        "NET_RAW",
        "PERFMON",
        "SYS_ADMIN",
        "SYS_BOOT",
        "SYS_MODULE",
        "SYS_NICE",
        "SYS_PACCT",
        "SYS_PTRACE",
        "SYS_RAWIO",
        "SYS_RESOURCE",
        "SYS_TIME",
        "SYS_TTY_CONFIG",
        "WAKE_ALARM"
    )

    return $broadCapabilities -contains $normalized
}

function Test-IsHostNamespaceMode($Value) {
    if ($null -eq $Value) { return $false }
    return (([string]$Value).Trim()) -ieq "host"
}

function Test-IsPracticalConfigMount($Volume) {
    $target = ""
    $source = ""
    if ($Volume.PSObject.Properties.Name -contains "target" -and $null -ne $Volume.target) {
        $target = [string]$Volume.target
    }
    if ($Volume.PSObject.Properties.Name -contains "source" -and $null -ne $Volume.source) {
        $source = [string]$Volume.source
    }

    $normalizedTarget = $target -replace "\\", "/"
    $normalizedSource = $source -replace "\\", "/"

    if ($normalizedTarget -match '^/etc/nginx/.*\.conf$') { return $true }
    if ($normalizedTarget -eq "/etc/nginx/proxy_params.conf") { return $true }
    if ($normalizedTarget -eq "/docker-entrypoint-initdb.d") { return $true }
    if ($normalizedTarget -match '/db/migration$') { return $true }

    if ($normalizedSource -match '/config/.*\.(conf|json|ya?ml|properties)$') { return $true }
    return $false
}

function Invoke-SelfTestValidator([string]$Name, [string]$ComposePath, [string]$EnvPath) {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & $script:PowerShellExecutable `
            -NoLogo `
            -NoProfile `
            -ExecutionPolicy Bypass `
            -File $PSCommandPath `
            -ComposeEnvFile $EnvPath `
            -ComposeFile $ComposePath 2>&1
        $exitCode = $LASTEXITCODE
        $global:LASTEXITCODE = 0
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    return [pscustomobject]@{
        Name = $Name
        ExitCode = $exitCode
        Output = (($output | Out-String).Trim())
    }
}

function Assert-SelfTestPasses([string]$Name, [string]$ComposePath, [string]$EnvPath) {
    $proc = Invoke-SelfTestValidator $Name $ComposePath $EnvPath
    if ($proc.ExitCode -ne 0) {
        Fail "Compose safety self-test '$Name' should pass. exitCode=$($proc.ExitCode) output=[$($proc.Output)]"
    }
}

function Assert-SelfTestFails([string]$Name, [string]$ComposePath, [string]$EnvPath, [string]$ExpectedMessage) {
    $proc = Invoke-SelfTestValidator $Name $ComposePath $EnvPath
    if ($proc.ExitCode -eq 0) {
        Fail "Compose safety self-test '$Name' should fail."
    }

    $combinedOutput = $proc.Output
    if ($combinedOutput -notmatch [regex]::Escape($ExpectedMessage)) {
        Fail "Compose safety self-test '$Name' did not include expected message '$ExpectedMessage'. output=[$combinedOutput]"
    }
}

function Assert-SelfTestFailsWithMessages([string]$Name, [string]$ComposePath, [string]$EnvPath, [string[]]$ExpectedMessages) {
    $proc = Invoke-SelfTestValidator $Name $ComposePath $EnvPath
    if ($proc.ExitCode -eq 0) {
        Fail "Compose safety self-test '$Name' should fail."
    }

    $combinedOutput = $proc.Output
    foreach ($expectedMessage in $ExpectedMessages) {
        if ($combinedOutput -notmatch [regex]::Escape($expectedMessage)) {
            Fail "Compose safety self-test '$Name' did not include expected message '$expectedMessage'. output=[$combinedOutput]"
        }
    }
}

function Invoke-SelfTest {
    $script:SelfTestTempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("legent-compose-safety-test-" + [System.Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Force -Path $script:SelfTestTempRoot | Out-Null
    try {
        $composeEnv = Join-Path $script:SelfTestTempRoot ".env.example"
        $nginxConf = Join-Path $script:SelfTestTempRoot "nginx.conf"
        Set-Content -Path $composeEnv -Value "COMPOSE_PROJECT_NAME=legent-compose-validator-test" -Encoding UTF8
        Set-Content -Path $nginxConf -Value "server { listen 80; }" -Encoding UTF8
        New-Item -ItemType Directory -Force -Path (Join-Path $script:SelfTestTempRoot "worker") | Out-Null

        $validCompose = Join-Path $script:SelfTestTempRoot "valid-compose.yml"
        @"
services:
  nginx:
    image: nginx:1.27-alpine
    volumes:
      - ./nginx.conf:/etc/nginx/conf.d/default.conf:ro
  worker:
    build: ./worker
"@ | Set-Content -Path $validCompose -Encoding UTF8
        Assert-SelfTestPasses "valid-compose" $validCompose $composeEnv

        $latestCompose = Join-Path $script:SelfTestTempRoot "latest-compose.yml"
        @"
services:
  nginx:
    image: nginx:latest
"@ | Set-Content -Path $latestCompose -Encoding UTF8
        Assert-SelfTestFails "latest-image" $latestCompose $composeEnv "mutable image reference 'nginx:latest'"

        $untaggedCompose = Join-Path $script:SelfTestTempRoot "untagged-compose.yml"
        @"
services:
  nginx:
    image: nginx
"@ | Set-Content -Path $untaggedCompose -Encoding UTF8
        Assert-SelfTestFails "untagged-image" $untaggedCompose $composeEnv "mutable image reference 'nginx'"

        $writableConfigCompose = Join-Path $script:SelfTestTempRoot "writable-config-compose.yml"
        @"
services:
  nginx:
    image: nginx:1.27-alpine
    volumes:
      - ./nginx.conf:/etc/nginx/conf.d/default.conf
"@ | Set-Content -Path $writableConfigCompose -Encoding UTF8
        Assert-SelfTestFails "writable-config-mount" $writableConfigCompose $composeEnv "must be read-only"

        $unsafeRuntimeCompose = Join-Path $script:SelfTestTempRoot "unsafe-runtime-compose.yml"
        @"
services:
  worker:
    image: busybox:1.36
    privileged: true
    network_mode: host
    pid: host
    ipc: host
    cap_add:
      - SYS_ADMIN
    volumes:
      - type: bind
        source: /var/run/docker.sock
        target: /var/run/docker.sock
"@ | Set-Content -Path $unsafeRuntimeCompose -Encoding UTF8
        Assert-SelfTestFailsWithMessages "unsafe-runtime-settings" `
            $unsafeRuntimeCompose `
            $composeEnv `
            @(
                "uses privileged container mode",
                "uses host network namespace",
                "uses host PID namespace",
                "uses host IPC namespace",
                "adds broad Linux capability 'SYS_ADMIN'",
                "binds the Docker socket"
            )
    } finally {
        if (Test-Path -LiteralPath $script:SelfTestTempRoot) {
            Remove-Item -LiteralPath $script:SelfTestTempRoot -Recurse -Force
        }
    }

    Write-Host "Compose safety validator self-test passed."
}

if ($SelfTest) {
    Invoke-SelfTest
    exit 0
}

if (-not (Test-Path -LiteralPath $ComposeEnvFile)) {
    Fail "Compose env file is required for reproducible safety validation: $ComposeEnvFile"
}

foreach ($file in $ComposeFile) {
    if (-not (Test-Path -LiteralPath $file)) {
        Fail "Compose file was not found: $file"
    }
}

$composeArgs = @("compose", "--env-file", $ComposeEnvFile)
foreach ($file in $ComposeFile) {
    $composeArgs += @("-f", $file)
}
$composeArgs += @("config", "--format", "json")

$stderrPath = [System.IO.Path]::GetTempFileName()
try {
    $composeOutput = & docker @composeArgs 2>$stderrPath
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        $stderr = Get-Content -LiteralPath $stderrPath -Raw -ErrorAction SilentlyContinue
        Fail "docker compose config failed before safety validation. stderr=[$stderr]"
    }

    $configJson = ($composeOutput -join "`n")
    if ([string]::IsNullOrWhiteSpace($configJson)) {
        Fail "docker compose config produced empty JSON output."
    }

    $config = $configJson | ConvertFrom-Json
} finally {
    if (Test-Path -LiteralPath $stderrPath) {
        Remove-Item -LiteralPath $stderrPath -Force
    }
}

if (-not ($config.PSObject.Properties.Name -contains "services")) {
    Fail "Compose configuration does not contain services."
}

$failures = [System.Collections.Generic.List[string]]::new()
foreach ($serviceProperty in $config.services.PSObject.Properties) {
    $serviceName = $serviceProperty.Name
    $service = $serviceProperty.Value

    if ($service.PSObject.Properties.Name -contains "image" -and $null -ne $service.image) {
        $image = [string]$service.image
        if (-not (Test-ImageHasStableReference $image)) {
            Add-Failure $failures "service '$serviceName' uses mutable image reference '$image'; use an explicit non-latest tag or sha256 digest."
        }
    }

    if ($service.PSObject.Properties.Name -contains "privileged" -and (Test-IsTruthy $service.privileged)) {
        Add-Failure $failures "service '$serviceName' uses privileged container mode; keep local Compose services unprivileged."
    }

    if ($service.PSObject.Properties.Name -contains "network_mode" -and (Test-IsHostNamespaceMode $service.network_mode)) {
        Add-Failure $failures "service '$serviceName' uses host network namespace; use Compose-managed bridge networking instead."
    }

    if ($service.PSObject.Properties.Name -contains "pid" -and (Test-IsHostNamespaceMode $service.pid)) {
        Add-Failure $failures "service '$serviceName' uses host PID namespace; keep process isolation enabled."
    }

    if ($service.PSObject.Properties.Name -contains "ipc" -and (Test-IsHostNamespaceMode $service.ipc)) {
        Add-Failure $failures "service '$serviceName' uses host IPC namespace; keep IPC isolation enabled."
    }

    if ($service.PSObject.Properties.Name -contains "cap_add" -and $null -ne $service.cap_add) {
        foreach ($capability in @($service.cap_add)) {
            if (Test-IsBroadCapabilityAddition ([string]$capability)) {
                $normalizedCapability = Normalize-CapabilityName ([string]$capability)
                Add-Failure $failures "service '$serviceName' adds broad Linux capability '$normalizedCapability'; remove cap_add or document a narrower runtime design outside local Compose."
            }
        }
    }

    if (-not ($service.PSObject.Properties.Name -contains "volumes") -or $null -eq $service.volumes) {
        continue
    }

    foreach ($volume in @($service.volumes)) {
        if (-not ($volume.PSObject.Properties.Name -contains "type") -or $volume.type -ne "bind") {
            continue
        }

        if ((Test-IsDockerSocketPath ([string]$volume.source)) -or (Test-IsDockerSocketPath ([string]$volume.target))) {
            Add-Failure $failures "service '$serviceName' binds the Docker socket '$($volume.source)' -> '$($volume.target)'; do not grant containers Docker host control."
        }

        if (-not (Test-IsPracticalConfigMount $volume)) {
            continue
        }

        $readOnly = $false
        if ($volume.PSObject.Properties.Name -contains "read_only" -and $null -ne $volume.read_only) {
            $readOnly = [bool]$volume.read_only
        }

        if (-not $readOnly) {
            Add-Failure $failures "service '$serviceName' bind mount '$($volume.source)' -> '$($volume.target)' is configuration input and must be read-only."
        }
    }
}

if ($failures.Count -gt 0) {
    $message = "Compose safety validation failed:`n - " + ($failures -join "`n - ")
    Fail $message
}

Write-Host "Compose safety validation passed."

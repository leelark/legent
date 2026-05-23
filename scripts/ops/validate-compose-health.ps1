param(
    [switch]$AllowNotRunning,
    [string]$ComposeEnvFile,
    [string[]]$ComposeFile = @(),
    [switch]$SelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail($Message) {
    Write-Error $Message
    exit 1
}

function New-ComposePsArguments {
    param(
        [string]$EnvFile,
        [string[]]$Files = @()
    )

    $arguments = @("compose")
    if (-not [string]::IsNullOrWhiteSpace($EnvFile)) {
        $arguments += @("--env-file", $EnvFile)
    }
    foreach ($file in @($Files)) {
        if (-not [string]::IsNullOrWhiteSpace($file)) {
            $arguments += @("-f", $file)
        }
    }
    $arguments += @("ps", "--format", "json")
    return $arguments
}

function Convert-ComposePsOutput {
    param(
        [AllowNull()]
        [object]$Output
    )

    $lines = @(@($Output) | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) })
    if ($lines.Count -eq 0) {
        return @()
    }

    $joined = ($lines -join "`n").Trim()
    if ($joined.StartsWith("[")) {
        return @($joined | ConvertFrom-Json)
    }

    $rows = @()
    foreach ($line in $lines) {
        $rows += ($line | ConvertFrom-Json)
    }
    return $rows
}

function Get-UnhealthyComposeRows {
    param(
        [object[]]$Rows
    )

    $bad = @()
    foreach ($row in @($Rows)) {
        $state = [string]$row.State
        $health = ""
        if ($row.PSObject.Properties.Name -contains "Health") { $health = [string]$row.Health }
        if ($state -notmatch "running" -or ($health -and $health -notmatch "healthy|starting")) {
            $bad += "$($row.Service): state=$state health=$health"
        }
    }
    return $bad
}

function Invoke-ComposeHealthSelfTest {
    $expectedArgs = @("compose", "--env-file", ".env.example", "-f", "docker-compose.yml", "-f", "docker-compose.override.yml", "ps", "--format", "json")
    $actualArgs = New-ComposePsArguments -EnvFile ".env.example" -Files @("docker-compose.yml", "docker-compose.override.yml")
    if (($actualArgs -join "|") -ne ($expectedArgs -join "|")) {
        throw "env-file/compose-file argument wiring failed. Actual: $($actualArgs -join ' ')"
    }

    $noServices = @(Convert-ComposePsOutput -Output @())
    if ($noServices.Count -ne 0) {
        throw "no-services fixture should parse as zero rows."
    }

    $healthyLines = @(
        '{"Service":"postgres","State":"running","Health":"healthy"}',
        '{"Service":"kafka","State":"running","Health":"starting"}',
        '{"Service":"nginx","State":"running"}'
    )
    $healthyRows = @(Convert-ComposePsOutput -Output $healthyLines)
    $healthyFailures = @(Get-UnhealthyComposeRows -Rows $healthyRows)
    if ($healthyRows.Count -ne 3 -or $healthyFailures.Count -ne 0) {
        throw "healthy/starting fixture should pass."
    }

    $jsonArrayRows = @(Convert-ComposePsOutput -Output '[{"Service":"redis","State":"running","Health":"healthy"}]')
    if ($jsonArrayRows.Count -ne 1 -or $jsonArrayRows[0].Service -ne "redis") {
        throw "JSON array fixture should parse as one redis row."
    }

    $unhealthyRows = @(Convert-ComposePsOutput -Output '{"Service":"api","State":"exited","Health":"unhealthy"}')
    $unhealthyFailures = @(Get-UnhealthyComposeRows -Rows $unhealthyRows)
    if ($unhealthyFailures.Count -ne 1 -or $unhealthyFailures[0] -notmatch "api: state=exited health=unhealthy") {
        throw "unhealthy fixture should fail with service details."
    }

    Write-Host "Compose health validator self-test passed."
}

if ($SelfTest) {
    Invoke-ComposeHealthSelfTest
    exit 0
}

$composeArgs = New-ComposePsArguments -EnvFile $ComposeEnvFile -Files $ComposeFile
$previousErrorActionPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = "Continue"
    $output = & docker @composeArgs 2>&1
    $dockerExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previousErrorActionPreference
}

if ($dockerExitCode -ne 0) {
    if ($AllowNotRunning) {
        Write-Warning "docker $($composeArgs -join ' ') failed, but AllowNotRunning was set."
        exit 0
    }
    Fail "docker $($composeArgs -join ' ') failed."
}

$rows = @(Convert-ComposePsOutput -Output $output)
if ($rows.Count -eq 0) {
    if ($AllowNotRunning) {
        Write-Warning "No compose services are running."
        exit 0
    }
    Fail "No compose services are running."
}

$bad = @(Get-UnhealthyComposeRows -Rows $rows)
if ($bad.Count -gt 0) {
    $bad | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Host "Compose health validation passed for $($rows.Count) services."

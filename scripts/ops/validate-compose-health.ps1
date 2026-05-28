param(
    [switch]$AllowNotRunning,
    [string]$ComposeEnvFile,
    [string[]]$ComposeFile = @(),
    [int]$WaitSeconds = 180,
    [int]$PollSeconds = 5,
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
    $arguments += @("ps", "-a", "--format", "json")
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

    $completedSetupServices = @("postgres-init", "kafka-setup")
    $bad = @()
    foreach ($row in @($Rows)) {
        $service = [string]$row.Service
        $state = [string]$row.State
        $health = ""
        if ($row.PSObject.Properties.Name -contains "Health") { $health = [string]$row.Health }
        $exitCode = $null
        if ($row.PSObject.Properties.Name -contains "ExitCode") { $exitCode = [int]$row.ExitCode }
        if ($completedSetupServices -contains $service -and $state -eq "exited" -and $exitCode -eq 0) {
            continue
        }
        if ($state -ne "running" -or ($health -and $health -ne "healthy")) {
            $bad += "$($row.Service): state=$state health=$health"
        }
    }
    return $bad
}

function Invoke-DockerComposePs {
    param(
        [string[]]$ComposeArgs
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & docker @ComposeArgs 2>&1
        return [pscustomobject]@{
            ExitCode = $LASTEXITCODE
            Output = $output
        }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

function Invoke-ComposeHealthSelfTest {
    $expectedArgs = @("compose", "--env-file", ".env.example", "-f", "docker-compose.yml", "-f", "docker-compose.override.yml", "ps", "-a", "--format", "json")
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
        '{"Service":"nginx","State":"running"}'
    )
    $healthyRows = @(Convert-ComposePsOutput -Output $healthyLines)
    $healthyFailures = @(Get-UnhealthyComposeRows -Rows $healthyRows)
    if ($healthyRows.Count -ne 2 -or $healthyFailures.Count -ne 0) {
        throw "healthy/no-health fixture should pass."
    }

    $startingRows = @(Convert-ComposePsOutput -Output '{"Service":"kafka","State":"running","Health":"starting"}')
    $startingFailures = @(Get-UnhealthyComposeRows -Rows $startingRows)
    if ($startingFailures.Count -ne 1 -or $startingFailures[0] -notmatch "kafka: state=running health=starting") {
        throw "starting fixture should fail with service details."
    }

    $completedSetupRows = @(Convert-ComposePsOutput -Output @(
        '{"Service":"postgres-init","State":"exited","ExitCode":0,"Health":""}',
        '{"Service":"kafka-setup","State":"exited","ExitCode":0,"Health":""}'
    ))
    $completedSetupFailures = @(Get-UnhealthyComposeRows -Rows $completedSetupRows)
    if ($completedSetupFailures.Count -ne 0) {
        throw "successful setup jobs should pass."
    }

    $unexpectedExitedRows = @(Convert-ComposePsOutput -Output '{"Service":"api","State":"exited","ExitCode":0,"Health":""}')
    $unexpectedExitedFailures = @(Get-UnhealthyComposeRows -Rows $unexpectedExitedRows)
    if ($unexpectedExitedFailures.Count -ne 1 -or $unexpectedExitedFailures[0] -notmatch "api: state=exited health=") {
        throw "unexpected exited service should fail."
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
$deadline = (Get-Date).AddSeconds([Math]::Max(0, $WaitSeconds))
$sleepSeconds = [Math]::Max(1, $PollSeconds)
$lastRows = @()
$lastBad = @()

do {
    $result = Invoke-DockerComposePs -ComposeArgs $composeArgs
    if ($result.ExitCode -ne 0) {
        if ($AllowNotRunning) {
            Write-Warning "docker $($composeArgs -join ' ') failed, but AllowNotRunning was set."
            exit 0
        }
        Fail "docker $($composeArgs -join ' ') failed."
    }

    $lastRows = @(Convert-ComposePsOutput -Output $result.Output)
    if ($lastRows.Count -eq 0) {
        if ($AllowNotRunning) {
            Write-Warning "No compose services are running."
            exit 0
        }
        $lastBad = @("No compose services are present.")
    } else {
        $lastBad = @(Get-UnhealthyComposeRows -Rows $lastRows)
        if ($lastBad.Count -eq 0) {
            Write-Host "Compose health validation passed for $($lastRows.Count) services."
            exit 0
        }
    }

    if ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds $sleepSeconds
    }
} while ((Get-Date) -lt $deadline)

$lastBad | ForEach-Object { Write-Error $_ }
exit 1

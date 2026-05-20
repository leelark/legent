Set-StrictMode -Version Latest

$script:CodexStateMutexName = $null

function Get-CodexStateMutexName {
    if ($script:CodexStateMutexName) { return $script:CodexStateMutexName }
    $root = (Get-Location).Path.ToLowerInvariant()
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($root)
    $hash = [System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes)
    $suffix = [System.BitConverter]::ToString($hash).Replace("-", "").Substring(0, 16)
    $script:CodexStateMutexName = "Global\LegentCodexState-$suffix"
    return $script:CodexStateMutexName
}

function Invoke-CodexStateMutation {
    param(
        [Parameter(Mandatory = $true)][scriptblock]$ScriptBlock,
        [string]$Name = "codex-state",
        [int]$TimeoutSeconds = 60
    )

    $mutex = [System.Threading.Mutex]::new($false, (Get-CodexStateMutexName))
    $acquired = $false
    try {
        $acquired = $mutex.WaitOne([TimeSpan]::FromSeconds($TimeoutSeconds))
        if (-not $acquired) {
            throw "Timed out waiting for Codex state lock during $Name."
        }
        & $ScriptBlock
    } finally {
        if ($acquired) { $mutex.ReleaseMutex() | Out-Null }
        $mutex.Dispose()
    }
}

function Resolve-CodexPath {
    param([Parameter(Mandatory = $true)][string]$Path)
    return $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Path)
}

function Read-CodexJsonFile {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path $Path)) { throw "Missing JSON file: $Path" }
    return Get-Content -Path $Path -Raw | ConvertFrom-Json
}

function Write-CodexJsonFile {
    param(
        [Parameter(Mandatory = $true)]$Object,
        [Parameter(Mandatory = $true)][string]$Path,
        [int]$Depth = 20
    )

    $resolved = Resolve-CodexPath $Path
    $dir = Split-Path -Parent $resolved
    if ($dir) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }

    $temp = "$resolved.$([System.Guid]::NewGuid().ToString('N')).tmp"
    $backup = "$resolved.$([System.Guid]::NewGuid().ToString('N')).bak"
    $encoding = [System.Text.UTF8Encoding]::new($false)
    $json = $Object | ConvertTo-Json -Depth $Depth

    try {
        [System.IO.File]::WriteAllText($temp, $json + [Environment]::NewLine, $encoding)
        if (Test-Path $resolved) {
            [System.IO.File]::Replace($temp, $resolved, $backup, $true)
            if (Test-Path $backup) { Remove-Item -LiteralPath $backup -Force }
        } else {
            [System.IO.File]::Move($temp, $resolved)
        }
    } finally {
        if (Test-Path $temp) { Remove-Item -LiteralPath $temp -Force }
    }
}

function Add-CodexJsonLine {
    param(
        [Parameter(Mandatory = $true)]$Object,
        [Parameter(Mandatory = $true)][string]$Path,
        [int]$Depth = 8
    )

    $resolved = Resolve-CodexPath $Path
    $dir = Split-Path -Parent $resolved
    if ($dir) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    $encoding = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::AppendAllText($resolved, ($Object | ConvertTo-Json -Compress -Depth $Depth) + [Environment]::NewLine, $encoding)
}

function Set-CodexJsonProperty {
    param($Object, [Parameter(Mandatory = $true)][string]$Name, $Value)
    if ($null -eq $Object.PSObject.Properties[$Name]) {
        $Object | Add-Member -NotePropertyName $Name -NotePropertyValue $Value
    } else {
        $Object.$Name = $Value
    }
}

function Get-CodexJsonArray {
    param($Object, [Parameter(Mandatory = $true)][string]$Name)
    if ($null -eq $Object.PSObject.Properties[$Name] -or $null -eq $Object.$Name) {
        return @()
    }
    return @($Object.$Name | Where-Object { $null -ne $_ })
}

function Get-CodexJsonProperty {
    param($Object, [Parameter(Mandatory = $true)][string]$Name, $Default = $null)
    if ($null -eq $Object -or $null -eq $Object.PSObject.Properties[$Name]) {
        return $Default
    }
    return $Object.$Name
}

function Normalize-CodexStringArray {
    param([string[]]$Values)
    $normalized = @()
    foreach ($value in @($Values)) {
        if ($null -eq $value) { continue }
        foreach ($part in ($value -split ",")) {
            $trimmed = $part.Trim()
            if ($trimmed) { $normalized += $trimmed }
        }
    }
    return $normalized
}

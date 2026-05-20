param(
    [Parameter(Mandatory = $true)][string]$ThreadId,
    [Parameter(Mandatory = $true)][string]$WorkItemId,
    [Parameter(Mandatory = $true)][string[]]$FilesInScope,
    [string]$AgentId = "",
    [string]$WorktreeId = "",
    [string]$Branch = "",
    [switch]$ReadOnly,
    [int]$TtlMinutes = 240
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "codex-state.ps1")

function Normalize-StringArray([string[]]$Values) {
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

function Set-JsonProperty($Object, [string]$Name, $Value) {
    if ($null -eq $Object.PSObject.Properties[$Name]) {
        $Object | Add-Member -NotePropertyName $Name -NotePropertyValue $Value
    } else {
        $Object.$Name = $Value
    }
}

function Get-JsonArray($Object, [string]$Name) {
    if ($null -eq $Object.PSObject.Properties[$Name] -or $null -eq $Object.$Name) {
        return @()
    }
    return @($Object.$Name | Where-Object { $null -ne $_ })
}

function Get-JsonProperty($Object, [string]$Name, $Default = $null) {
    if ($null -eq $Object -or $null -eq $Object.PSObject.Properties[$Name]) {
        return $Default
    }
    return $Object.$Name
}

function Write-JsonFile($Object, [string]$Path, [int]$Depth = 20) {
    Write-CodexJsonFile -Object $Object -Path $Path -Depth $Depth
}

function Normalize-Scope([string]$Scope) {
    return $Scope.Trim().TrimEnd("\", "/").Replace("/", "\").ToLowerInvariant().Replace("\**", "")
}

function Scopes-Overlap([string]$Left, [string]$Right) {
    $l = Normalize-Scope $Left
    $r = Normalize-Scope $Right
    if (-not $l -or -not $r) { return $false }
    if ($l -eq $r) { return $true }
    return ($l.StartsWith($r + "\") -or $r.StartsWith($l + "\"))
}

$result = Invoke-CodexStateMutation -Name "acquire-lease" -ScriptBlock {
    $threadPath = ".codex/threads/thread-registry.json"
    $leasePath = ".codex/worktrees/leases/active-leases.json"
    $threads = Get-Content -Path $threadPath -Raw | ConvertFrom-Json
    $leases = Get-Content -Path $leasePath -Raw | ConvertFrom-Json
    $thread = Get-JsonArray $threads "threads" | Where-Object { (Get-JsonProperty $_ "threadId") -eq $ThreadId } | Select-Object -First 1
    if (-not $thread) { Write-Error "Thread is not registered: $ThreadId"; exit 1 }
    $normalizedFiles = Normalize-StringArray $FilesInScope
    if (@($normalizedFiles).Count -eq 0) { Write-Error "FilesInScope must not be empty."; exit 1 }

    $now = (Get-Date).ToUniversalTime()
    foreach ($lease in @(Get-JsonArray $leases "leases" | Where-Object { (Get-JsonProperty $_ "status") -eq "ACTIVE" -or -not (Get-JsonProperty $_ "status") })) {
        if (-not (Get-JsonProperty $lease "leaseId")) { Write-Error "Existing active lease missing leaseId."; exit 1 }
        if (-not (Get-JsonProperty $lease "expiresAt")) { Write-Error "Existing active lease $($lease.leaseId) missing expiresAt."; exit 1 }
        $expiresAt = [datetime]::Parse([string]$lease.expiresAt).ToUniversalTime()
        if ($expiresAt -lt $now) { Write-Error "Existing active lease $($lease.leaseId) expired at $($lease.expiresAt)."; exit 1 }
        foreach ($scope in @($normalizedFiles)) {
            foreach ($existingScope in @(Get-JsonArray $lease "filesInScope")) {
                if (Scopes-Overlap ([string]$existingScope) ([string]$scope)) {
                    Write-Error "Refusing to persist overlapping lease for ${scope}: $($lease.leaseId) and requested $WorkItemId."
                    exit 1
                }
            }
        }
    }

    $leaseId = "$ThreadId-$WorkItemId-" + $now.ToString("yyyyMMddTHHmmssZ")
    $baseCommit = ""
    try { $baseCommit = (git rev-parse HEAD 2>$null) } catch { $baseCommit = "" }
    if (-not $baseCommit) { $baseCommit = "unknown" }

    $record = [ordered]@{
        leaseId = $leaseId
        threadId = $ThreadId
        workItemId = $WorkItemId
        agentId = $AgentId
        owner = Get-JsonProperty $thread "owner" "UNKNOWN_OWNER"
        worktreeId = $WorktreeId
        branch = $Branch
        filesInScope = @($normalizedFiles)
        writeGlobs = @($normalizedFiles)
        readOnly = [bool]$ReadOnly
        status = "ACTIVE"
        baseCommit = $baseCommit
        acquiredAt = $now.ToString("o")
        expiresAt = $now.AddMinutes($TtlMinutes).ToString("o")
        heartbeatAt = $now.ToString("o")
    }

    $leaseRecords = @(Get-JsonArray $leases "leases")
    $leaseRecords += $record
    Set-JsonProperty $leases "leases" $leaseRecords
    $threadLeaseIds = @(Get-JsonArray $thread "leaseIds")
    $threadLeaseIds += $leaseId
    Set-JsonProperty $thread "leaseIds" $threadLeaseIds
    Set-JsonProperty $thread "lastUpdated" $now.ToString("o")
    Set-JsonProperty $thread "heartbeatAt" $now.ToString("o")

    Write-JsonFile $leases $leasePath 12
    Write-JsonFile $threads $threadPath 12

    [pscustomobject]@{
        LeaseId = $leaseId
        Actor = (Get-JsonProperty $thread "owner" "UNKNOWN_OWNER")
        Module = (Get-JsonProperty $thread "module" "overall")
        Files = @($normalizedFiles)
    }
}

& .codex/utilities/validate-worktree-leases.ps1
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& .codex/utilities/write-audit-event.ps1 -EventType LEASE_ACQUIRED -Actor $result.Actor -ThreadId $ThreadId -WorkItemId $WorkItemId -Module $result.Module -Files @($result.Files) -Summary "Lease acquired for scoped module work." -NextAction "Proceed with checkpointed work."
Write-Host "Acquired lease $($result.LeaseId)"

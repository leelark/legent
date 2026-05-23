param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail($Message) {
    Write-Error $Message
    exit 1
}

function Write-JsonFile($Object, [string]$Path, [int]$Depth = 20) {
    $json = $Object | ConvertTo-Json -Depth $Depth
    $encoding = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText((Resolve-Path -LiteralPath (Split-Path -Parent $Path)).Path + "\" + (Split-Path -Leaf $Path), $json + [Environment]::NewLine, $encoding)
}

function Assert-UppercaseAuditEventTypes([string[]]$Lines, [string]$Source) {
    $lineNumber = 0
    foreach ($line in @($Lines)) {
        $lineNumber++
        if ([string]::IsNullOrWhiteSpace($line)) { continue }

        try {
            $event = $line | ConvertFrom-Json
        } catch {
            throw "Invalid audit JSON in ${Source}:$($lineNumber): $($_.Exception.Message)"
        }

        if ($event.PSObject.Properties.Name -notcontains "eventType") {
            throw "Audit event missing eventType in ${Source}:$($lineNumber)."
        }

        $eventType = [string]$event.eventType
        if ([string]::IsNullOrWhiteSpace($eventType) -or $eventType -cnotmatch "^[A-Z][A-Z0-9_]*$") {
            throw "Audit event type must be uppercase snake-case in ${Source}:$($lineNumber): $eventType"
        }
    }
}

$sourceRoot = (Get-Location).Path
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("legent-codex-state-" + [System.Guid]::NewGuid().ToString("N"))

try {
    New-Item -ItemType Directory -Force -Path (Join-Path $tempRoot ".codex/utilities") | Out-Null
    foreach ($dir in @(".codex/backlog", ".codex/worktrees/leases", ".codex/threads", ".codex/checkpoints", ".codex/audit/events")) {
        New-Item -ItemType Directory -Force -Path (Join-Path $tempRoot $dir) | Out-Null
    }
    Copy-Item -LiteralPath (Join-Path $sourceRoot ".codex/utilities/codex-state.ps1") -Destination (Join-Path $tempRoot ".codex/utilities/codex-state.ps1")
    foreach ($script in @("promote-backlog-item.ps1", "start-work-item.ps1", "acquire-lease.ps1", "new-checkpoint.ps1", "update-checkpoint.ps1", "complete-work-item.ps1", "write-audit-event.ps1", "validate-worktree-leases.ps1")) {
        Copy-Item -LiteralPath (Join-Path $sourceRoot ".codex/utilities/$script") -Destination (Join-Path $tempRoot ".codex/utilities/$script")
    }

    $queue = [ordered]@{
        schemaVersion = 1
        freshBaselineDate = "2026-05-20"
        lastUpdated = "2026-05-20"
        readyWork = @()
        backlogWork = @([ordered]@{
            id = "smoke-item"
            title = "Smoke lifecycle item"
            status = "BACKLOG"
            priorityScore = 1
            owner = "PROGRAM_MANAGER_AGENT"
            scope = "Exercise lifecycle state utilities in a temp copy."
            acceptanceCriteria = @("Promote, start, checkpoint update, audit, overlap rejection, and complete succeed.")
            validationProfile = "docs-memory"
            validationCommands = @("smoke-validation")
            partnerAgents = @("TEST_ARCHITECT")
            blockers = @()
            memoryTargets = @()
            nextAction = "Run smoke lifecycle."
            lastUpdated = "2026-05-20"
        })
        inProgressWork = @()
        blockedWork = @()
        doneWork = @()
    }
    $leases = [ordered]@{
        schemaVersion = 1
        freshBaselineDate = "2026-05-20"
        leases = @()
    }
    $threads = [ordered]@{
        schemaVersion = 1
        coordinatorThreadId = "overall-24x7"
        threads = @([ordered]@{
            threadId = "overall-24x7"
            threadRole = "OVERALL"
            status = "ACTIVE"
            module = "overall"
            owner = "PROGRAM_MANAGER_AGENT"
            leaseIds = @()
            maxParallelAgents = 6
            activeAgents = @()
            heartbeatAt = (Get-Date).ToUniversalTime().ToString("o")
            startedAt = (Get-Date).ToUniversalTime().ToString("o")
            lastUpdated = (Get-Date).ToUniversalTime().ToString("o")
            nextAction = "Run smoke lifecycle."
        })
    }

    Write-JsonFile $queue (Join-Path $tempRoot ".codex/backlog/queue.json")
    Write-JsonFile $leases (Join-Path $tempRoot ".codex/worktrees/leases/active-leases.json")
    Write-JsonFile $threads (Join-Path $tempRoot ".codex/threads/thread-registry.json")

    Push-Location $tempRoot
    try {
        & .codex/utilities/promote-backlog-item.ps1 -WorkItemId smoke-item
        & .codex/utilities/start-work-item.ps1 -ThreadId overall-24x7 -WorkItemId smoke-item -FilesInScope @("smoke/scope.txt")

        $checkpoint = Get-ChildItem ".codex/checkpoints" -Filter "*smoke-item.json" | Select-Object -First 1
        if (-not $checkpoint) { Fail "Smoke checkpoint was not created." }
        & .codex/utilities/update-checkpoint.ps1 -Path $checkpoint.FullName -Status VALIDATING -NextAction "Complete smoke lifecycle."

        $overlapOut = Join-Path $tempRoot "overlap.stdout.txt"
        $overlapErr = Join-Path $tempRoot "overlap.stderr.txt"
        $overlapProcess = Start-Process -FilePath "powershell" -ArgumentList @(
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            ".codex/utilities/acquire-lease.ps1",
            "-ThreadId",
            "overall-24x7",
            "-WorkItemId",
            "smoke-overlap",
            "-FilesInScope",
            "smoke/scope.txt"
        ) -Wait -PassThru -WindowStyle Hidden -RedirectStandardOutput $overlapOut -RedirectStandardError $overlapErr
        if ($overlapProcess.ExitCode -eq 0) { Fail "Overlapping lease acquisition unexpectedly succeeded." }

        $afterRejectedOverlap = Get-Content ".codex/worktrees/leases/active-leases.json" -Raw | ConvertFrom-Json
        $activeAfterRejectedOverlap = @($afterRejectedOverlap.leases | Where-Object { -not $_.status -or $_.status -eq "ACTIVE" })
        if (@($activeAfterRejectedOverlap).Count -ne 1) { Fail "Rejected overlap changed active lease count." }

        & .codex/utilities/complete-work-item.ps1 -WorkItemId smoke-item -Status DONE -Summary "Smoke lifecycle completed." -ValidationRun @("smoke-validation") -ResidualRisks @() -NextAction "None."
        & .codex/utilities/validate-worktree-leases.ps1

        $finalQueue = Get-Content ".codex/backlog/queue.json" -Raw | ConvertFrom-Json
        $done = @($finalQueue.doneWork | Where-Object { $_.id -eq "smoke-item" })
        if (@($done).Count -ne 1) { Fail "Smoke item was not completed." }
        if (@($finalQueue.inProgressWork).Count -ne 0) { Fail "Smoke item remained in progress." }

        $finalLeases = Get-Content ".codex/worktrees/leases/active-leases.json" -Raw | ConvertFrom-Json
        $activeFinal = @($finalLeases.leases | Where-Object { -not $_.status -or $_.status -eq "ACTIVE" })
        if (@($activeFinal).Count -ne 0) { Fail "Smoke leases were not released." }

        $auditLines = Get-ChildItem ".codex/audit/events" -Filter "*.jsonl" | Get-Content
        if (@($auditLines).Count -lt 3) { Fail "Smoke audit events were not written." }
        try {
            Assert-UppercaseAuditEventTypes -Lines @($auditLines) -Source "smoke audit events"
        } catch {
            Fail $_.Exception.Message
        }

        $mixedCaseAuditLine = '{"timestamp":"2026-05-20T00:00:00Z","eventType":"coordination","actor":"PROGRAM_MANAGER_AGENT","module":"overall","summary":"Mixed-case fixture."}'
        $mixedCaseRejected = $false
        try {
            Assert-UppercaseAuditEventTypes -Lines @($mixedCaseAuditLine) -Source "mixed-case audit fixture"
        } catch {
            $mixedCaseRejected = $true
        }
        if (-not $mixedCaseRejected) { Fail "Mixed-case audit event type was not rejected." }
    } finally {
        Pop-Location
    }
} finally {
    if (Test-Path $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}

Write-Host "Codex lifecycle state smoke passed."

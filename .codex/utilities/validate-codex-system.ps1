param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail($Message) {
    Write-Error $Message
    exit 1
}

function Read-Json($Path) {
    if (-not (Test-Path $Path)) { Fail "Missing required JSON: $Path" }
    try {
        return Get-Content -Path $Path -Raw | ConvertFrom-Json
    } catch {
        Fail "Invalid JSON in ${Path}: $($_.Exception.Message)"
    }
}

$requiredFiles = @(
    "AGENTS.md",
    "ARCHITECTURE.md",
    "PROJECT_CONTEXT.md",
    ".codex/bootstrap.md",
    ".codex/agents/agent-catalog.md",
    ".codex/agents/routing-matrix.md",
    ".codex/commands/continuous-cycle.md",
    ".codex/commands/full-audit.md",
    ".codex/commands/pending-scan.md",
    ".codex/commands/refine-backlog.md",
    ".codex/commands/research-pass.md",
    ".codex/state/team-state.json",
    ".codex/backlog/queue.json",
    ".codex/threads/thread-registry.json",
    ".codex/teams/module-team-registry.json",
    ".codex/worktrees/worktree-registry.json",
    ".codex/worktrees/leases/active-leases.json",
    ".codex/utilities/utility-registry.json",
    ".codex/schemas/work-item.schema.json",
    ".codex/schemas/active-lease.schema.json",
    ".codex/schemas/coordination-event.schema.json",
    ".codex/schemas/agent-handoff.schema.json",
    ".codex/prompts/overall-24x7.md",
    ".codex/prompts/multi-module-coordinator-24x7.md",
    ".codex/prompts/module-backend-service-24x7.md",
    ".codex/prompts/module-frontend-24x7.md",
    ".codex/prompts/module-infra-24x7.md",
    ".codex/prompts/module-docs-codex-24x7.md"
)

foreach ($file in $requiredFiles) {
    if (-not (Test-Path $file)) { Fail "Missing required Codex system file: $file" }
}

$requiredMemory = @(
    "architecture-memory.md",
    "repo-map.md",
    "service-dependencies.md",
    "bug-history.md",
    "root-cause-history.md",
    "failed-fixes.md",
    "successful-fixes.md",
    "performance-bottlenecks.md",
    "security-findings.md",
    "technical-debt.md",
    "design-decisions.md",
    "release-history.md",
    "active-work-items.md",
    "blocked-items.md",
    "unresolved-risks.md",
    "fresh-start.md"
)

foreach ($file in $requiredMemory) {
    $path = Join-Path ".codex/memory" $file
    if (-not (Test-Path $path)) { Fail "Missing required memory file: $path" }
}

$stalePattern = "Historical summary retained|Resolved summary retained|Audience V17|Double opt-in|older completed work|previous memory item"
$memoryHits = rg -n $stalePattern .codex/memory 2>$null
if ($LASTEXITCODE -eq 0 -and $memoryHits) {
    Write-Error $memoryHits
    Fail "Fresh memory validation failed: stale memory markers found."
}
$global:LASTEXITCODE = 0

$state = Read-Json ".codex/state/team-state.json"
if ([int]$state.maxParallelAgents -gt 6) { Fail "maxParallelAgents must not exceed 6." }
if (@($state.activeAgents).Count -gt 6) { Fail "activeAgents exceeds 6." }

$queue = Read-Json ".codex/backlog/queue.json"
$validationRegistry = Read-Json ".codex/state/validation-registry.json"
$knownProfiles = @($validationRegistry.profiles | ForEach-Object { $_.id })
$allowedStatuses = @("BACKLOG", "READY", "IN_PROGRESS", "BLOCKED", "REVIEW", "VALIDATING", "DONE", "WONT_DO")
function Get-QueueBucket($Queue, [string]$Name) {
    if ($Queue.PSObject.Properties.Name -contains $Name) { return @($Queue.$Name) }
    return @()
}
$allItems = @(Get-QueueBucket $queue "readyWork") + @(Get-QueueBucket $queue "backlogWork") + @(Get-QueueBucket $queue "inProgressWork") + @(Get-QueueBucket $queue "blockedWork") + @(Get-QueueBucket $queue "doneWork")
$seenItemIds = @{}
foreach ($item in $allItems) {
    if (-not $item.id) { Fail "Backlog item missing id." }
    if ($seenItemIds.ContainsKey([string]$item.id)) { Fail "Duplicate work item id: $($item.id)" }
    $seenItemIds[[string]$item.id] = $true
    if ($allowedStatuses -notcontains [string]$item.status) { Fail "Invalid status for backlog item $($item.id): $($item.status)" }
    foreach ($field in @("title", "owner", "scope", "nextAction", "validationProfile", "lastUpdated")) {
        if (-not [string]$item.$field) { Fail "Backlog item $($item.id) missing $field." }
    }
    if ($null -eq $item.priorityScore) { Fail "Backlog item $($item.id) missing priorityScore." }
    if ($item.scoring) {
        $expectedScore = ([int]$item.scoring.productionReadinessImpact * 5) + ([int]$item.scoring.securityRisk * 4) + ([int]$item.scoring.userImpact * 3) + ([int]$item.scoring.performanceImpact * 2) + [int]$item.scoring.technicalDebtImpact
        if ([int]$item.priorityScore -ne $expectedScore) { Fail "Backlog item $($item.id) priorityScore $($item.priorityScore) does not match formula $expectedScore." }
    }
    if ($knownProfiles -notcontains [string]$item.validationProfile) { Fail "Backlog item $($item.id) uses unknown validationProfile: $($item.validationProfile)" }
    if (@($item.acceptanceCriteria).Count -eq 0) { Fail "Backlog item $($item.id) needs acceptanceCriteria." }
    foreach ($memoryTarget in @($item.memoryTargets)) {
        if ($memoryTarget -and -not (Test-Path (Join-Path ".codex/memory" $memoryTarget))) {
            Fail "Backlog item $($item.id) references missing memory target: $memoryTarget"
        }
    }
}

function Get-IdSet($Items) {
    $set = @{}
    foreach ($item in @($Items)) {
        if ($item -and $item.id) {
            $set[[string]$item.id] = $true
        }
    }
    return $set
}

function Assert-MatchingIdSet([string]$Name, $ExpectedItems, $ActualItems) {
    $expected = Get-IdSet $ExpectedItems
    $actual = Get-IdSet $ActualItems
    foreach ($id in $expected.Keys) {
        if (-not $actual.ContainsKey($id)) { Fail "team-state $Name missing queue item: $id" }
    }
    foreach ($id in $actual.Keys) {
        if (-not $expected.ContainsKey($id)) { Fail "team-state $Name contains stale or unexpected item: $id" }
    }
}

Assert-MatchingIdSet "readyWork" (Get-QueueBucket $queue "readyWork") @($state.readyWork)
Assert-MatchingIdSet "backlogWork" (Get-QueueBucket $queue "backlogWork") @($state.backlogWork)
Assert-MatchingIdSet "activeWork" (Get-QueueBucket $queue "inProgressWork") @($state.activeWork)

$registry = Read-Json ".codex/worktrees/worktree-registry.json"
$leases = Read-Json ".codex/worktrees/leases/active-leases.json"
if ($null -eq $registry.activeWorktrees) { Fail "worktree registry missing activeWorktrees." }
if ($null -eq $leases.leases) { Fail "active leases missing leases array." }

& .codex/utilities/validate-worktree-leases.ps1
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$global:LASTEXITCODE = 0

& .codex/utilities/validate-thread-coordination.ps1
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$global:LASTEXITCODE = 0

& .codex/utilities/reconcile-worktrees.ps1
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$global:LASTEXITCODE = 0

& .codex/utilities/monitor-autonomous-org.ps1 -CheckOnly
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$global:LASTEXITCODE = 0

$checkpointFiles = Get-ChildItem ".codex/checkpoints" -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -ne "checkpoint-template.md" }
foreach ($checkpoint in $checkpointFiles) {
    if ($checkpoint.Extension -eq ".json") {
        $cp = Read-Json $checkpoint.FullName
        foreach ($field in @("id", "createdAt", "objective", "status", "owner", "filesInScope", "validationPlan", "nextAction")) {
            if ($null -eq $cp.$field) { Fail "Checkpoint $($checkpoint.Name) missing $field." }
        }
    }
}

Write-Host "Codex autonomous system validation passed."

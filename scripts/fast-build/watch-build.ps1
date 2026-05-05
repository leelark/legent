#!/usr/bin/env pwsh
# Legent: Watch Mode - Auto-rebuild on file changes
# Monitors source files and triggers fast rebuild automatically
# Usage: .\scripts\fast-build\watch-build.ps1 [-Service <name>] [-LogFile <path>]

param(
    [string]$Service,
    [int]$DebounceSeconds = 5,
    [string[]]$WatchPaths = @("services/*/src", "shared/*/src", "frontend/src"),
    [string]$LogFile = "$PSScriptRoot\..\..\watch-build-errors.log",
    [switch]$SingleContainer
)

$ErrorActionPreference = "Stop"

# Initialize error log
echo "=== Legent Watch Build Error Log Started: $(Get-Date) ===" > $LogFile
echo "" >> $LogFile

# Colors
$Cyan = "Cyan"
$Green = "Green"
$Yellow = "Yellow"
$Gray = "Gray"

function Write-Status($Message, $Color = $Gray) {
    Write-Host $Message -ForegroundColor $Color
}

Write-Status "================================================" $Cyan
Write-Status "    LEGENT WATCH MODE (Auto-Build)" $Cyan
Write-Status "================================================" $Cyan
Write-Status ""
Write-Status "Watching for changes in:" $Yellow
foreach ($path in $WatchPaths) {
    Write-Status "  - $path" $Gray
}
Write-Status ""
Write-Status "Press Ctrl+C to stop" $Gray
Write-Status ""

# Get project root
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent (Split-Path -Parent $scriptPath)
Set-Location $projectRoot

# Calculate file hashes
$lastHashes = @{}
$lastBuildTime = Get-Date "1970-01-01"
$pendingBuild = $false

function Get-FileHashes($paths) {
    $hashes = @{}
    foreach ($pattern in $paths) {
        $files = Get-ChildItem -Path $pattern -Recurse -File -ErrorAction SilentlyContinue |
            Where-Object { $_.Extension -in @(".java", ".ts", ".tsx", ".js", ".jsx", ".html", ".css", ".xml", ".properties") }
        
        foreach ($file in $files) {
            try {
                $hash = (Get-FileHash -Path $file.FullName -Algorithm MD5 -ErrorAction SilentlyContinue).Hash
                if ($hash) {
                    $hashes[$file.FullName] = $hash
                }
            } catch {
                # File may be locked, skip
            }
        }
    }
    return $hashes
}

function Has-Changes($oldHashes, $newHashes) {
    # Check for new or modified files
    foreach ($key in $newHashes.Keys) {
        if (-not $oldHashes.ContainsKey($key)) {
            return $true, "New file: $key"
        }
        if ($oldHashes[$key] -ne $newHashes[$key]) {
            return $true, "Modified: $key"
        }
    }
    
    # Check for deleted files
    foreach ($key in $oldHashes.Keys) {
        if (-not $newHashes.ContainsKey($key)) {
            return $true, "Deleted: $key"
        }
    }
    
    return $false, ""
}

function Invoke-FastBuild($changedFile) {
    Write-Status ""
    Write-Status "[BUILD] Starting fast rebuild..." $Yellow
    Write-Status "================================================" $Cyan
    Write-Status "[TRIGGER] Changed: $changedFile" $Gray
    
    $buildStart = Get-Date
    $buildArgs = @{}
    $targetService = $Service
    
    # Auto-detect service from changed file if not specified
    if (-not $targetService -and $changedFile) {
        if ($changedFile -match "services/([^/]+)/") {
            $detectedService = $Matches[1] -replace "-service$", ""
            $targetService = $detectedService
            Write-Status "[AUTO] Detected service: $targetService" $Gray
        } elseif ($changedFile -match "frontend/") {
            $targetService = "frontend"
            Write-Status "[AUTO] Detected: frontend" $Gray
        } elseif ($changedFile -match "shared/") {
            Write-Status "[AUTO] Shared module changed - building all services" $Yellow
        }
    }
    
    if ($targetService) { 
        $buildArgs['Service'] = $targetService 
        Write-Status "[INFO] Building service: $targetService" $Gray
    } else {
        Write-Status "[INFO] Building all services" $Gray
    }
    
    # Build
    $buildOutput = & "$PSScriptRoot\docker-fast-build.ps1" @buildArgs 2>&1
    $buildExit = $LASTEXITCODE
    
    # Log output
    echo "=== Build Started: $buildStart ===" >> $LogFile
    echo "Changed File: $changedFile" >> $LogFile
    echo "Service: $targetService" >> $LogFile
    echo "Output:" >> $LogFile
    $buildOutput | Out-String >> $LogFile
    echo "Exit Code: $buildExit" >> $LogFile
    echo "" >> $LogFile
    
    if ($buildExit -eq 0) {
        Write-Status ""
        Write-Status "[DEPLOY] Restarting containers..." $Yellow
        
        # Single container mode - only restart the changed service
        if ($SingleContainer -and $targetService) {
            $dockerService = if ($targetService -eq "frontend") { "frontend" } else { "$targetService-service" }
            Write-Status "[INFO] Single container mode - restarting: $dockerService" $Gray
            docker compose stop $dockerService 2>&1 | Out-Null
            docker compose rm -f $dockerService 2>&1 | Out-Null
            docker compose up -d $dockerService 2>&1 | Out-Null
        } else {
            Write-Status "[INFO] Full redeploy mode" $Gray
            docker compose up -d 2>&1 | Out-Null
        }
        
        $deployOutput = docker compose ps 2>&1
        echo "Deploy Status:" >> $LogFile
        $deployOutput | Out-String >> $LogFile
        echo "" >> $LogFile
        
        Write-Status "[OK] Deploy complete!" $Green
    } else {
        Write-Status "[FAIL] Build failed! Check $LogFile for details" $Red
        echo "[ERROR] Build failed at $(Get-Date)" >> $LogFile
        echo "" >> $LogFile
    }
    
    $buildEnd = Get-Date
    $duration = $buildEnd - $buildStart
    Write-Status "[TIME] Build took: $($duration.ToString('mm\:ss'))" $Gray
    
    Write-Status "================================================" $Cyan
    Write-Status "[WATCH] Resuming watch mode..." $Cyan
    Write-Status ""
}

# Initial scan
Write-Status "[INIT] Scanning initial state..." $Gray
$lastHashes = Get-FileHashes $WatchPaths
Write-Status "[OK] Monitoring $($lastHashes.Count) files" $Green
Write-Status ""

# Main loop
try {
    while ($true) {
        Start-Sleep -Seconds 2
        
        $currentHashes = Get-FileHashes $WatchPaths
        $hasChange, $changeMsg = Has-Changes $lastHashes $currentHashes
        
        if ($hasChange) {
            Write-Status "[CHANGE] $changeMsg" $Yellow
            
            # Debounce - wait for more changes
            $pendingBuild = $true
            $changeTime = Get-Date
            
            while (((Get-Date) - $changeTime).TotalSeconds -lt $DebounceSeconds) {
                Start-Sleep -Milliseconds 500
                $checkHashes = Get-FileHashes $WatchPaths
                $stillChanging, $_ = Has-Changes $currentHashes $checkHashes
                
                if ($stillChanging) {
                    $changeTime = Get-Date
                    $currentHashes = $checkHashes
                    Write-Status "[CHANGE] Still changing..." $Gray
                }
            }
            
            # Update last hashes and trigger build
            $lastHashes = $currentHashes
            Invoke-FastBuild -changedFile $changeMsg
            $lastBuildTime = Get-Date
            $pendingBuild = $false
        }
        
        # Show heartbeat every 30 seconds
        $elapsed = (Get-Date) - $lastBuildTime
        if ($elapsed.TotalSeconds -gt 30 -and $elapsed.TotalSeconds % 30 -lt 2) {
            Write-Status "[WATCH] $($elapsed.ToString('mm\:ss')) since last build | $($currentHashes.Count) files monitored" $Gray
        }
    }
} catch {
    Write-Status ""
    Write-Status "[STOP] Watch mode terminated" $Yellow
    Write-Status "================================================" $Cyan
}

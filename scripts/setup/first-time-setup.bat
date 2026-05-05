@echo off
REM First-Time Setup Script for Legent Email Studio
REM Run this once after cloning the repository

echo ==========================================
echo  Legent: First-Time Setup
echo ==========================================
echo.
echo This script will:
echo   1. Cache infrastructure images (~2.5GB)
echo   2. Build shared libraries base image
echo   3. Set up optimized build environment
echo.
echo Estimated time: 15-20 minutes (one-time only!)
echo.

set /p confirm="Proceed? (Y/n): "
if /I not "%confirm%"=="Y" if not "%confirm%"=="" exit /b 0

echo.
echo ==========================================
echo Step 1/3: Caching Infrastructure Images
echo ==========================================
echo.
call scripts\infrastructure\cache-images.bat
if %ERRORLEVEL% neq 0 (
    echo ❌ Infrastructure cache failed
    exit /b 1
)

echo.
echo ==========================================
echo Step 2/3: Building Shared Base Image
echo ==========================================
echo.
call scripts\cached-builds\setup-cached-builds.bat
if %ERRORLEVEL% neq 0 (
    echo ❌ Shared base build failed
    exit /b 1
)

echo.
echo ==========================================
echo Step 3/3: Finalizing Setup
echo ==========================================
echo.

REM Check Maven wrapper exists
if not exist mvnw.cmd (
    echo Downloading Maven wrapper...
    powershell -Command "Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/takari/maven-wrapper/master/mvnw.cmd' -OutFile 'mvnw.cmd'"
    if not exist .mvn (
        mkdir .mvn
        mkdir .mvn\wrapper
    )
    powershell -Command "Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/takari/maven-wrapper/master/.mvn/wrapper/maven-wrapper.properties' -OutFile '.mvn\wrapper\maven-wrapper.properties'"
    echo ✅ Maven wrapper downloaded
)

REM Create .env file with optimizations if it doesn't exist
if not exist .env (
    echo Creating .env with build optimizations...
    (
        echo # BuildKit optimizations
        echo DOCKER_BUILDKIT=1
        echo COMPOSE_DOCKER_CLI_BUILD=1
        echo BUILDKIT_INLINE_CACHE=1
    ) > .env
    echo ✅ Created .env file
)

echo.
echo ==========================================
echo ✅ Setup Complete!
echo ==========================================
echo.
echo 🚀 Quick Start Commands:
echo.
echo    # Start infrastructure (now instant!)
echo    docker compose up -d postgres redis kafka opensearch
echo.
echo    # Build all services (recommended - fastest)
echo    .\scripts\fast-build\fast-build.ps1
echo.
echo    # Or single service:
echo    .\scripts\fast-build\fast-build.ps1 -Service campaign
echo.
echo    # Alternative: Docker-only cached builds
echo    .\scripts\cached-builds\build-services-cached.ps1 -All
echo.
echo Documentation:
echo    .\docs\build\BUILD_OPTIMIZATIONS.md    - Build speed improvements
echo    .\docs\build\CACHED_BUILDS.md          - Layered caching explained
echo    .\docs\build\INFRASTRUCTURE_CACHE.md   - Infrastructure image caching
echo.
echo Pro Tips:
echo    • All images are now cached locally
echo    • 'docker compose up' starts in ~30s instead of 5-10min
echo    • Service builds only compile your code (shared libs cached)
echo    • Run this again anytime with: first-time-setup.bat
echo.
pause

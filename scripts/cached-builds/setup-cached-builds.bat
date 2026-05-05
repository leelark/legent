@echo off
REM Setup cached builds - Run this once to prepare the optimized build environment

echo ==========================================
echo  Legent Cached Build Setup
echo ==========================================
echo.

REM Check Docker is running
docker info >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ❌ Docker is not running. Please start Docker Desktop first.
    exit /b 1
)

echo 🔄 Step 1: Building shared libraries base image...
echo    This may take 5-10 minutes the first time...
docker build -f shared/Dockerfile -t legent-shared-base:latest .

if %ERRORLEVEL% neq 0 (
    echo ❌ Base image build failed!
    exit /b 1
)

echo ✅ Base image built successfully!
echo.

REM Generate cached Dockerfiles for all services if they don't exist
echo 🔄 Step 2: Checking for cached Dockerfiles...

for %%s in (foundation,identity,content,audience,campaign,delivery,tracking,automation,deliverability,platform) do (
    if not exist "services\%%s-service\Dockerfile.cached" (
        echo    Creating cached Dockerfile for %%s-service...
        copy "scripts\cached-builds\Dockerfile.cached.template" "services\%%s-service\Dockerfile.cached" >nul
        powershell -Command "(Get-Content 'services\%%s-service\Dockerfile.cached') -replace 'SERVICE_NAME', '%%s-service' -replace 'SERVICE_PORT', '8081' | Set-Content 'services\%%s-service\Dockerfile.cached'"
    ) else (
        echo    ✓ %%s-service already has cached Dockerfile
    )
)

echo.
echo ==========================================
echo ✅ Setup Complete!
echo ==========================================
echo.
echo 🚀 Quick Start:
echo    .\scripts\docker\docker-build.bat              - Standard build
echo    .\scripts\cached-builds\build-services-cached.ps1 -All  - Cached build (FAST!)
echo    make build-cached             - Using Make
echo.
echo 📚 Next time:
echo    • Shared libraries are pre-built in legent-shared-base image
echo    • Service builds only compile service code
echo    • Rebuild base only when shared libs change:
echo      .\scripts\cached-builds\build-shared-base.ps1 -Force
echo.

@echo off
REM Docker Fast Build - Windows Batch Wrapper
REM Usage: docker-build.bat [clean|frontend|foundation|campaign|...]

setlocal EnableDelayedExpansion

REM Enable BuildKit
set DOCKER_BUILDKIT=1
set COMPOSE_DOCKER_CLI_BUILD=1
set BUILDKIT_INLINE_CACHE=1

echo 🐳 Docker Fast Build with BuildKit
echo ====================================

if "%~1"=="" goto build_all
if "%~1"=="clean" goto build_clean
if "%~1"=="all" goto build_all

REM Build specific service
echo 🔨 Building %1-service...
docker compose build %1-service
if %ERRORLEVEL% neq 0 (
    echo ❌ Build failed for %1-service
    exit /b 1
)
echo ✅ %1-service built successfully
goto done

:build_clean
echo ⚠️ Building all services WITHOUT cache...
docker compose build --no-cache
if %ERRORLEVEL% neq 0 (
    echo ❌ Build failed
    exit /b 1
)
goto done

:build_all
echo 🔨 Building all services with layer caching...
docker compose build --parallel
if %ERRORLEVEL% neq 0 (
    echo ❌ Build failed
    exit /b 1
)
goto done

:done
echo ✅ Build completed!
echo 💡 Use 'docker-build.bat clean' for fresh builds
echo 💡 Use 'docker-build.bat ^<service-name^>' for single service

@echo off
SETLOCAL ENABLEDELAYEDEXPANSION

:START
cls
echo =====================================
echo   LEGENT CLEAN BUILD + RUN SCRIPT
echo =====================================

cd /d "C:\Users\leelark.saxena\Desktop\Legent - v1.0.2"

:: Speed optimizations
set DOCKER_BUILDKIT=1
set COMPOSE_DOCKER_CLI_BUILD=1

echo.
echo [1/5] Maven clean + build (parallel, skip tests)...
call mvn -T 1C clean install -U -DskipTests
IF %ERRORLEVEL% NEQ 0 (
    echo ❌ Maven build failed
    goto ERROR
)

echo.
echo [2/5] Stop containers + clean project volumes...
docker compose down --volumes --remove-orphans --timeout 5

IF %ERRORLEVEL% NEQ 0 (
    echo ⚠️ Graceful shutdown failed, forcing stop...
    docker compose kill
    docker compose down --volumes --remove-orphans
)

echo ✅ Cleanup complete.

echo.
echo [3/5] Rebuild Docker images (fresh)...
docker compose build --no-cache --parallel --progress=plain
IF %ERRORLEVEL% NEQ 0 (
    echo ❌ Docker build failed
    goto ERROR
)

echo.
echo [4/5] Start containers...
docker compose up -d --remove-orphans
IF %ERRORLEVEL% NEQ 0 (
    echo ❌ Docker up failed
    goto ERROR
)

echo.
echo [5/5] System status:
docker compose ps

echo.
echo =====================================
echo ✅ CLEAN BUILD & RUN COMPLETE
echo =====================================

echo.
echo 🔄 Watching logs (press Ctrl+C to stop logs only)...
docker compose logs -f

echo.
echo Script is still running. Press Ctrl+C again or close window to exit.
goto WAIT

:ERROR
echo.
echo ❌ ERROR OCCURRED. Script will NOT exit.
echo Fix the issue and press any key to retry...
pause >nul
goto START

:WAIT
timeout /t 999999 >nul
goto WAIT

ENDLOCAL
@echo off
SETLOCAL ENABLEDELAYEDEXPANSION

echo =====================================
echo   LEGENT CLEAN BUILD + RUN SCRIPT
echo =====================================

:: 📂 Move to project directory
cd /d "C:\Users\leelark.saxena\Desktop\Legent - v1.0.2"

echo.
echo [1/7] Running Maven clean...
call mvn clean
IF %ERRORLEVEL% NEQ 0 (
    echo ❌ Maven clean failed
    exit /b %ERRORLEVEL%
)

echo.
echo [2/7] Running Maven build (skip tests)...
call mvn clean install -U -DskipTests
IF %ERRORLEVEL% NEQ 0 (
    echo ❌ Maven build failed
    exit /b %ERRORLEVEL%
)

echo.
echo [3/7] Stopping Docker containers...
docker compose down -v

echo.
echo [4/7] Cleaning Docker system...
docker system prune -a -f

echo.
echo [5/7] Cleaning Docker volumes...
docker volume prune -f

echo.
echo [6/7] Building Docker images (no cache)...
docker compose build --no-cache
IF %ERRORLEVEL% NEQ 0 (
    echo ❌ Docker build failed
    exit /b %ERRORLEVEL%
)

echo.
echo [7/7] Starting containers...
docker compose up -d
IF %ERRORLEVEL% NEQ 0 (
    echo ❌ Docker up failed
    exit /b %ERRORLEVEL%
)

echo.
echo =====================================
echo ✅ ALL DONE SUCCESSFULLY
echo =====================================

pause
ENDLOCAL
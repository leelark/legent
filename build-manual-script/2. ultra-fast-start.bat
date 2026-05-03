@echo off
echo ================================
echo   LEGENT ULTRA FAST START
echo ================================

cd /d "C:\Users\leelark.saxena\Desktop\Legent - v1.0.2"

echo.
echo Starting containers (no rebuild)...
docker compose up -d

IF %ERRORLEVEL% NEQ 0 (
    echo Failed to start
    exit /b %ERRORLEVEL%
)

echo.
echo STARTED (FASTEST MODE)
echo ================================

pause

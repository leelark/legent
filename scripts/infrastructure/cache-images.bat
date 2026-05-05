@echo off
REM Cache Infrastructure Images - Pre-download for faster starts
REM Run this once after cloning the repo

echo ==========================================
echo  Infrastructure Image Cache
echo ==========================================
echo.
echo This will download all infrastructure images:
echo   - PostgreSQL, Redis, Kafka, OpenSearch, etc.
echo.
echo Total size: ~2.5 GB (one-time download)
echo.

set /p confirm="Proceed? (Y/n): "
if /I not "%confirm%"=="Y" if not "%confirm%"=="" exit /b 0

echo.
echo Pulling images (this may take 5-10 minutes)...
echo.

REM Database
echo [1/10] PostgreSQL...
docker pull postgres:15-alpine >nul 2>&1 && echo   OK || echo   FAILED

echo [2/10] Redis...
docker pull redis:7-alpine >nul 2>&1 && echo   OK || echo   FAILED

echo [3/10] ClickHouse...
docker pull clickhouse/clickhouse-server:23.8-alpine >nul 2>&1 && echo   OK || echo   FAILED

REM Message Queue
echo [4/10] Zookeeper...
docker pull confluentinc/cp-zookeeper:7.5.0 >nul 2>&1 && echo   OK || echo   FAILED

echo [5/10] Kafka...
docker pull confluentinc/cp-kafka:7.5.0 >nul 2>&1 && echo   OK || echo   FAILED

echo [6/10] Kafka UI...
docker pull kafbat/kafka-ui:latest >nul 2>&1 && echo   OK || echo   FAILED

REM Search & Storage
echo [7/10] OpenSearch (large, ~1GB)...
docker pull opensearchproject/opensearch:2.11.0 >nul 2>&1 && echo   OK || echo   FAILED

echo [8/10] MinIO...
docker pull minio/minio >nul 2>&1 && echo   OK || echo   FAILED

REM Utilities
echo [9/10] MailHog...
docker pull mailhog/mailhog >nul 2>&1 && echo   OK || echo   FAILED

echo [10/10] Nginx...
docker pull nginx:1.27-alpine >nul 2>&1 && echo   OK || echo   FAILED

echo.
echo ==========================================
echo ✅ All infrastructure images cached!
echo ==========================================
echo.
echo 🚀 Next time you run:
echo    docker compose up
echo.
echo    It will start instantly without downloading!
echo.
echo 💡 Tips:
echo    - Images are cached until you run 'docker image prune'
echo    - Run this again if you want to update to latest versions
echo    - Check cache: docker images
echo.

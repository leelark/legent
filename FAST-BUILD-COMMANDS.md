# Legent Fast Build Commands Reference

## Quick Start - One Command

### Full Build + Deploy + Health Check (All Services)
```powershell
# One command does everything: Maven + Docker + Deploy + Health Check
powershell -ExecutionPolicy Bypass -File scripts\fast-build\all-in-one.ps1
```

### Watch Mode - Auto-rebuild on Code Changes
```powershell
# Automatically detects file changes, builds and deploys only changed service
# Errors logged to: watch-build-errors.log

# Single container mode (only restart changed service - FASTEST)
powershell -ExecutionPolicy Bypass -File scripts\fast-build\watch-build.ps1 -SingleContainer

# Watch specific service only
powershell -ExecutionPolicy Bypass -File scripts\fast-build\watch-build.ps1 -Service content -SingleContainer

# Custom log file location
powershell -ExecutionPolicy Bypass -File scripts\fast-build\watch-build.ps1 -SingleContainer -LogFile C:\temp\my-errors.log
```

---

## Individual Scripts

### 1. Fast Build (Maven + Docker)
```powershell
# Build all services
powershell -ExecutionPolicy Bypass -File scripts\fast-build\fast-build.ps1

# Build single service (fastest for development)
powershell -ExecutionPolicy Bypass -File scripts\fast-build\fast-build.ps1 -Service content

# Clean build (force rebuild everything)
powershell -ExecutionPolicy Bypass -File scripts\fast-build\fast-build.ps1 -Clean

# Maven only (skip Docker)
powershell -ExecutionPolicy Bypass -File scripts\fast-build\fast-build.ps1 -SkipDocker
```

### 2. Docker Fast Build
```powershell
# Build Maven + Docker images for all services
powershell -ExecutionPolicy Bypass -File scripts\fast-build\docker-fast-build.ps1

# Build single service
powershell -ExecutionPolicy Bypass -File scripts\fast-build\docker-fast-build.ps1 -Service content

# Clean Docker build (no cache)
powershell -ExecutionPolicy Bypass -File scripts\fast-build\docker-fast-build.ps1 -Clean
```

### 3. All-in-One (Build + Deploy + Health)
```powershell
# Full pipeline: Build + Deploy + Health Check
powershell -ExecutionPolicy Bypass -File scripts\fast-build\all-in-one.ps1

# Single service mode
powershell -ExecutionPolicy Bypass -File scripts\fast-build\all-in-one.ps1 -Service content

# Skip build, just deploy and health check
powershell -ExecutionPolicy Bypass -File scripts\fast-build\all-in-one.ps1 -SkipBuild

# Clean build + deploy
powershell -ExecutionPolicy Bypass -File scripts\fast-build\all-in-one.ps1 -Clean
```

---

## Docker Compose Commands

### Start Services
```powershell
# Start all services
docker compose up -d

# Start specific service only
docker compose up -d content-service

# Start infrastructure only (postgres, redis, kafka, etc.)
docker compose up -d postgres redis zookeeper kafka minio

# Start without building (use existing images)
docker compose up -d --no-build
```

### Check Status
```powershell
# Show all containers with health status
docker compose ps

# Watch continuous status
watch -n 5 docker compose ps

# Show only unhealthy containers
docker compose ps | Select-String "unhealthy"
```

### View Logs
```powershell
# All services logs
docker compose logs -f

# Specific service logs
docker compose logs -f content-service

# Last 100 lines
docker compose logs --tail 100 content-service

# Error logs only
docker compose logs 2>&1 | findstr "ERROR"
```

### Stop/Restart
```powershell
# Stop all services
docker compose down

# Stop without removing volumes
docker compose stop

# Restart single service
docker compose restart content-service

# Stop and remove single container
docker compose stop content-service
docker compose rm -f content-service
docker compose up -d content-service
```

---

## Development Workflow

### 1. Initial Setup
```powershell
# Full clean build and start everything
powershell -ExecutionPolicy Bypass -File scripts\fast-build\all-in-one.ps1 -Clean
```

### 2. Daily Development (Watch Mode)
```powershell
# Start watch mode - auto-rebuild on save
powershell -ExecutionPolicy Bypass -File scripts\fast-build\watch-build.ps1 -SingleContainer

# Edit code in IDE -> Auto detects changes -> Builds only changed service -> Restarts only that container
```

### 3. Manual Quick Iteration
```powershell
# Edit code, then quick rebuild single service
powershell -ExecutionPolicy Bypass -File scripts\fast-build\docker-fast-build.ps1 -Service content
docker compose restart content-service
```

### 4. After Pulling Code Changes
```powershell
# Full rebuild after git pull
powershell -ExecutionPolicy Bypass -File scripts\fast-build\all-in-one.ps1 -Clean
```

---

## Service Names Reference

| Service | Maven Module | Docker Compose | Port |
|---------|--------------|----------------|------|
| foundation | foundation-service | foundation-service | 8081 |
| identity | identity-service | identity-service | 8089 |
| content | content-service | content-service | 8090 |
| audience | audience-service | audience-service | 8082 |
| campaign | campaign-service | campaign-service | 8083 |
| delivery | delivery-service | delivery-service | 8084 |
| tracking | tracking-service | tracking-service | 8085 |
| automation | automation-service | automation-service | 8086 |
| deliverability | deliverability-service | deliverability-service | 8087 |
| platform | platform-service | platform-service | 8088 |
| frontend | (npm/node) | frontend | 3000 |

---

## Troubleshooting

### Build Fails
```powershell
# Check error log
Get-Content watch-build-errors.log -Tail 50

# Manual Maven build to see full errors
mvn clean install -DskipTests

# Docker build issues
docker compose build --no-cache content-service
```

### Service Won't Start
```powershell
# Check logs
docker compose logs --tail 50 content-service

# Health check failing
docker inspect --format='{{.State.Health.Status}}' legent-content

# Port conflict - check what's using port
netstat -ano | findstr 8080
```

### Clear Everything and Restart
```powershell
# Nuclear option - clear all and rebuild
docker compose down -v  # Remove volumes too
docker system prune -f  # Clean unused images
powershell -ExecutionPolicy Bypass -File scripts\fast-build\all-in-one.ps1 -Clean
```

---

## Performance Tips

1. **Use `-SingleContainer`** in watch mode for fastest rebuild (only restarts changed service)
2. **Specify `-Service`** for single service builds (much faster than building all)
3. **Incremental builds** are default - only changed modules rebuild
4. **Docker layer cache** - images cache layers automatically
5. **Shared base image** - all services use `legent-shared-base` for dependencies

---

## File Locations

| Script | Path |
|--------|------|
| Fast Build | `scripts\fast-build\fast-build.ps1` |
| Docker Fast Build | `scripts\fast-build\docker-fast-build.ps1` |
| All-in-One | `scripts\fast-build\all-in-one.ps1` |
| Watch Mode | `scripts\fast-build\watch-build.ps1` |
| Error Log | `watch-build-errors.log` (in project root) |

---

## Access URLs (When Running)

- API Gateway: http://localhost:8080
- Frontend App: http://localhost:3000
- MailHog (Email Testing): http://localhost:8025
- Kafka UI: http://localhost:8091
- MinIO Console: http://localhost:9001

# ──────────────────────────────────────────────────────
# Legent: Email Studio — Makefile
# ──────────────────────────────────────────────────────

.PHONY: help infra infra-down backend-build fast-build fast-build-ps fast-build-maven backend-run frontend-install frontend-dev clean test docker-build docker-build-fast shared-base shared-base-ps build-cached cache-infra setup

# ── Default ──
help:
	@echo ""
	@echo "  Legent: Email Studio — Development Commands"
	@echo "  ──────────────────────────────────────────────"
	@echo ""
	@echo "  Infrastructure:"
	@echo "    make infra           Start all infrastructure (Postgres, Kafka, Redis, etc.)"
	@echo "    make infra-down      Stop all infrastructure"
	@echo ""
	@echo "  Backend:"
	@echo "    make backend-build     Build all Java services (parallel)"
	@echo "    make fast-build        Fast Maven build (parallel)"
	@echo "    make fast-build-ps     Fast build using PowerShell script"
	@echo "    make backend-run       Run Foundation Service locally"
	@echo "    make test              Run all backend tests"
	@echo ""
	@echo "  Frontend:"
	@echo "    make frontend-install  Install frontend dependencies"
	@echo "    make frontend-dev      Start Next.js dev server"
	@echo ""
	@echo "  Docker:"
	@echo "    make docker-build      Build all Docker images (parallel)"
	@echo "    make docker-build-fast Build with BuildKit optimizations"
	@echo "    make shared-base       Build shared libraries base image"
	@echo "    make shared-base-ps    Build base using PowerShell script"
	@echo "    make build-cached      Build services using cached base"
	@echo "    make cache-infra       Pre-download infrastructure images"
	@echo ""
	@echo "  Setup:"
	@echo "    make setup             First-time setup (Windows)"
	@echo ""
	@echo "  General:"
	@echo "    make clean             Clean all build artifacts"
	@echo ""

# ── Infrastructure ──
infra:
	docker compose up -d
	@echo "✅ Infrastructure started. Kafka UI: http://localhost:8090"

infra-down:
	docker compose down

# ── Backend ──
backend-build:
	./mvnw clean install -DskipTests -T 1C

fast-build:
	./mvnw install -DskipTests -T 1C -o

backend-run:
	./mvnw -pl services/foundation-service spring-boot:run

test:
	./mvnw clean test

# ── Frontend ──
frontend-install:
	cd frontend && npm install

frontend-dev:
	cd frontend && npm run dev

# ── Docker ──
docker-build:
	DOCKER_BUILDKIT=1 COMPOSE_DOCKER_CLI_BUILD=1 docker compose build --parallel

docker-build-fast:
	DOCKER_BUILDKIT=1 COMPOSE_DOCKER_CLI_BUILD=1 BUILDKIT_INLINE_CACHE=1 \
		docker compose -f docker-compose.yml -f scripts/docker/docker-compose.build.yml build --parallel

# Build shared base image (run once, or when shared libs change)
shared-base:
	docker build -f shared/Dockerfile -t legent-shared-base:latest .
	@echo "✅ Shared base image built. Use 'make build-cached' for services."

# Build services using cached base (much faster)
build-cached:
	docker compose -f docker-compose.yml -f scripts/cached-builds/docker-compose.cached.yml build --parallel

# Pre-download infrastructure images for faster starts
cache-infra:
	@echo "Downloading infrastructure images (~2.5GB one-time)..."
	@powershell -ExecutionPolicy Bypass -File scripts/infrastructure/pull-infrastructure.ps1 -OnlyMissing

# Fast build using PowerShell script (recommended for daily use)
fast-build-ps:
	@powershell -ExecutionPolicy Bypass -File scripts/fast-build/fast-build.ps1

# Fast build with Maven only (no Docker)
fast-build-maven:
	./mvnw install -DskipTests -T 1C -o

# Setup for new developers
setup:
	@scripts\setup\first-time-setup.bat

# Build shared base image using script
shared-base-ps:
	@powershell -ExecutionPolicy Bypass -File scripts/cached-builds/build-shared-base.ps1

# ── General ──
clean:
	./mvnw clean
	cd frontend && rm -rf .next node_modules

# ──────────────────────────────────────────────────────
# Legent: Email Studio — Makefile
# ──────────────────────────────────────────────────────

.PHONY: help infra infra-down backend-build backend-run frontend-install frontend-dev clean test

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
	@echo "    make backend-build   Build all Java services"
	@echo "    make backend-run     Run Foundation Service locally"
	@echo "    make test            Run all backend tests"
	@echo ""
	@echo "  Frontend:"
	@echo "    make frontend-install  Install frontend dependencies"
	@echo "    make frontend-dev      Start Next.js dev server"
	@echo ""
	@echo "  General:"
	@echo "    make clean           Clean all build artifacts"
	@echo ""

# ── Infrastructure ──
infra:
	docker compose up -d
	@echo "✅ Infrastructure started. Kafka UI: http://localhost:8090"

infra-down:
	docker compose down

# ── Backend ──
backend-build:
	./mvnw clean install -DskipTests

backend-run:
	./mvnw -pl services/foundation-service spring-boot:run

test:
	./mvnw clean test

# ── Frontend ──
frontend-install:
	cd frontend && npm install

frontend-dev:
	cd frontend && npm run dev

# ── General ──
clean:
	./mvnw clean
	cd frontend && rm -rf .next node_modules

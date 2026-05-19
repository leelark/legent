.PHONY: help infra infra-down backend-build backend-run frontend-install frontend-dev clean test docker-build validate release-local

help:
	@echo "Legent development commands"
	@echo "  make infra             Start Docker Compose"
	@echo "  make infra-down        Stop Docker Compose"
	@echo "  make backend-build     Maven install without tests"
	@echo "  make backend-run       Run foundation service locally"
	@echo "  make test              Run backend tests"
	@echo "  make frontend-install  Install frontend dependencies"
	@echo "  make frontend-dev      Start frontend dev server"
	@echo "  make docker-build      Build Docker Compose images"
	@echo "  make validate          Run lightweight ops validators"
	@echo "  make release-local     Run local-only release gate"

infra:
	docker compose up -d

infra-down:
	docker compose down

backend-build:
	./mvnw install -DskipTests -T 1C

backend-run:
	./mvnw -pl services/foundation-service spring-boot:run

test:
	./mvnw test

frontend-install:
	cd frontend && npm ci

frontend-dev:
	cd frontend && npm run dev

docker-build:
	docker compose build

validate:
	powershell -ExecutionPolicy Bypass -File scripts/ops/validate-env.ps1 -EnvFile .env.example -AllowPlaceholders
	powershell -ExecutionPolicy Bypass -File scripts/ops/validate-route-map.ps1
	powershell -ExecutionPolicy Bypass -File scripts/ops/validate-repo-artifact-hygiene.ps1
	powershell -ExecutionPolicy Bypass -File scripts/ops/validate-production-overlay.ps1

release-local:
	powershell -ExecutionPolicy Bypass -File scripts/ops/release-gate.ps1 -LocalOnly -SkipBackend -SkipFrontend -SkipCompose -SkipKustomize

clean:
	./mvnw clean
	cd frontend && rm -rf .next

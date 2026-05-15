# Build Scripts Organization

Build, deployment, validation, and optimization scripts are grouped by purpose.

## Folder Structure

```text
scripts/
+-- cached-builds/        # Layered caching for shared libraries
+-- docker/               # Docker-specific build scripts
+-- fast-build/           # Fast local development builds
+-- infrastructure/       # Infrastructure image management
+-- load/                 # High-volume and live-load validation harnesses
+-- ops/                  # Environment, route, release, production, and health validators
+-- setup/                # One-time setup scripts
+-- README.md             # This file
```

## Quick Reference

### Daily Development

```powershell
# Full build - Maven + Docker
.\scripts\fast-build\fast-build.ps1

# Single service build
.\scripts\fast-build\fast-build.ps1 -Service campaign

# When shared libraries change
.\scripts\fast-build\fast-build.ps1 -RebuildBase

# Docker-only build
.\scripts\fast-build\docker-fast-build.ps1 -Service campaign
```

### Operations and Release Gates

```powershell
# Validate required environment variables without printing secrets
.\scripts\ops\validate-env.ps1 -EnvFile .env

# Route, Nginx, ingress, and controller-root drift check
.\scripts\ops\validate-route-map.ps1

# Local Compose health validation
.\scripts\ops\validate-compose-health.ps1

# Full release-readiness gate
.\scripts\ops\release-gate.ps1
```

### Load Validation

```powershell
# Live high-volume validation requires a real token and approved dataset
.\scripts\load\phase3-high-volume-load.ps1 -BaseUrl https://gateway.example.com/api/v1 -TenantId tenant-enterprise-load -WorkspaceId workspace-enterprise-load -Token $env:LEGENT_LOAD_TOKEN -DataProfileName enterprise-2026q2-volume -RequireLive -FailOnErrors
```

### Infrastructure

```batch
# Cache images
.\scripts\infrastructure\cache-images.bat
```

```powershell
# PowerShell image cache with progress
.\scripts\infrastructure\pull-infrastructure.ps1
```

### Setup

```batch
# Complete first-time setup
.\scripts\setup\first-time-setup.bat
```

## Detailed Contents

### `fast-build/`

| Script | Purpose |
| --- | --- |
| `fast-build.ps1` | Incremental Maven + Docker build |
| `fast-build.ps1 -RebuildBase` | Rebuild when `shared/` libraries change |
| `docker-fast-build.ps1` | Docker-only builds with BuildKit |

Use for day-to-day code changes. Prefer `-RebuildBase` after shared module edits.

### `cached-builds/`

| Script/File | Purpose |
| --- | --- |
| `build-shared-base.ps1` | Build shared libraries base image |
| `build-services-cached.ps1` | Build services using cached base |
| `setup-cached-builds.bat` | One-time cached-build setup |
| `docker-compose.cached.yml` | Compose override for cached builds |
| `Dockerfile.cached.template` | Template for cached Dockerfiles |

Use when shared libraries change or for CI/CD pipeline optimization.

### `infrastructure/`

| Script/File | Purpose |
| --- | --- |
| `cache-images.bat` | Pre-download infrastructure images |
| `pull-infrastructure.ps1` | PowerShell image pull with progress |
| `docker-compose.override.yml` | Auto-applied pull policy |

Use for new-machine setup or image pre-caching.

### `docker/`

| Script/File | Purpose |
| --- | --- |
| `docker-build.bat` | Standard Docker builds |
| `docker-compose.build.yml` | Build cache configuration |
| `.env.docker` | Docker environment variables |

Use for Docker-specific build scenarios.

### `ops/`

| Script | Purpose |
| --- | --- |
| `validate-env.ps1` | Required environment and placeholder checks |
| `validate-route-map.ps1` | Gateway route-map, Nginx, ingress, and controller-root drift checks |
| `validate-production-overlay.ps1` | Production Kustomize render drift and fail-closed checks |
| `validate-compose-health.ps1` | Local Compose health validation |
| `release-gate.ps1` | End-to-end release-readiness gate |

Use before promotion, after route/ingress changes, or when validating local runtime health.

### `load/`

| Script | Purpose |
| --- | --- |
| `phase3-high-volume-load.ps1` | High-volume live-mode load harness with explicit token and dataset gates |

Use before production high-volume sending or after campaign, delivery, tracking, audience, or rate-control changes.

### `setup/`

| Script/File | Purpose |
| --- | --- |
| `first-time-setup.bat` | Complete one-time setup |
| `docker-daemon.json` | Docker daemon configuration |

Use for fresh clone or developer onboarding.

## Documentation

See `docs/build/` for detailed documentation:

- `QUICKSTART.md`
- `BUILD_OPTIMIZATIONS.md`
- `CACHED_BUILDS.md`
- `INFRASTRUCTURE_CACHE.md`

## Migration from Old Locations

| Old Command | New Command |
| --- | --- |
| `fast-build.ps1` | `scripts\fast-build\fast-build.ps1` |
| `cache-images.bat` | `scripts\infrastructure\cache-images.bat` |
| `first-time-setup.bat` | `scripts\setup\first-time-setup.bat` |

## Makefile

The root `Makefile` still calls scripts from their current locations.

```bash
make fast-build
make cache-infra
make build-cached
```
